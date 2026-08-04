package org.kde.kdeconnect.ui.compose.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.ui.AppTheme
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect_tp.BuildConfig
import java.io.InputStreamReader

class SettingsViewModel(
    private val dataStore: SettingsDataStore,
    private val themeUtil: ThemeUtil
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStore.deviceName,
        dataStore.theme,
        dataStore.bluetoothEnabled,
        dataStore.fileDestination,
        dataStore.isFileDestinationDefault,
    ) { deviceName, theme, bluetoothEnabled, destination, destinationDefault ->
        SettingsUiState(
            deviceName = deviceName,
            theme = theme,
            bluetoothEnabled = bluetoothEnabled,
            fileDestination = destination.toUri(),
            fileDestinationIsDefault = destinationDefault
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

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            dataStore.setTheme(theme)
            themeUtil.applyTheme(theme)
        }
    }

    fun saveStorageLocation(context: Context, uri: Uri) {
        viewModelScope.launch {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            dataStore.setFileDestination(uri.toString())
        }
    }

    fun resetStorageLocation() {
        viewModelScope.launch {
            dataStore.setFileDestination(dataStore.getDefaultDestinationUri().toString())
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

    fun getDisplayPath(context: Context, uri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        val type = split[0]
        val relativePath = if (split.size > 1) split[1] else ""

        val volumeName = if (type == "primary") {
            "~"
        } else {
            getSdCardLabel(context, type) ?: type
        }

        return if (relativePath.isEmpty()) volumeName else "$volumeName/$relativePath"
    }

    fun getSdCardLabel(context: Context, volumeId: String): String? {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return storageManager.storageVolumes.firstOrNull { it.uuid == volumeId }?.getDescription(context)
    }
}

data class SettingsUiState(
    val deviceName: String = "",
    val theme: AppTheme = AppTheme.Default,
    val bluetoothEnabled: Boolean = false,
    val fileDestination: Uri? = null,
    val fileDestinationIsDefault: Boolean = true
)
