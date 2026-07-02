/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import android.app.Application
import androidx.annotation.StringRes
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
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect_tp.R
import org.koin.core.annotation.InjectedParam
import kotlin.time.Duration.Companion.milliseconds

data class DeviceUiState(
    val deviceName: String = "",
    val pairStatus: PairingHandler.PairState = PairingHandler.PairState.NotPaired,
    val isReachable: Boolean = false,
    val verificationKey: String? = null,
    val pluginsWithButtons: List<Plugin.PluginUiButton> = emptyList(),
    val pluginsNeedPermissions: List<Plugin> = emptyList(),
    val pluginsNeedOptionalPermissions: List<Plugin> = emptyList(),
    val batterySubtitle: String? = null,
    val menuEntries: List<Plugin.PluginUiMenuEntry> = emptyList(),
    val isRefreshing: Boolean = false
)

class DeviceViewModel(application: Application, @InjectedParam private val deviceId: String) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    private val device: Device?
        get() = KdeConnect.getInstance().getDevice(deviceId)

    private val pluginsChangedListener = Device.PluginsChangedListener {
        viewModelScope.launch { refreshUI() }
    }

    private val pairingCallback = object : PairingHandler.PairingCallback {
        override fun incomingPairRequest() {
            viewModelScope.launch { refreshUI() }
        }

        override fun pairingSuccessful() {
            viewModelScope.launch { refreshUI() }
        }

        override fun pairingFailed(error: String) {
            viewModelScope.launch { refreshUI() }
        }

        override fun unpaired(device: Device) {
            viewModelScope.launch { refreshUI() }
        }
    }

    init {
        device?.apply {
            addPairingCallback(pairingCallback)
            addPluginsChangedListener(pluginsChangedListener)
        }
        refreshUI()
    }

    override fun onCleared() {
        device?.apply {
            removePairingCallback(pairingCallback)
            removePluginsChangedListener(pluginsChangedListener)
        }
    }

    fun refreshUI() {
        val device = device ?: return
        
        val pluginsWithButtons = device.loadedPlugins.values.flatMap { it.getUiButtons() }
        val pluginsNeedPermissions = device.pluginsWithoutPermissions.values.toList()
        val pluginsNeedOptionalPermissions = device.pluginsWithoutOptionalPermissions.values.toList()
        val menuEntries = device.loadedPlugins.values.flatMap { it.getUiMenuEntries() }

        _uiState.update { state ->
            state.copy(
                deviceName = device.name,
                pairStatus = device.pairStatus,
                isReachable = device.isReachable,
                verificationKey = device.verificationKey,
                pluginsWithButtons = pluginsWithButtons,
                pluginsNeedPermissions = pluginsNeedPermissions,
                pluginsNeedOptionalPermissions = pluginsNeedOptionalPermissions,
                batterySubtitle = getBatterySubtitle(device),
                menuEntries = menuEntries
            )
        }
    }

    private fun getBatterySubtitle(device: Device): String? {
        val batteryPlugin = device.getPlugin(BatteryPlugin::class.java)
        val info = batteryPlugin?.remoteBatteryInfo ?: return null

        @StringRes
        val resId = when {
            info.isCharging -> R.string.battery_status_charging_format
            BatteryPlugin.isLowBattery(info) -> R.string.battery_status_low_format
            else -> R.string.battery_status_format
        }

        return getApplication<Application>().getString(resId, info.currentCharge)
    }

    fun requestPairing() {
        device?.requestPairing()
        refreshUI()
    }

    fun acceptPairing() {
        device?.acceptPairing()
        refreshUI()
    }

    fun cancelPairing() {
        device?.cancelPairing()
        refreshUI()
    }

    fun unpair() {
        device?.unpair()
        refreshUI()
    }

    fun refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(getApplication())
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500.milliseconds)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
