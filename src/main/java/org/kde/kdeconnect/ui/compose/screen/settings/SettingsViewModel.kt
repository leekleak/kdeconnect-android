package org.kde.kdeconnect.ui.compose.screen.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
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
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect_tp.BuildConfig
import java.io.InputStreamReader

class SettingsViewModel(
    private val dataStore: SettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStore.deviceName,
        dataStore.theme,
        dataStore.persistentNotificationEnabled,
        dataStore.bluetoothEnabled,
    ) { deviceName, theme, persistentNotificationEnabled, bluetoothEnabled ->
        SettingsUiState(
            deviceName = deviceName,
            theme = theme,
            persistentNotificationEnabled = persistentNotificationEnabled,
            bluetoothEnabled = bluetoothEnabled,
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

    fun exportLogs(context: Context, uri: Uri) {
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
)
