/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import org.kde.kdeconnect.helpers.LoggerTagged

class KdeConnectBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                LoggerTagged.i { "MyUpdateReceiver" }
                BackgroundService.start(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                LoggerTagged.i { "KdeConnectBroadcastReceiver" }
                try {
                    BackgroundService.start(context)
                } catch (e: IllegalStateException) { // To catch ForegroundServiceStartNotAllowedException
                    LoggerTagged.w(e) { "Couldn't start the foreground service." }
                }
            }

            WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION, WifiManager.WIFI_STATE_CHANGED_ACTION, ConnectivityManager.CONNECTIVITY_ACTION -> {
                LoggerTagged.i { "Connection state changed, trying to connect" }
                BackgroundService.forceRefreshConnections()
            }

            Intent.ACTION_SCREEN_ON -> try {
                BackgroundService.forceRefreshConnections()
            } catch (e: IllegalStateException) { // To catch ForegroundServiceStartNotAllowedException
                LoggerTagged.w(e) { "Couldn't start the foreground service." }
            }

            else -> LoggerTagged.i { "Ignoring broadcast event: ${intent.action}" }
        }
    }
}
