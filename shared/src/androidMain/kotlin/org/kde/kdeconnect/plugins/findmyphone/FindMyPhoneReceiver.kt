/*
 * SPDX-FileCopyrightText: 2015 David Edmundson <kde@davidedmundson.co.uk>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.findmyphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.helpers.LoggerTagged
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FindMyPhoneReceiver : BroadcastReceiver(), KoinComponent {
    val deviceManager: DeviceManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FOUND_IT -> foundIt(intent)
            else -> LoggerTagged.d { "Unhandled Action received: ${intent.action}" }
        }
    }

    private fun foundIt(intent: Intent) {
        if (!intent.hasExtra(EXTRA_DEVICE_ID)) {
            LoggerTagged.e { "foundIt() - deviceId extra is not present, ignoring" }
            return
        }
        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val plugin = deviceManager.getDevicePlugin(deviceId, FindMyPhonePlugin::class.java)
                plugin?.let {
                    it.stopPlaying()
                    it.stopFlashing()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FOUND_IT: String = "org.kde.kdeconnect.plugins.findmyphone.foundIt"
        const val EXTRA_DEVICE_ID: String = "deviceId"
    }
}
