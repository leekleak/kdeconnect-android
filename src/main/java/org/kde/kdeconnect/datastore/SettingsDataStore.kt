package org.kde.kdeconnect.datastore

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    val fileDestination: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[FILE_DESTINATION] ?: getDefaultDestinationUri().toString() }
        .distinctUntilChanged()

    val isFileDestinationDefault: Flow<Boolean> = fileDestination.map { it == getDefaultDestinationUri().toString() }

    val presenterVolumeKeysEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_PRESENTER_VOLUME_KEYS] ?: true }
        .distinctUntilChanged()

    val presenterSensitivity: Flow<Int> = context.dataStore.data
        .map { it[KEY_PRESENTER_SENSITIVITY] ?: 50 }
        .distinctUntilChanged()

    val certificate: Flow<String> = context.dataStore.data
        .map { it[KEY_CERTIFICATE] ?: "" }
        .distinctUntilChanged()

    // Blocking getters for legacy interop
    fun getDeviceNameBlocking(): String = runBlocking { deviceName.first() }
    fun getThemeBlocking(): String = runBlocking { theme.first() }
    fun getBluetoothEnabledBlocking(): Boolean = runBlocking { bluetoothEnabled.first() }
    fun isPersistentNotificationEnabledBlocking(): Boolean = runBlocking { persistentNotificationEnabled.first() }
    fun getDeviceIdBlocking(): String = runBlocking { deviceId.first() }
    fun isPresenterVolumeKeysEnabledBlocking(): Boolean = runBlocking { presenterVolumeKeysEnabled.first() }
    fun getPresenterSensitivityBlocking(): Int = runBlocking { presenterSensitivity.first() }
    fun getCertificateBlocking(): String = runBlocking { certificate.first() }

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

    suspend fun setFileDestination(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[FILE_DESTINATION] = uri
        }
    }

    suspend fun setPresenterVolumeKeysEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PRESENTER_VOLUME_KEYS] = enabled }
    }

    suspend fun setPresenterSensitivity(sensitivity: Int) {
        context.dataStore.edit { it[KEY_PRESENTER_SENSITIVITY] = sensitivity }
    }

    suspend fun setCertificate(certificate: String) {
        context.dataStore.edit { it[KEY_CERTIFICATE] = certificate }
    }

    fun getDefaultDestinationUri(): Uri {
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOWNLOADS}"
        )
    }

    companion object {
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name_preference")
        private val KEY_THEME = stringPreferencesKey("theme_pref")
        private val KEY_BLUETOOTH_ENABLED = booleanPreferencesKey("bluetooth_enabled")
        private val KEY_PERSISTENT_NOTIFICATION = booleanPreferencesKey("persistentNotification")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id_preference")
        private val FILE_DESTINATION = stringPreferencesKey("file_destination")
        private val KEY_PRESENTER_VOLUME_KEYS = booleanPreferencesKey("pref_presenter_enable_volume_keys")
        private val KEY_PRESENTER_SENSITIVITY = intPreferencesKey("pref_presenter_sensitivity")
        private val KEY_CERTIFICATE = stringPreferencesKey("certificate")
    }
}
