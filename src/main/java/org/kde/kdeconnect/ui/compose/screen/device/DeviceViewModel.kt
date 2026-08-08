/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.koin.core.annotation.InjectedParam

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
    deviceManager: DeviceManager,
    @InjectedParam private val deviceId: String
) : ViewModel() {

    private val device: Device = deviceManager.getDevice(deviceId)!!

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DeviceUiState> = device.state.map { deviceState ->
        val pluginsWithButtons = deviceState.loadedPlugins.values.flatMap { it.getUiButtons() }
        val pluginsNeedPermissions = deviceState.pluginsWithoutPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }
        
        DeviceUiState(
            deviceUiModel = device.toUiModel(),
            pairStatus = deviceState.pairStatus,
            verificationKey = deviceState.verificationKey,
            pluginsWithButtons = pluginsWithButtons,
            pluginsNeedPermissions = pluginsNeedPermissions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceUiState()
    )

    fun requestPairing() = viewModelScope.launch { device.requestPairing() }
    fun acceptPairing() = viewModelScope.launch { device.acceptPairing() }
    fun cancelPairing() = viewModelScope.launch { device.cancelPairing() }
}
