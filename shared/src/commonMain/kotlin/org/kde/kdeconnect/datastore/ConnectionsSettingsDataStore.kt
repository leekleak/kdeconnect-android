package org.kde.kdeconnect.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ConnectionsSettingsDataStore(private val dataStore: DataStore<Preferences>) {

    val trustedNetworksRaw: Flow<String> = dataStore.data
        .map { it[KEY_TRUSTED_NETWORKS] ?: "" }
        .distinctUntilChanged()

    val trustedNetworks: Flow<List<String>> = trustedNetworksRaw
        .map { serialized -> serialized.split(NETWORK_SSID_DELIMITER).filter { it.isNotEmpty() } }
        .distinctUntilChanged()

    val allNetworksAllowed: Flow<Boolean> = dataStore.data
        .map { it[KEY_TRUST_ALL_NETWORKS] ?: true }
        .distinctUntilChanged()

    val customDeviceList: Flow<String> = dataStore.data
        .map { preferences -> preferences[KEY_CUSTOM_DEVICE_LIST] ?: "" }
        .distinctUntilChanged()

    suspend fun setTrustedNetworksRaw(serialized: String) {
        dataStore.edit { it[KEY_TRUSTED_NETWORKS] = serialized }
    }

    suspend fun setAllNetworksAllowed(allowed: Boolean) {
        dataStore.edit { it[KEY_TRUST_ALL_NETWORKS] = allowed }
    }

    suspend fun setCustomDeviceList(list: String) {
        dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_DEVICE_LIST] = list
        }
    }

    companion object {
        private val KEY_TRUSTED_NETWORKS = stringPreferencesKey("trusted_network_preference")
        private val KEY_TRUST_ALL_NETWORKS = booleanPreferencesKey("trust_all_network_preference")
        private val KEY_CUSTOM_DEVICE_LIST = stringPreferencesKey("device_list_preference")
        private const val NETWORK_SSID_DELIMITER = "\u0000"
    }
}
