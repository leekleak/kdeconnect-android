/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import androidx.core.net.toUri
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.plugins.PermissionPluginInfo.Companion.isPermissionGranted
import org.kde.kdeconnect.plugins.Plugin
import java.io.IOException

class FindMyPhonePlugin(
    private val context: Context,
    private val device: Device,
    private val telephonySettingsDataStore: TelephonySettingsDataStore,
    private val permissionRequestHelper: PermissionRequestHelper
) : Plugin() {
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationId = System.currentTimeMillis().toInt()
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private var previousVolume = -1
    private val flashlightManager: FlashlightManager = FlashlightManager(context)

    override val pluginInfo: FindMyPhonePluginInfo = FindMyPhonePluginInfo

    override fun onCreate(): Boolean {
        val ringtoneString = telephonySettingsDataStore.getRingtoneUriBlockingBlocking()
        val ringtone = if (ringtoneString.isEmpty()) {
            Settings.System.DEFAULT_RINGTONE_URI
        } else {
            ringtoneString.toUri()
        }

        try {
            mediaPlayer.setDataSource(context, ringtone)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            mediaPlayer.setAudioAttributes(audioAttributes)
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
        } catch (e: Exception) {
            LoggerTagged.e(e) { "Exception" }
            return false
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (mediaPlayer.isPlaying) {
                stopPlaying()
            }
        } catch (_: IllegalStateException) { }
        mediaPlayer.release()
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if (!pluginInfo.checkRequiredPermissions(context)) { // Todo: Find my permissions should be granted on app setup
            pluginInfo.showPermissionExplanation(context, permissionRequestHelper)
        } else {
            val intent = Intent(context, FindMyPhoneActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.deviceId)
            context.startActivity(intent)
        }
        return true
    }

    fun startPlaying() {
        if (!mediaPlayer.isPlaying) {
            // Make sure we are heard even when the phone is silent, restore original volume later
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            mediaPlayer.start()
        }
    }

    fun startFlashing() {
        if (this.isFlashlightEnabledInSettings && isPermissionGranted(
                context,
                Manifest.permission.CAMERA
            )
        ) {
            flashlightManager.startFlashing()
        }
    }

    fun hideNotification() {
        notificationManager.cancel(notificationId)
    }

    fun stopPlaying() {
        if (previousVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
        }
        mediaPlayer.stop()
        try {
            mediaPlayer.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun stopFlashing() {
        flashlightManager.stopFlashing()
    }

    private val isFlashlightEnabledInSettings: Boolean
        get() = telephonySettingsDataStore.getFlashlightEnabledBlockingBlocking()

    companion object {
        const val PACKET_TYPE_FINDMYPHONE_REQUEST: String = "kdeconnect.findmyphone.request"
    }
}
