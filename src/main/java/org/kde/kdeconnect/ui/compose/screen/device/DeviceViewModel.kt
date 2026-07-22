/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.Plugin
import org.koin.core.annotation.InjectedParam
import kotlin.time.Duration.Companion.milliseconds

data class DeviceUiState(
    val deviceName: String = "",
    val pairStatus: PairingHandler.PairState = PairingHandler.PairState.NotPaired,
    val isReachable: Boolean = false,
    val verificationKey: String? = null,
    val pluginsWithButtons: List<Plugin.PluginUiButton> = emptyList(),
    val pluginsNeedPermissions: List<Plugin> = emptyList(),
    val batterySubtitle: String? = null,
    val isRefreshing: Boolean = false
)

class DeviceViewModel(
    application: Application,
    private val deviceHelper: DeviceHelper,
    @InjectedParam private val deviceId: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    private val device: Device?
        get() = KdeConnect.getInstance().getDevice(deviceId)

    init {
        viewModelScope.launch {
            device?.let { device ->
                device.state.collect { deviceState ->
                    val pluginsWithButtons = deviceState.loadedPlugins.values.flatMap { it.getUiButtons() }
                    val pluginsNeedPermissions = deviceState.pluginsWithoutPermissions.values.filter { device.isPluginEnabled(it.pluginKey) }

                    _uiState.update { state ->
                        state.copy(
                            deviceName = deviceState.deviceInfo.name,
                            pairStatus = deviceState.pairStatus,
                            isReachable = deviceState.isReachable,
                            verificationKey = deviceState.verificationKey,
                            pluginsWithButtons = pluginsWithButtons,
                            pluginsNeedPermissions = pluginsNeedPermissions,
                            batterySubtitle = deviceHelper.getBatterySubtitle(getApplication<Application>().applicationContext, device),
                        )
                    }
                }
            }
        }
    }

    fun requestPairing() {
        device?.requestPairing()
    }

    fun acceptPairing() {
        device?.acceptPairing()
    }

    fun cancelPairing() {
        device?.cancelPairing()
    }

    fun unpair() {
        device?.unpair()
    }

    fun refreshDevicesAction() {
        BackgroundService.forceRefreshConnections(getApplication())
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500.milliseconds)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
