/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <david@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.helpers.LifecycleHelper.isInForeground
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo.Companion.isPermissionGranted
import org.kde.kdeconnect_tp.R
import java.io.IOException

class FindMyPhonePlugin(
    context: Context,
    device: Device,
    private val telephonySettingsDataStore: TelephonySettingsDataStore
) : Plugin(context, device) {
    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationId = System.currentTimeMillis().toInt()
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private var previousVolume = -1
    private val powerManager: PowerManager = context.getSystemService(PowerManager::class.java)
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
            Log.e("FindMyPhoneActivity", "Exception", e)
            return false
        }

        return true
    }

    override fun onDestroy() {
        if (mediaPlayer.isPlaying) {
            stopPlaying()
        }
        mediaPlayer.release()
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (!pluginInfo.checkRequiredPermissions(context)) { // Todo: Find my permissions should be granted on app setup
            pluginInfo.showPermissionExplanation(context, deviceId)
        } else {
            val intent = Intent(context, FindMyPhoneActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.deviceId)
            context.startActivity(intent)
        }
        return true
    }

    private fun showBroadcastNotification() {
        val intent = Intent(context, FindMyPhoneReceiver::class.java)
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        intent.action = FindMyPhoneReceiver.ACTION_FOUND_IT
        intent.putExtra(FindMyPhoneReceiver.EXTRA_DEVICE_ID, device.deviceId)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createNotification(pendingIntent)
    }

    private fun showActivityNotification() {
        val intent = Intent(context, FindMyPhoneActivity::class.java)
        intent.putExtra(FindMyPhoneActivity.EXTRA_DEVICE_ID, device.deviceId)

        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        createNotification(pi)
    }

    private fun createNotification(pendingIntent: PendingIntent?) {
        val notification =
            NotificationCompat.Builder(context, NotificationHelper.Channels.HIGHPRIORITY)
        notification
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentTitle(context.getString(R.string.findmyphone_found))
        notification.setGroup("BackgroundService")

        notificationManager.notify(notificationId, notification.build())
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

    val isPlaying: Boolean
        get() = mediaPlayer.isPlaying

    private val isFlashlightEnabledInSettings: Boolean
        get() = telephonySettingsDataStore.getFlashlightEnabledBlockingBlocking()

    companion object {
        const val PACKET_TYPE_FINDMYPHONE_REQUEST: String = "kdeconnect.findmyphone.request"
    }
}
