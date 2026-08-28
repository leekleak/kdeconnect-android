package org.kde.kdeconnect.ui.screen.settings.advanced.paired

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.device.DeviceState
import org.kde.kdeconnect.device.PairState

class SavedDevicesViewModel(
    private val deviceManager: DeviceManager,
) : ViewModel() {
    private val _pairingUiState = MutableStateFlow(SavedDevicesUiState())
    val pairingUiState: StateFlow<SavedDevicesUiState> = _pairingUiState.asStateFlow()

    val deviceToUnpair: MutableState<DeviceState?> = mutableStateOf(null)

    init {
        viewModelScope.launch {
            deviceManager.allDeviceStatesMap.collect { map ->
                val devices = map.values.filter { it.pairState == PairState.Paired }.toList()

                _pairingUiState.update { state ->
                    state.copy(saved = devices)
                }
            }
        }
    }

    fun queueUnpair(deviceModel: DeviceState?) {
        deviceToUnpair.value = deviceModel
    }

    fun unpair(deviceModel: DeviceState) {
        viewModelScope.launch {
            val device = deviceManager.getDevice(deviceModel.deviceInfo.id)
            device?.unpair()
            deviceToUnpair.value = null
        }
    }
}

data class SavedDevicesUiState(
    val saved: List<DeviceState> = emptyList(),
)