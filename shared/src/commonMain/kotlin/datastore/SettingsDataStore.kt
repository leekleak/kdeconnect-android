package org.kde.kdeconnect.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.kde.kdeconnect.ui.AppTheme

class SettingsDataStore(
    private val dataStore: DataStore<Preferences>,
    private val defaults: SettingsDefaults
) {

    val deviceName: Flow<String> = dataStore.data
        .map { it[KEY_DEVICE_NAME] ?: defaults.getDefaultDeviceName() }
        .distinctUntilChanged()

    val theme: Flow<AppTheme> = dataStore.data
        .map { preferences -> preferences[KEY_THEME]?.let { AppTheme.valueOf(it) } ?: AppTheme.Default }
        .distinctUntilChanged()

    val bluetoothEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[KEY_BLUETOOTH_ENABLED] ?: false }
        .distinctUntilChanged()

    val deviceId: Flow<String> = dataStore.data
        .map { preferences -> preferences[KEY_DEVICE_ID] ?: "" }
        .distinctUntilChanged()

    val fileDestination: Flow<String> = dataStore.data
        .map { preferences -> preferences[FILE_DESTINATION] ?: defaults.getDefaultFileDestination() }
        .distinctUntilChanged()

    val isFileDestinationDefault: Flow<Boolean> = fileDestination.map { it == defaults.getDefaultFileDestination() }

    val presenterVolumeKeysEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_PRESENTER_VOLUME_KEYS] ?: true }
        .distinctUntilChanged()

    val presenterSensitivity: Flow<Int> = dataStore.data
        .map { it[KEY_PRESENTER_SENSITIVITY] ?: 50 }
        .distinctUntilChanged()

    val certificate: Flow<String> = dataStore.data
        .map { it[KEY_CERTIFICATE] ?: "" }
        .distinctUntilChanged()

    suspend fun setDeviceName(name: String) {
        dataStore.edit { preferences ->
            preferences[KEY_DEVICE_NAME] = name
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[KEY_THEME] = theme.name
        }
    }

    suspend fun setBluetoothEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_BLUETOOTH_ENABLED] = enabled
        }
    }

    suspend fun setDeviceId(id: String) {
        dataStore.edit { preferences ->
            preferences[KEY_DEVICE_ID] = id
        }
    }

    suspend fun setFileDestination(uri: String) {
        dataStore.edit { preferences ->
            preferences[FILE_DESTINATION] = uri
        }
    }

    suspend fun resetFileDestination() {
        dataStore.edit { preferences ->
            preferences.remove(FILE_DESTINATION)
        }
    }

    suspend fun setPresenterVolumeKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PRESENTER_VOLUME_KEYS] = enabled }
    }

    suspend fun setPresenterSensitivity(sensitivity: Int) {
        dataStore.edit { it[KEY_PRESENTER_SENSITIVITY] = sensitivity }
    }

    suspend fun setCertificate(certificate: String) {
        dataStore.edit { it[KEY_CERTIFICATE] = certificate }
    }

    companion object {
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name_preference")
        private val KEY_THEME = stringPreferencesKey("theme_pref")
        private val KEY_BLUETOOTH_ENABLED = booleanPreferencesKey("bluetooth_enabled")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id_preference")
        private val FILE_DESTINATION = stringPreferencesKey("file_destination")
        private val KEY_PRESENTER_VOLUME_KEYS = booleanPreferencesKey("pref_presenter_enable_volume_keys")
        private val KEY_PRESENTER_SENSITIVITY = intPreferencesKey("pref_presenter_sensitivity")
        private val KEY_CERTIFICATE = stringPreferencesKey("certificate")
    }
}
