package org.kde.kdeconnect.ui.screen.device.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.plugins.PluginUiButton
import org.koin.core.annotation.InjectedParam

class DeviceShortcutSettingsViewModel(
    @InjectedParam val deviceId: String,
    val deviceManager: DeviceManager
): ViewModel() {
    private val device: Device = deviceManager.getDevice(deviceId)!!
    val uiState = device.state.map { state ->
        DeviceShortcutSettingsState(
            enabledShortcuts = state.deviceInfo.shortcuts
                .flatMap { device.getPlugin(it)?.getUiButtons() ?: emptyList() },
            disabledShortcuts = device.loadedPlugins.keys
                .filter { key -> key !in state.deviceInfo.shortcuts }
                .flatMap { device.getPlugin(it)?.getUiButtons() ?: emptyList() },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceShortcutSettingsState()
    )

    fun addShortcut(button: PluginUiButton) {
        device.addShortcut(button)
    }

    fun removeShortcut(button: PluginUiButton) {
        device.removeShortcut(button)
    }
}

data class DeviceShortcutSettingsState(
    val enabledShortcuts: List<PluginUiButton> = emptyList(),
    val disabledShortcuts: List<PluginUiButton> = emptyList()
)