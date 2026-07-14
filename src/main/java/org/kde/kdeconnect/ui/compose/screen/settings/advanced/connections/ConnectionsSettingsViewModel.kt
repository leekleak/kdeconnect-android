package org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper

data class ConnectionsSettingsUiState(
    val trustedNetworks: List<String> = emptyList(),
    val allNetworksAllowed: Boolean = true,
    val currentSSID: String? = null,
    val hasLocationPermission: Boolean = false,
    val customDevices: List<DeviceHost> = emptyList(),
)

class ConnectionsSettingsViewModel(
    application: Application,
    private val connectionsDataStore: ConnectionsSettingsDataStore,
) : AndroidViewModel(application) {
    private val helper = TrustedNetworkHelper(application)

    private val _updateTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ConnectionsSettingsUiState> = combine(
        connectionsDataStore.trustedNetworks,
        connectionsDataStore.allNetworksAllowed,
        connectionsDataStore.customDeviceList,
        _updateTrigger
    ) { trustedNetworks, allNetworksAllowed, customDeviceListRaw, _ ->
        val customDevices = CustomDevicesHelper.deserializeIpList(customDeviceListRaw)
        customDevices.sortBy { it.toString() }

        ConnectionsSettingsUiState(
            trustedNetworks = trustedNetworks,
            allNetworksAllowed = allNetworksAllowed,
            currentSSID = helper.currentSSID,
            hasLocationPermission = helper.hasPermissions,
            customDevices = customDevices
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConnectionsSettingsUiState()
    )

    init {
        uiState.value.customDevices.forEach { it.checkReachable { updateUiState() } }
    }

    fun updateUiState() {
        _updateTrigger.value++
    }

    fun addCustomDevice(host: String) {
        val deviceHost = DeviceHost.toDeviceHostOrNull(host) ?: return
        val currentDevices = uiState.value.customDevices.toMutableList()
        if (currentDevices.none { it.toString() == deviceHost.toString() }) {
            currentDevices.add(deviceHost)
            saveCustomDeviceList(currentDevices)
        }
    }

    fun deleteCustomDevice(device: DeviceHost) {
        val currentDevices = uiState.value.customDevices.filter { it != device }
        saveCustomDeviceList(currentDevices)
    }

    private fun saveCustomDeviceList(devices: List<DeviceHost>) {
        viewModelScope.launch {
            val serialized = devices.joinToString(",") { it.toString() }
            connectionsDataStore.setCustomDeviceList(serialized)
        }
    }

    fun setAllNetworksAllowed(allowed: Boolean) {
        viewModelScope.launch {
            connectionsDataStore.setAllNetworksAllowed(allowed)
        }
    }

    fun addTrustedNetwork(ssid: String) {
        viewModelScope.launch {
            val current = uiState.value.trustedNetworks.toMutableList()
            if (!current.contains(ssid)) {
                current.add(ssid)
                val serialized = current.joinToString("\u0000")
                connectionsDataStore.setTrustedNetworksRaw(serialized)
            }
        }
    }

    fun removeTrustedNetwork(ssid: String) {
        viewModelScope.launch {
            val current = uiState.value.trustedNetworks.toMutableList()
            if (current.remove(ssid)) {
                val serialized = current.joinToString("\u0000")
                connectionsDataStore.setTrustedNetworksRaw(serialized)
            }
        }
    }
}
