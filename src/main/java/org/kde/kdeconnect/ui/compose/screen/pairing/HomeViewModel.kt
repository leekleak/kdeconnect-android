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
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel

class HomeViewModel(
    deviceManager: DeviceManager,
    backgroundServiceData: BackgroundServiceData,
    private val trustedNetworkHelper: TrustedNetworkHelper,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    val uiState = combine(
        backgroundServiceData.isConnectedToNonCellularNetwork,
        deviceManager.allDeviceStatesMap.map { it.values },
        refreshing
    ) { isConnectedToNonCellularNetwork, devices, refreshing ->
        HomeUiState(
            wifiAvailable = isConnectedToNonCellularNetwork,
            trustedNetwork = trustedNetworkHelper.getIsTrustedNetwork(),
            connected = devices.filter { it.isReachable && it.pairStatus == PairingHandler.PairState.Paired }.map { it.toUiModel() },
            availableCount = devices.count { it.isReachable && it.pairStatus != PairingHandler.PairState.Paired },
            refreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onRefresh(context: Context) {
        refreshing.update { true }

        forceRefreshConnections(context)

        viewModelScope.launch {
            delay(timeMillis = 1500)
            refreshing.update { false }
        }
    }
}

data class HomeUiState(
    val wifiAvailable: Boolean = true,
    val trustedNetwork: Boolean = true,
    val connected: List<DeviceUiModel> = emptyList(),
    val availableCount: Int = 0,
    val refreshing: Boolean = false
)
