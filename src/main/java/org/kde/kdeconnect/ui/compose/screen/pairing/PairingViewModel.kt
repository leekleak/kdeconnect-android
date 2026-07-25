/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService.Companion.forceRefreshConnections
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.helpers.TrustedNetworkHelper.Companion.isTrustedNetwork
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel

class PairingViewModel(
    application: Application,
    private val deviceManager: DeviceManager,
    private val backgroundServiceData: BackgroundServiceData,
) : AndroidViewModel(application) {
    private val _pairingUiState = MutableStateFlow(
        value = PairingUiState(
            isWifiAvailable = false,
            isTrustedNetwork = false,
            hasDuplicateNames = false,
            connected = emptyList(),
            available = emptyList(),
            remembered = emptyList()
        )
    )
    val pairingUiState: StateFlow<PairingUiState> = _pairingUiState.asStateFlow()

    init {
        viewModelScope.launch {
            backgroundServiceData.isConnectedToNonCellularNetwork.collect {
                updateConnectivityInfoHeader(it, application)
            }
        }
        viewModelScope.launch {
            deviceManager.allDeviceStatesMap.collect { map ->
                val devices = map.values.filter { it.isReachable || it.pairStatus == PairingHandler.PairState.Paired }
                buildUiState(devices)
            }
        }
    }

    fun updateConnectivity(
        isWifiAvailable: Boolean,
        isTrustedNetwork: Boolean
    ) {
        _pairingUiState.update {
            it.copy(
                isWifiAvailable = isWifiAvailable,
                isTrustedNetwork = isTrustedNetwork
            )
        }
    }

    fun buildUiState(devices: List<DeviceState>) =
        viewModelScope.launch(context = Dispatchers.Default) {
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
                    remembered = remembered
                )
            }
        }

    fun onRefresh() {
        _pairingUiState.update { uiState -> uiState.copy(isRefreshing = true) }

        forceRefreshConnections(context = getApplication())

        viewModelScope.launch {
            delay(timeMillis = 1500)
            _pairingUiState.update { uiState -> uiState.copy(isRefreshing = false) }
        }
    }

    private fun updateConnectivityInfoHeader(isConnectedToNonCellularNetwork: Boolean, context: Context) {
        updateConnectivity(
            isWifiAvailable = isConnectedToNonCellularNetwork,
            isTrustedNetwork = isTrustedNetwork(context)
        )
    }
}
