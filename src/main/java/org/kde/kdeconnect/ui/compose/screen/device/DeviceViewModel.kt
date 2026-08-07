/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.koin.core.annotation.InjectedParam
import kotlin.time.Duration.Companion.milliseconds

data class DeviceUiState(
    val deviceUiModel: DeviceUiModel = DeviceUiModel(),
    val pairStatus: PairingHandler.PairState = PairingHandler.PairState.NotPaired,
    val verificationKey: String? = null,
    val pluginsWithButtons: List<Plugin.PluginUiButton> = emptyList(),
    val pluginsNeedPermissions: List<Plugin> = emptyList(),
    val batteryInfo: DeviceBatteryInfo? = null,
    val isRefreshing: Boolean = false
)

class DeviceViewModel(
    private val deviceHelper: DeviceHelper,
    private val deviceManager: DeviceManager,
    @InjectedParam private val deviceId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    private val device: Device?
        get() = deviceManager.getDevice(deviceId)

    init {
        viewModelScope.launch {
            device?.let { device ->
                device.state.collect { deviceState ->
                    val pluginsWithButtons = deviceState.loadedPlugins.values.flatMap { it.getUiButtons() }
                    val pluginsNeedPermissions = deviceState.pluginsWithoutPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }

                    _uiState.update { state ->
                        state.copy(
                            deviceUiModel = device.toUiModel(),
                            pairStatus = deviceState.pairStatus,
                            verificationKey = deviceState.verificationKey,
                            pluginsWithButtons = pluginsWithButtons,
                            pluginsNeedPermissions = pluginsNeedPermissions,
                            batteryInfo = null//deviceHelper.getBattery(device)
                        )
                    }
                }
            }
        }
    }

    fun requestPairing() = viewModelScope.launch { device?.requestPairing() }
    fun acceptPairing() = viewModelScope.launch { device?.acceptPairing() }
    fun cancelPairing() = viewModelScope.launch { device?.cancelPairing() }

    fun refreshDevicesAction(context: Context) {
        BackgroundService.forceRefreshConnections(context)
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500.milliseconds)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
