/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.receivenotifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPlugin.Companion.PACKET_TYPE_NOTIFICATION
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPlugin.Companion.PACKET_TYPE_NOTIFICATION_REQUEST
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R

class ReceiveNotificationsPlugin(context: Context, device: Device) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = ReceiveNotificationsPluginInfo
    override fun onCreate(): Boolean {
        // request all existing notifications
        val np = NetworkPacket(PACKET_TYPE_NOTIFICATION_REQUEST)
        np["request"] = true
        device.sendPacket(np)
        return true
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if ("ticker" !in np || "appName" !in np || "id" !in np) {
            Log.e("NotificationsPlugin", "Received notification packet lacks properties")
            return true
        }

        if (np.getBoolean("silent", false)) {
            return true
        }

        val resultPendingIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        var largeIcon: Bitmap? = null
        val payload = np.payload
        if (payload != null && payload.payloadSize != 0L) {
            val width = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            val height = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height)
            val input = payload.inputStream
            largeIcon = BitmapFactory.decodeStream(input)
            payload.close()

            if (largeIcon != null) {
                // Log.i("NotificationsPlugin", "hasPayload: size=${largeIcon.width}/${largeIcon.height} opti=$width/$height")
                if (largeIcon.width > width || largeIcon.height > height) {
                    // older API levels don't scale notification icons automatically, therefore:
                    largeIcon = largeIcon.scale(width, height, false)
                }
            }
        }

        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return true

        val noti =
            NotificationCompat.Builder(context, NotificationHelper.Channels.RECEIVENOTIFICATION)
                .setContentTitle(np.getString("appName"))
                .setContentText(np.getString("ticker"))
                .setContentIntent(resultPendingIntent)
                .setTicker(np.getString("ticker"))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(largeIcon)
                .setAutoCancel(true)
                .setLocalOnly(true) // to avoid bouncing the notification back to other kdeconnect nodes
                .setDefaults(Notification.DEFAULT_ALL)
                .setStyle(NotificationCompat.BigTextStyle().bigText(np.getString("ticker")))
                .build()

        val id = np.getString("id")
        val intId = try { id.toInt() } catch (e: NumberFormatException) { 0 }
        notificationManager.notify("kdeconnectId:${id}", intId, noti)

        return true
    }

    companion object {
        const val PACKET_TYPE_NOTIFICATION = "kdeconnect.notification"
        const val PACKET_TYPE_NOTIFICATION_REQUEST = "kdeconnect.notification.request"
    }
}

object ReceiveNotificationsPluginInfo : PluginInfo(
    instantiableClass = ReceiveNotificationsPlugin::class.java,
    displayNameRes = R.string.pref_plugin_receive_notifications,
    descriptionRes = R.string.pref_plugin_receive_notifications_desc,
    requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf()
    },
    supportedPacketTypes = arrayOf(PACKET_TYPE_NOTIFICATION),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_NOTIFICATION_REQUEST),
) {
    override val permissionExplanation: Int = R.string.receive_notifications_permission_explanation
}
