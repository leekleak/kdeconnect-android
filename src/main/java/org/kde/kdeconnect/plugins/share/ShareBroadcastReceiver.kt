/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.helpers.LoggerTagged
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ShareBroadcastReceiver : BroadcastReceiver(), KoinComponent {
    private val deviceManager: DeviceManager by inject()

    override fun onReceive(context: Context?, intent: Intent) {
        when (intent.action) {
            SharePlugin.ACTION_CANCEL_SHARE -> cancelShare(intent)
            else -> LoggerTagged.d { "Unhandled Action received: ${intent.action}" }
        }
    }

    private fun cancelShare(intent: Intent) {
        if (!intent.hasExtra(SharePlugin.CANCEL_SHARE_DATA_TRANSFER_JOB_ID_EXTRA) ||
            !intent.hasExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA)
        ) {
            LoggerTagged.e { "cancelShare() - not all expected extra's are present. Ignoring this cancel intent" }
            return
        }

        val jobId = intent.getIntExtra(SharePlugin.CANCEL_SHARE_DATA_TRANSFER_JOB_ID_EXTRA, -1)
        val deviceId = intent.getStringExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val plugin = deviceManager.getDevicePlugin(deviceId, SharePlugin::class.java)
                plugin?.cancelJob(jobId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
