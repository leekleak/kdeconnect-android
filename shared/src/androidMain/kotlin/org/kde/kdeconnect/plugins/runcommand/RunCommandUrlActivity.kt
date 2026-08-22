/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.runcommand

import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.os.Vibrator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.runcommand_noruncommandplugin
import org.kde.kdeconnect.generated.resources.runcommand_nosuchdevice
import org.kde.kdeconnect.generated.resources.runcommand_notpaired
import org.kde.kdeconnect.generated.resources.runcommand_notreachable
import org.kde.kdeconnect.helpers.LoggerTagged
import org.koin.android.ext.android.inject

class RunCommandUrlActivity : AppCompatActivity() {
    private val deviceManager: DeviceManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action != null) {
            try {
                val uri = intent.data
                val deviceId = uri!!.pathSegments[0]

                val vibrator = getSystemService(Vibrator::class.java)
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(100)
                    }
                }

                val device = deviceManager.getDevice(deviceId)

                if (device == null) {
                    error(Res.string.runcommand_nosuchdevice)
                } else if (!device.isPaired) {
                    error(Res.string.runcommand_notpaired)
                } else if (!device.isReachable) {
                    error(Res.string.runcommand_notreachable)
                } else {
                    lifecycleScope.launch {
                        val plugin = device.getPlugin(RunCommandPlugin::class.java)
                        if (plugin == null) {
                            error(Res.string.runcommand_noruncommandplugin)
                        } else {
                            plugin.runCommand(uri.pathSegments[1])
                        }
                        this@RunCommandUrlActivity.finish()
                    }
                    return
                }
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Exception" }
            }
        }
    }

    private fun error(message: StringResource) {
        Toast.makeText(this, runBlocking { getString(message) }, Toast.LENGTH_LONG).show()
    }
}
