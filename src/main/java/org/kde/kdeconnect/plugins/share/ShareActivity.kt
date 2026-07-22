/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.compose.screen.share.ShareScreen
import org.kde.kdeconnect_tp.R
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class ShareActivity : AppCompatActivity() {
    private val deviceManager: DeviceManager by inject()

    private var isRefreshing by mutableStateOf(value = false)
    private var uiDevices by mutableStateOf<List<DeviceUiModel>>(value = emptyList())
    private var intentHasUrl by mutableStateOf(value = false)

    private suspend fun refreshDevicesAction() {
        isRefreshing = true

        BackgroundService.forceRefreshConnections(context = this)
        delay(1500.milliseconds)
        isRefreshing = false
    }

    private fun updateDeviceList() {
        val intent = intent
        val action = intent.action
        if (Intent.ACTION_SEND != action && Intent.ACTION_SEND_MULTIPLE != action) {
            finish()
            return
        }
        val devices = deviceManager.devices.values.filter { it.isReachable }
        this.intentHasUrl = doesIntentContainUrl(intent)
        this.uiDevices = devices
            .filter { device -> device.isPaired && (intentHasUrl || device.isReachable) }
            .map { it.toUiModel() }
    }

    private fun deviceClicked(
        device: Device,
        intent: Intent
    ) {
        val plugin: SharePlugin? = deviceManager.getDevicePlugin(device.deviceId, SharePlugin::class.java)
        plugin?.share(intent)
        finish()
    }

    private fun doesIntentContainUrl(intent: Intent?): Boolean {
        intent?.extras?.let { extras ->
            val url = extras.getString(Intent.EXTRA_TEXT)
            return URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KdeTheme(this) {
                val scope = rememberCoroutineScope()
                ShareScreen(
                    devices = uiDevices,
                    intentHasUrl = intentHasUrl,
                    isRefreshing = isRefreshing,
                    onDeviceClick = { deviceId ->
                        val device = deviceManager.getDevice(id = deviceId) ?: return@ShareScreen
                        deviceClicked(device = device, intent = intent)
                    },
                    onRefresh = {
                        scope.launch {
                            refreshDevicesAction()
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val intent = intent
        var deviceId = intent.getStringExtra("deviceId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
        }

        if (deviceId != null) {
            val plugin: SharePlugin? = deviceManager.getDevicePlugin(deviceId, SharePlugin::class.java)
            plugin?.share(intent)
            finish()
        } else {
            Toast.makeText(this, R.string.could_not_find_device, Toast.LENGTH_LONG).show()
            deviceManager.addDeviceListChangedCallback(key = "ShareActivity") {
                runOnUiThread { updateDeviceList() }
            }
            BackgroundService.forceRefreshConnections(context = this) // force a network re-discover
            updateDeviceList()
        }
    }

    override fun onStop() {
        deviceManager.removeDeviceListChangedCallback(key = "ShareActivity")
        super.onStop()
    }
}