package org.kde.kdeconnect.ui.compose.screen.settings.advanced.paired

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel

class SavedDevicesViewModel(
    private val deviceManager: DeviceManager,
) : ViewModel() {
    private val _pairingUiState = MutableStateFlow(SavedDevicesUiState())
    val pairingUiState: StateFlow<SavedDevicesUiState> = _pairingUiState.asStateFlow()

    val deviceToUnpair: MutableState<DeviceUiModel?> = mutableStateOf(null)

    init {
        viewModelScope.launch {
            deviceManager.allDeviceStatesMap.collect { map ->
                val devices = map.values.filter { it.pairStatus == PairingHandler.PairState.Paired }.toList()

                _pairingUiState.update { state ->
                    state.copy(saved = devices.map { it.toUiModel() })
                }
            }
        }
    }

    fun queueUnpair(deviceModel: DeviceUiModel?) {
        deviceToUnpair.value = deviceModel
    }

    fun unpair(deviceModel: DeviceUiModel) {
        viewModelScope.launch {
            val device = deviceManager.getDevice(deviceModel.id)
            device?.unpair()
            deviceToUnpair.value = null
        }
    }
}

data class SavedDevicesUiState(
    val saved: List<DeviceUiModel> = emptyList(),
)