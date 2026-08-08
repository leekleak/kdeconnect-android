/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.compose.screen.share.DeviceSelectScreen
import org.kde.kdeconnect_tp.R
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class ShareActivity : AppCompatActivity() {
    private val deviceManager: DeviceManager by inject()
    private val backgroundServiceData: BackgroundServiceData by inject()
    private val trustedNetworkHelper: TrustedNetworkHelper by inject()

    private var isRefreshing by mutableStateOf(value = false)
    private var uiDevices: StateFlow<List<DeviceUiModel>> = deviceManager.devices.map { devices ->
        devices.values
            .filter { device -> device.isPaired && device.isReachable }
            .map { it.toUiModel() }
    }.stateIn(
        scope = lifecycleScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val wifiToTrusted: StateFlow<Pair<Boolean, Boolean>> = backgroundServiceData.isConnectedToNonCellularNetwork.map {
        it to trustedNetworkHelper.getIsTrustedNetwork()
    }.stateIn(
        scope = lifecycleScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true to true
    )

    private suspend fun refreshDevicesAction() {
        isRefreshing = true

        BackgroundService.forceRefreshConnections(context = this)
        delay(1500.milliseconds)
        isRefreshing = false
    }

    private fun deviceClicked(
        device: Device,
        intent: Intent
    ) {
        val plugin: SharePlugin? = deviceManager.getDevicePlugin(device.deviceId, SharePlugin::class.java)
        lifecycleScope.launch {
            plugin?.share(intent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KdeTheme {
                val scope = rememberCoroutineScope()
                val devices by uiDevices.collectAsStateWithLifecycle()
                val wifiToTrustedValue by wifiToTrusted.collectAsStateWithLifecycle()
                DeviceSelectScreen(
                    devices = devices,
                    pageTitle = stringResource(R.string.share),
                    actionIcon = painterResource(R.drawable.share),
                    actionDescription = stringResource(R.string.share),
                    isRefreshing = isRefreshing,
                    onDeviceClick = { deviceId ->
                        val device = deviceManager.getDevice(id = deviceId) ?: return@DeviceSelectScreen
                        deviceClicked(device = device, intent = intent)
                    },
                    wifiAvailable = wifiToTrustedValue.first,
                    trustedNetwork = wifiToTrustedValue.second,
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

        val action = intent.action
        if (Intent.ACTION_SEND != action && Intent.ACTION_SEND_MULTIPLE != action) {
            finish()
            return
        }

        if (deviceId != null) {
            val plugin: SharePlugin? = deviceManager.getDevicePlugin(deviceId, SharePlugin::class.java)
            lifecycleScope.launch {
                plugin?.share(intent)
            }
            finish()
        } else {
            Toast.makeText(this, R.string.could_not_find_device, Toast.LENGTH_LONG).show()
            BackgroundService.forceRefreshConnections(context = this) // force a network re-discover
        }
    }

    override fun onStop() {
        super.onStop()
    }
}