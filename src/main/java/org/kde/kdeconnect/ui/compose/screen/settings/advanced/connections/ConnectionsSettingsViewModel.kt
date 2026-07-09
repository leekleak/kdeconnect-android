package org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper

data class ConnectionsSettingsUiState(
    val trustedNetworks: List<String> = emptyList(),
    val allNetworksAllowed: Boolean = true,
    val currentSSID: String? = null,
    val hasLocationPermission: Boolean = false,
    val customDevices: List<DeviceHost> = emptyList(),
)

class ConnectionsSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val helper = TrustedNetworkHelper(application)
    private val _uiState = MutableStateFlow(ConnectionsSettingsUiState())
    val uiState: StateFlow<ConnectionsSettingsUiState> = _uiState.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateUiState()
    }

    init {
        PreferenceManager.getDefaultSharedPreferences(application)
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        updateUiState()
    }

    fun updateUiState() {
        _uiState.update {
            it.copy(
                trustedNetworks = helper.trustedNetworks,
                allNetworksAllowed = helper.allNetworksAllowed,
                currentSSID = helper.currentSSID,
                hasLocationPermission = helper.hasPermissions,
                customDevices = ArrayList(it.customDevices)
            )
        }
    }

    private fun loadCustomDevices(context: Context) {
        val devices = CustomDevicesHelper.getCustomDeviceList(context)
        _uiState.update { it.copy(customDevices = devices) }
        devices.forEach { it.checkReachable { updateUiState() } }
    }

    fun addCustomDevice(host: String, context: Context) {
        val deviceHost = DeviceHost.toDeviceHostOrNull(host) ?: return
        val currentDevices = _uiState.value.customDevices.toMutableList()
        if (currentDevices.none { it.toString() == deviceHost.toString() }) {
            currentDevices.add(deviceHost)
            currentDevices.sortBy { it.toString() }
            CustomDevicesHelper.saveCustomDeviceList(context, currentDevices)
            loadCustomDevices(context)
        }
    }

    fun deleteCustomDevice(device: DeviceHost, context: Context) {
        val currentDevices = _uiState.value.customDevices.filter { it != device }
        CustomDevicesHelper.saveCustomDeviceList(context, currentDevices)
        _uiState.update { it.copy(
            customDevices = currentDevices,
        ) }
    }

    fun setAllNetworksAllowed(allowed: Boolean) {
        allowed.also { helper.allNetworksAllowed = it }
    }

    fun addTrustedNetwork(ssid: String) {
        val current = helper.trustedNetworks.toMutableList()
        if (!current.contains(ssid)) {
            current.add(ssid)
            helper.trustedNetworks = current
        }
    }

    fun removeTrustedNetwork(ssid: String) {
        val current = helper.trustedNetworks.toMutableList()
        if (current.remove(ssid)) {
            helper.trustedNetworks = current
        }
    }

    override fun onCleared() {
        PreferenceManager.getDefaultSharedPreferences(getApplication())
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}
