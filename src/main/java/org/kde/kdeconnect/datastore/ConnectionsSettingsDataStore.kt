package org.kde.kdeconnect.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class ConnectionsSettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "connections_settings",
    )

    val trustedNetworksRaw: Flow<String> = context.dataStore.data
        .map { it[KEY_TRUSTED_NETWORKS] ?: "" }
        .distinctUntilChanged()

    val trustedNetworks: Flow<List<String>> = trustedNetworksRaw
        .map { serialized -> serialized.split(NETWORK_SSID_DELIMITER).filter { it.isNotEmpty() } }
        .distinctUntilChanged()

    val allNetworksAllowed: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_TRUST_ALL_NETWORKS] ?: true }
        .distinctUntilChanged()

    val customDeviceList: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_CUSTOM_DEVICE_LIST] ?: "" }
        .distinctUntilChanged()

    fun getTrustedNetworksRawBlocking(): String = runBlocking { trustedNetworksRaw.first() }
    fun areAllNetworksAllowedBlocking(): Boolean = runBlocking { allNetworksAllowed.first() }
    fun getCustomDeviceListBlocking(): String = runBlocking { customDeviceList.first() }

    suspend fun setTrustedNetworksRaw(serialized: String) {
        context.dataStore.edit { it[KEY_TRUSTED_NETWORKS] = serialized }
    }

    suspend fun setAllNetworksAllowed(allowed: Boolean) {
        context.dataStore.edit { it[KEY_TRUST_ALL_NETWORKS] = allowed }
    }

    suspend fun setCustomDeviceList(list: String) {
        context.dataStore.edit { preferences ->
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
