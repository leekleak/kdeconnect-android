package org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kde.kdeconnect.helpers.TrustedNetworkHelper

data class ConnectionsSettingsUiState(
    val trustedNetworks: List<String> = emptyList(),
    val allNetworksAllowed: Boolean = true,
    val currentSSID: String? = null,
    val hasLocationPermission: Boolean = false
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
                hasLocationPermission = helper.hasPermissions
            )
        }
    }

    fun setAllNetworksAllowed(allowed: Boolean) {
        helper.allNetworksAllowed = allowed
        // Preference listener will trigger updateUiState
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
