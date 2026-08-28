/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.telephony

import android.Manifest.permission.READ_CALL_LOG
import android.Manifest.permission.READ_CONTACTS
import android.Manifest.permission.READ_PHONE_STATE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.pref_plugin_telephony
import org.kde.kdeconnect.generated.resources.pref_plugin_telephony_desc
import org.kde.kdeconnect.helpers.ContactsHelper
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.telephony.TelephonyPlugin.Companion.PACKET_TYPE_TELEPHONY
import org.kde.kdeconnect.plugins.telephony.TelephonyPlugin.Companion.PACKET_TYPE_TELEPHONY_REQUEST_MUTE
import java.util.Timer
import java.util.TimerTask

class TelephonyPlugin(
    private val context: Context,
    private val device: Device,
    private val telephonySettings: TelephonySettingsDataStore
) : Plugin() {
    override val pluginInfo: PermissionPluginInfo = TelephonyPluginInfo
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var lastPacket: NetworkPacket? = null
    private var isMuted = false

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            //LoggerTagged.e { "TelephonyPlugin", "Telephony event: $action")
            if (TelephonyManager.ACTION_PHONE_STATE_CHANGED == intent.action) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                val intState = when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                    else -> TelephonyManager.CALL_STATE_IDLE
                }

                // We will get a second broadcast with the phone number https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED
                if (!intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) return
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

                if (intState != lastState) {
                    lastState = intState
                    if (number != null) {
                        coroutineScope.launch {
                            callBroadcastReceived(intState, number)
                        }
                    }
                }
            }
        }
    }

    private suspend fun callBroadcastReceived(state: Int, phoneNumber: String) {
        if (isNumberBlocked(phoneNumber)) return

        var np = NetworkPacket(PACKET_TYPE_TELEPHONY).update { // Todo: Make val
            val permissionCheck = ContextCompat.checkSelfPermission(context, READ_CONTACTS)

            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                val contactInfo = ContactsHelper.phoneNumberLookup(context, phoneNumber)

                contactInfo["name"]?.let { name -> put("contactName", name) }

                if (contactInfo.containsKey("photoID")) {
                    val photoUri = contactInfo["photoID"]
                    if (photoUri != null) {
                        try {
                            val base64photo = ContactsHelper.photoId64Encoded(context, photoUri)
                            if (base64photo.isNotEmpty()) {
                                put("phoneThumbnail", base64photo)
                            }
                        } catch (e: Exception) {
                            LoggerTagged.e(e) { "Failed to get contact photo" }
                        }
                    }
                }
            } else {
                put("contactName", phoneNumber)
            }

            put("phoneNumber", phoneNumber)
        }
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                unmuteRinger()
                np = np.update {
                    put("event", "ringing")
                }
                device.sendPacket(np)
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                np = np.update {
                    put("event", "talking")
                }
                device.sendPacket(np)
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                val lastPacket = lastPacket ?: return
                // Resend a cancel of the last event (can either be "ringing" or "talking")
                device.sendPacket(lastPacket.update {
                    put("isCancel", "true")
                })

                if (isMuted) {
                    val timer = Timer()
                    timer.schedule(object : TimerTask() {
                        override fun run() {
                            unmuteRinger()
                        }
                    }, 500)
                }

                // Emit a missed call notification if needed
                if ("ringing" == lastPacket.getString("event")) {
                    np = np.update {
                        put("event", "missedCall")
                        val phoneNumber = lastPacket.getString("phoneNumber")
                        if (phoneNumber != null) {
                            put("phoneNumber", phoneNumber)
                        }
                        val contactName = lastPacket.getString("contactName")
                        if (contactName != null) {
                            put("contactName", contactName)
                        }
                    }
                    device.sendPacket(np)
                }
            }
        }

        lastPacket = np
    }

    private fun unmuteRinger() {
        if (isMuted) {
            val am = ContextCompat.getSystemService(context, AudioManager::class.java) ?: return
            am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
            isMuted = false
        }
    }

    private fun muteRinger() {
        if (!isMuted) {
            val am = ContextCompat.getSystemService(context, AudioManager::class.java) ?: return
            am.setStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
            isMuted = true
        }
    }

    override fun onCreate(): Boolean {
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        filter.priority = 500
        context.registerReceiver(receiver, filter)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        context.unregisterReceiver(receiver)
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        when (np.type) {
            PACKET_TYPE_TELEPHONY_REQUEST_MUTE -> muteRinger()
        }
        return true
    }

    private fun isNumberBlocked(number: String?): Boolean {
        val blockedNumbers = telephonySettings.getBlockedNumbersBlockingBlocking()

        return blockedNumbers.any { s -> PhoneNumberUtils.compare(number, s) }
    }

    companion object {
        /**
         * Packet used for simple call events
         *
         * It contains the key "event" which maps to a string indicating the type of event:
         * - "ringing" - A phone call is incoming
         * - "missedCall" - An incoming call was not answered
         * - "sms" - An incoming SMS message
         * - Note: As of this writing (15 May 2018) the SMS interface is being improved and this type of event
         * is no longer the preferred way of handling SMS. Use the packets defined by the SMS plugin instead.
         *
         * Depending on the event, other fields may be defined
         */
        const val PACKET_TYPE_TELEPHONY: String = "kdeconnect.telephony"

        /**
         * Packet sent to indicate the user has requested the device mute its ringer
         *
         * The body should be empty
         */
        const val PACKET_TYPE_TELEPHONY_REQUEST_MUTE = "kdeconnect.telephony.request_mute"
    }
}

object TelephonyPluginInfo : PermissionPluginInfo(
    pluginKey = "TelephonyPlugin",
    instantiableClass = TelephonyPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_telephony,
    descriptionRes = Res.string.pref_plugin_telephony_desc,
    requiredPermissions = setOf(READ_PHONE_STATE, READ_CALL_LOG, READ_CONTACTS),
    supportedPacketTypes = setOf(PACKET_TYPE_TELEPHONY_REQUEST_MUTE),
    outgoingPacketTypes = setOf(PACKET_TYPE_TELEPHONY),
    lazy = false
)