package org.kde.kdeconnect.ui.screen.home

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
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.device.DeviceState
import org.kde.kdeconnect.device.PairState
import org.kde.kdeconnect.forceRefreshConnections
import org.kde.kdeconnect.helpers.TrustedNetworkHelper

class HomeViewModel(
    deviceManager: DeviceManager,
    backgroundServiceData: BackgroundServiceData,
    trustedNetworkHelper: TrustedNetworkHelper,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    val uiState = combine(
        backgroundServiceData.isConnectedToNonCellularNetwork,
        deviceManager.allDeviceStatesMap.map { it.values },
        trustedNetworkHelper.isTrustedNetwork,
        refreshing
    ) { isConnectedToNonCellularNetwork, devices, trustedNetwork,  refreshing ->
        HomeUiState(
            wifiAvailable = isConnectedToNonCellularNetwork,
            trustedNetwork = trustedNetwork,
            connected = devices.filter { it.isReachable && it.pairState == PairState.Paired },
            availableCount = devices.count { it.isReachable && it.pairState != PairState.Paired },
            refreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onRefresh() {
        refreshing.update { true }

        forceRefreshConnections()

        viewModelScope.launch {
            delay(timeMillis = 1500)
            refreshing.update { false }
        }
    }
}

data class HomeUiState(
    val wifiAvailable: Boolean = true,
    val trustedNetwork: Boolean = true,
    val connected: List<DeviceState> = emptyList(),
    val availableCount: Int = 0,
    val refreshing: Boolean = false
)
