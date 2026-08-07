/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService.Companion.forceRefreshConnections
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel

class PairingViewModel(
    private val deviceManager: DeviceManager,
    private val backgroundServiceData: BackgroundServiceData,
    private val trustedNetworkHelper: TrustedNetworkHelper,
) : ViewModel() {
    private val _pairingUiState = MutableStateFlow(
        value = PairingUiState(
            isWifiAvailable = false,
            isTrustedNetwork = false,
            hasDuplicateNames = false,
            connected = emptyList(),
            available = emptyList(),
        )
    )
    val pairingUiState: StateFlow<PairingUiState> = _pairingUiState.asStateFlow()

    init {
        viewModelScope.launch {
            backgroundServiceData.isConnectedToNonCellularNetwork.collect { isConnectedToNonCellularNetwork ->
                _pairingUiState.update {
                    it.copy(
                        isWifiAvailable = isConnectedToNonCellularNetwork,
                        isTrustedNetwork = trustedNetworkHelper.getIsTrustedNetwork()
                    )
                }
            }
        }
        viewModelScope.launch {
            deviceManager.allDeviceStatesMap.collect { map ->
                val devices = map.values.filter { it.isReachable || it.pairStatus == PairingHandler.PairState.Paired }
                val deviceList = devices.toList()

                val connected = mutableListOf<DeviceUiModel>()
                val available = mutableListOf<DeviceUiModel>()
                val remembered = mutableListOf<DeviceUiModel>()
                val names = mutableSetOf<String>()
                var hasDuplicateNames = false

                for (device in deviceList) {
                    val paired = device.pairStatus == PairingHandler.PairState.Paired
                    if (device.isReachable || paired) {
                        if (!names.add(device.deviceInfo.name)) hasDuplicateNames = true
                        val uiModel = device.toUiModel()
                        when {
                            device.isReachable && paired -> connected.add(uiModel)
                            device.isReachable && !paired -> available.add(uiModel)
                            else -> remembered.add(uiModel)
                        }
                    }
                }

                _pairingUiState.update { state ->
                    state.copy(
                        hasDuplicateNames = hasDuplicateNames,
                        connected = connected,
                        available = available,
                    )
                }
            }
        }
    }

    fun onRefresh(context: Context) {
        _pairingUiState.update { uiState -> uiState.copy(isRefreshing = true) }

        forceRefreshConnections(context)

        viewModelScope.launch {
            delay(timeMillis = 1500)
            _pairingUiState.update { uiState -> uiState.copy(isRefreshing = false) }
        }
    }
}
