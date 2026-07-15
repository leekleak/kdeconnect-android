/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.ping

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.ping.PingPlugin.Companion.PACKET_TYPE_PING
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R

class PingPlugin(context: Context, device: Device) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = PingPluginInfo

    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton(
            name = context.getString(R.string.send_ping),
            iconRes = R.drawable.arrow_upward,
            category = ButtonCategory.CONTROL
        ) { _ ->
            if (isDeviceInitialized) {
                device.sendPacket(NetworkPacket(PACKET_TYPE_PING))
            }
        }
    )

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type != PACKET_TYPE_PING) {
            Log.e(LOG_TAG, "Ping plugin should not receive packets other than pings!")
            return false
        }

        val mutableUpdateFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val resultPendingIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), mutableUpdateFlags)

        val (id: Int, message: String) = if (np.has("message")) {
            val id = System.currentTimeMillis().toInt()
            Pair(id, np.getString("message"))
        } else {
            val id = 42 // A unique id to create only one notification
            Pair(id, "Ping!")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionResult = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                // If notifications are not allowed, show a toast instead of a notification
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                return true
            }
        }

        val notificationManager = context.getSystemService<NotificationManager>()!!

        val notification = NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
            .setContentTitle(device.name)
            .setContentText(message)
            .setContentIntent(resultPendingIntent)
            .setTicker(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()

        notificationManager.notify(id, notification)

        return true
    }

    companion object {
        const val PACKET_TYPE_PING = "kdeconnect.ping"
        private const val LOG_TAG = "PingPlugin"
    }
}

object PingPluginInfo : PluginInfo(
    instantiableClass = PingPlugin::class.java,
    displayNameRes = R.string.pref_plugin_ping,
    descriptionRes = R.string.pref_plugin_ping_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_PING),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_PING),
) {
}
