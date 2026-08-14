package org.kde.kdeconnect.ui.screen.device.settings

import androidx.lifecycle.ViewModel
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.plugins.PluginUiButton
import org.koin.core.annotation.InjectedParam

class DeviceShortcutSettingsViewModel(
    @InjectedParam val deviceId: String,
    val deviceManager: DeviceManager
): ViewModel() {
    private val device: Device = deviceManager.getDevice(deviceId)!!

    fun getEnabledShortcuts(): List<PluginUiButton> {
        return device.state.value.deviceInfo.shortcuts
            .flatMap { device.getPlugin(it)?.getUiButtons() ?: emptyList() }
    }

    fun getDisabledShortcuts(): List<PluginUiButton> {
        return device.loadedPlugins.keys
            .filter { key -> key !in device.state.value.deviceInfo.shortcuts }
            .flatMap { device.getPlugin(it)?.getUiButtons() ?: emptyList() }
    }

    fun updateShortcuts(shortcuts: List<String>) {
        device.updateShortcuts(shortcuts)
    }
}

data class DeviceShortcutSettingsState(
    val enabledShortcuts: List<PluginUiButton> = emptyList(),
    val disabledShortcuts: List<PluginUiButton> = emptyList()
)