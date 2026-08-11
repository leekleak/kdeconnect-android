package org.kde.kdeconnect.ui.compose.screen.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService.Companion.forceRefreshConnections
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.PairState
import org.kde.kdeconnect.helpers.TrustedNetworkHelper

class PairingViewModel(
    backgroundServiceData: BackgroundServiceData,
    trustedNetworkHelper: TrustedNetworkHelper,
    private val deviceManager: DeviceManager,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    val uiState = combine(
        backgroundServiceData.isConnectedToNonCellularNetwork,
        deviceManager.allDeviceStatesMap.map { it.values },
        trustedNetworkHelper.isTrustedNetwork,
        refreshing
    ) { isConnectedToNonCellularNetwork, devices, trustedNetwork, refreshing ->
        PairingUiState(
            wifiAvailable = isConnectedToNonCellularNetwork,
            trustedNetwork = trustedNetwork,
            available = devices.filter { it.isReachable && it.pairState != PairState.Paired },
            refreshing = refreshing
        )

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PairingUiState()
    )

    fun onRefresh(context: Context) {
        refreshing.update { true }

        forceRefreshConnections(context)

        viewModelScope.launch {
            delay(timeMillis = 1500)
            refreshing.update { false }
        }
    }

    fun pair(deviceId: String) {
        val device = deviceManager.getDevice(deviceId) ?: return
        viewModelScope.launch {
            if (device.state.value.pairState != PairState.Requested) {
                device.requestPairing()
            }
        }
    }
}

data class PairingUiState(
    val wifiAvailable: Boolean = true,
    val trustedNetwork: Boolean = true,
    val available: List<DeviceState> = emptyList(),
    val refreshing: Boolean = false
)
