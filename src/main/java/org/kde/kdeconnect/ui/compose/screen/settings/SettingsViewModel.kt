package org.kde.kdeconnect.ui.compose.screen.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect_tp.BuildConfig
import java.io.InputStreamReader

class SettingsViewModel(
    application: Application,
    private val dataStore: SettingsDataStore
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStore.deviceName,
        dataStore.theme,
        dataStore.persistentNotificationEnabled,
        dataStore.bluetoothEnabled,
        dataStore.customDeviceList
    ) { params: Array<Any> ->
        val deviceName = params[0] as String
        val theme = params[1] as String
        val persistentNotificationEnabled = params[2] as Boolean
        val bluetoothEnabled = params[3] as Boolean
        val customDeviceListSerialized = params[4] as String

        SettingsUiState(
            deviceName = deviceName,
            theme = theme,
            persistentNotificationEnabled = persistentNotificationEnabled,
            bluetoothEnabled = bluetoothEnabled,
            customDevicesCount = CustomDevicesHelper.deserializeIpList(customDeviceListSerialized).size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setDeviceName(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                dataStore.setDeviceName(name)
            }
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            dataStore.setTheme(theme)
            ThemeUtil.applyTheme(theme)
        }
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setPersistentNotificationEnabled(enabled)
            BackgroundService.instance?.changePersistentNotificationVisibility(enabled)
        }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setBluetoothEnabled(enabled)
        }
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
}

data class SettingsUiState(
    val deviceName: String = "",
    val theme: String = "",
    val persistentNotificationEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val customDevicesCount: Int = 0,
)
