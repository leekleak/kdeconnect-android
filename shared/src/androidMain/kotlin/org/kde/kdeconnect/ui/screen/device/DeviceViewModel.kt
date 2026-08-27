package org.kde.kdeconnect.ui.screen.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.device.DeviceState
import org.kde.kdeconnect.plugins.PluginUiButton
import org.koin.core.annotation.InjectedParam

data class DeviceUiState(
    val deviceState: DeviceState,
    val pluginButtons: List<PluginUiButton> = emptyList()
)

class DeviceViewModel(
    deviceManager: DeviceManager,
    @InjectedParam private val deviceId: String
) : ViewModel() {
    private val device: Device = deviceManager.getDevice(deviceId)!!

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DeviceUiState> = device.state.map { state ->
        DeviceUiState(
            deviceState = state,
            pluginButtons = state.uiButtons.filter { state.deviceInfo.settings[it.pluginKey] == true }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceUiState(
            deviceState = device.state.value,
            pluginButtons = device.state.value.uiButtons.filter { device.deviceInfo.settings[it.pluginKey] == true }
        )
    )
}
