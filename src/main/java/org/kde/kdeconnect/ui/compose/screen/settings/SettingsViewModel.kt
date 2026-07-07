package org.kde.kdeconnect.ui.compose.screen.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.ui.CustomDevicesActivity
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect_tp.BuildConfig
import java.io.InputStreamReader
import androidx.core.content.edit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            DeviceHelper.KEY_DEVICE_NAME_PREFERENCE -> updateDeviceName()
            KEY_APP_THEME -> updateTheme()
            KEY_BLUETOOTH_ENABLED -> updateBluetoothEnabled()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        updateAll()
    }

    fun updateAll() {
        updateDeviceName()
        updateTheme()
        updatePersistentNotification()
        updateBluetoothEnabled()
        updateCustomDevicesCount()
        updatePlugins()
    }

    private fun updatePlugins() {
        val allPlugins = PluginFactory.availablePlugins.toList()
        val sortedPlugins = PluginFactory.sortPluginList(allPlugins)
        val pluginsWithSettings = sortedPlugins.filter {
            PluginFactory.getPluginInfo(it).hasSettings
        }.map { pluginKey ->
            val info = PluginFactory.getPluginInfo(pluginKey)
            PluginSettingsItem(pluginKey, info.displayName)
        }
        _uiState.update { it.copy(pluginsWithSettings = pluginsWithSettings) }
    }

    private fun updateDeviceName() {
        _uiState.update { it.copy(deviceName = DeviceHelper.getDeviceName(context)) }
    }

    private fun updateTheme() {
        _uiState.update { it.copy(theme = prefs.getString(KEY_APP_THEME, ThemeUtil.DEFAULT_MODE) ?: ThemeUtil.DEFAULT_MODE) }
    }

    private fun updatePersistentNotification() {
        _uiState.update { it.copy(persistentNotificationEnabled = NotificationHelper.isPersistentNotificationEnabled(context)) }
    }

    private fun updateBluetoothEnabled() {
        _uiState.update { it.copy(bluetoothEnabled = prefs.getBoolean(KEY_BLUETOOTH_ENABLED, false)) }
    }

    fun updateCustomDevicesCount() {
        _uiState.update { it.copy(customDevicesCount = CustomDevicesActivity.getCustomDeviceList(context).size) }
    }

    fun setDeviceName(name: String) {
        if (name.isNotBlank()) {
            prefs.edit { putString(DeviceHelper.KEY_DEVICE_NAME_PREFERENCE, name) }
        }
    }

    fun setTheme(theme: String) {
        prefs.edit { putString(KEY_APP_THEME, theme) }
        ThemeUtil.applyTheme(theme)
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        NotificationHelper.setPersistentNotificationEnabled(context, enabled)
        BackgroundService.instance?.changePersistentNotificationVisibility(enabled)
        updatePersistentNotification()
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_BLUETOOTH_ENABLED, enabled) }
    }

    fun exportLogs(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri) ?: return@launch
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d"))
            val reader = InputStreamReader(process.inputStream)
            output.use {
                it.write("KDE Connect ${BuildConfig.VERSION_NAME}\n".toByteArray(Charsets.UTF_8))
                it.write("Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})\n".toByteArray(Charsets.UTF_8))
                IOUtils.copy(reader, it, Charsets.UTF_8)
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    companion object {
        const val KEY_BLUETOOTH_ENABLED: String = "bluetooth_enabled"
        const val KEY_APP_THEME: String = "theme_pref"
    }
}

data class SettingsUiState(
    val deviceName: String = "",
    val theme: String = "",
    val persistentNotificationEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val customDevicesCount: Int = 0,
    val pluginsWithSettings: List<PluginSettingsItem> = emptyList()
)

data class PluginSettingsItem(
    val key: String,
    val name: String
)
