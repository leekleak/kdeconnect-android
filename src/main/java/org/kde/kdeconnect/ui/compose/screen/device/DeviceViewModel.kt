package org.kde.kdeconnect.ui.compose.screen.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.DeviceState
import org.koin.core.annotation.InjectedParam


class DeviceViewModel(
    deviceManager: DeviceManager,
    @InjectedParam private val deviceId: String
) : ViewModel() {
    private val device: Device = deviceManager.getDevice(deviceId)!!

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DeviceState> = device.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = device.state.value
    )
}
