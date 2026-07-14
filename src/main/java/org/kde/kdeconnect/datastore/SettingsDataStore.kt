package org.kde.kdeconnect.datastore

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
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
import org.kde.kdeconnect.ui.ThemeUtil

class SettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    val deviceName: Flow<String> = context.dataStore.data
        .map { it[KEY_DEVICE_NAME] ?: Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }
        .distinctUntilChanged()

    val theme: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_THEME] ?: ThemeUtil.DEFAULT_MODE }
        .distinctUntilChanged()

    val bluetoothEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_BLUETOOTH_ENABLED] ?: false }
        .distinctUntilChanged()

    val persistentNotificationEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_PERSISTENT_NOTIFICATION] ?: true }
        .distinctUntilChanged()

    val deviceId: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_DEVICE_ID] ?: "" }
        .distinctUntilChanged()

    val customDeviceList: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_CUSTOM_DEVICE_LIST] ?: "" }
        .distinctUntilChanged()

    // Blocking getters for legacy interop
    fun getDeviceNameBlocking(): String = runBlocking { deviceName.first() }
    fun getThemeBlocking(): String = runBlocking { theme.first() }
    fun getBluetoothEnabledBlocking(): Boolean = runBlocking { bluetoothEnabled.first() }
    fun isPersistentNotificationEnabledBlocking(): Boolean = runBlocking { persistentNotificationEnabled.first() }
    fun getDeviceIdBlocking(): String = runBlocking { deviceId.first() }
    fun getCustomDeviceListBlocking(): String = runBlocking { customDeviceList.first() }

    suspend fun setDeviceName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_NAME] = name
        }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME] = theme
        }
    }

    suspend fun setBluetoothEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLUETOOTH_ENABLED] = enabled
        }
    }

    suspend fun setPersistentNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PERSISTENT_NOTIFICATION] = enabled
        }
    }

    suspend fun setDeviceId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_ID] = id
        }
    }

    suspend fun setDeviceNameFetched(fetched: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEVICE_NAME_FETCHED] = fetched
        }
    }

    suspend fun setCustomDeviceList(list: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CUSTOM_DEVICE_LIST] = list
        }
    }

    companion object {
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name_preference")
        private val KEY_THEME = stringPreferencesKey("theme_pref")
        private val KEY_BLUETOOTH_ENABLED = booleanPreferencesKey("bluetooth_enabled")
        private val KEY_PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistentNotification")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id_preference")
        private val KEY_DEVICE_NAME_FETCHED = booleanPreferencesKey("device_name_downloaded_preference")
        private val KEY_CUSTOM_DEVICE_LIST = stringPreferencesKey("device_list_preference")
    }
}
