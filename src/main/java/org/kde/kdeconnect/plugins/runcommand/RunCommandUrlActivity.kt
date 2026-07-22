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
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect_tp.R
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
                    error(R.string.runcommand_nosuchdevice)
                } else if (!device.isPaired) {
                    error(R.string.runcommand_notpaired)
                } else if (!device.isReachable) {
                    error(R.string.runcommand_notreachable)
                } else {
                    val plugin = device.getPlugin(RunCommandPlugin::class.java)
                    if (plugin == null) {
                        error(R.string.runcommand_noruncommandplugin)
                    } else {
                        plugin.runCommand(uri.pathSegments[1])
                    }
                }
                this@RunCommandUrlActivity.finish()
            } catch (e: Exception) {
                Log.e("RuncommandPlugin", "Exception", e)
            }
        }
    }

    private fun error(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
