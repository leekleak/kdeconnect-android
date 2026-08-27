package org.kde.kdeconnect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackgroundServiceData {
    private val _isConnectedToNonCellularNetwork = MutableStateFlow(false)
    val isConnectedToNonCellularNetwork: StateFlow<Boolean> = _isConnectedToNonCellularNetwork.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isConnectedToNonCellularNetwork.value = connected
    }
}