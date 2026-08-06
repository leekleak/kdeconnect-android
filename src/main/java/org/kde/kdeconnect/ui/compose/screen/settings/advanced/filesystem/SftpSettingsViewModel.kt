package org.kde.kdeconnect.ui.compose.screen.settings.advanced.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.plugins.sftp.SftpPlugin
import org.kde.kdeconnect_tp.R

data class SftpSettingsUiState(
    val storageInfoList: List<SftpPlugin.StorageInfo> = emptyList(),
)

class SftpSettingsViewModel(
    private val dataStore: SftpSettingsDataStore,
    private val deviceManager: DeviceManager
) : ViewModel() {

    val uiState: StateFlow<SftpSettingsUiState> = dataStore.storageInfoListJson
        .map { jsonString ->
            val storageInfoList = mutableListOf<SftpPlugin.StorageInfo>()
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    storageInfoList.add(SftpPlugin.StorageInfo.fromJSON(jsonArray.getJSONObject(i)))
                }
            } catch (e: JSONException) {
                Log.e("SFTPSettingsViewModel", "Couldn't load storage info", e)
            }
            storageInfoList.sortBy { it.displayName.lowercase() }
            SftpSettingsUiState(storageInfoList = storageInfoList)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SftpSettingsUiState()
        )

    private fun saveSettings(storageInfoList: List<SftpPlugin.StorageInfo>) {
        val jsonArray = JSONArray()
        try {
            for (storageInfo in storageInfoList) {
                jsonArray.put(storageInfo.toJSON())
            }
        } catch (_: JSONException) {
        }

        viewModelScope.launch {
            dataStore.setStorageInfoListJson(jsonArray.toString())
            deviceManager.devices.values.forEach { it.launchBackgroundReloadPluginsFromSettings() }
        }
    }

    fun addStorage(context: Context, storageInfo: SftpPlugin.StorageInfo, takeFlags: Int) {
        context.contentResolver.takePersistableUriPermission(storageInfo.uri, takeFlags)
        val newList = uiState.value.storageInfoList + storageInfo
        saveSettings(newList)
    }

    fun updateStorage(oldUri: Uri, newDisplayName: String) {
        val newList = uiState.value.storageInfoList.map {
            if (it.uri == oldUri) {
                it.copy(displayName = newDisplayName)
            } else {
                it
            }
        }
        saveSettings(newList)
    }

    fun deleteStorages(context: Context, uris: Set<Uri>) {
        val newList = uiState.value.storageInfoList.filter { storageInfo ->
            if (uris.contains(storageInfo.uri)) {
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        storageInfo.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    Log.e("SFTPSettingsViewModel", "Exception releasing permission", e)
                }
                false
            } else {
                true
            }
        }
        saveSettings(newList)
    }

    fun isDisplayNameAllowed(context: Context, displayName: String, excludeUri: Uri? = null): String? {
        if (displayName.isBlank()) {
            return context.getString(R.string.sftp_storage_preference_display_name_cannot_be_empty)
        }
        val alreadyUsed = uiState.value.storageInfoList.any {
            it.displayName == displayName && it.uri != excludeUri
        }
        if (alreadyUsed) {
            return context.getString(R.string.sftp_storage_preference_display_name_already_used)
        }
        return null
    }

    fun isUriAllowed(context: Context, uri: Uri): String? {
        val alreadyConfigured = uiState.value.storageInfoList.any { it.uri == uri }
        if (alreadyConfigured) {
            return context.getString(R.string.sftp_storage_preference_storage_location_already_configured)
        }
        return null
    }
}
