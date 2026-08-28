package org.kde.kdeconnect.ui.screen.device.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.plugins.PluginUiButton
import org.koin.core.annotation.InjectedParam

class DeviceShortcutSettingsViewModel(
    @InjectedParam val deviceId: String,
    val deviceManager: DeviceManager
): ViewModel() {
    private val device: Device = deviceManager.getDevice(deviceId)!!

    val uiState: StateFlow<ShortcutSettingsUiState> = device.state.map { state ->
        val enabledInSettings = state.uiButtons.filter { state.deviceInfo.settings[it.pluginKey] == true }
        ShortcutSettingsUiState(
            enabled = enabledInSettings.filter { it.pluginKey in state.deviceInfo.shortcuts },
            disabled = enabledInSettings.filter { it.pluginKey !in state.deviceInfo.shortcuts }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShortcutSettingsUiState())

    fun updateShortcuts(shortcuts: List<String>) {
        device.updateShortcuts(shortcuts)
    }
}

data class ShortcutSettingsUiState(
    val enabled: List<PluginUiButton> = emptyList(),
    val disabled: List<PluginUiButton> = emptyList()
)
