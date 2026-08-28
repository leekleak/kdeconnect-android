package org.kde.kdeconnect.ui.screen.settings.advanced.filesystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.sftp_storage_preference_display_name_already_used
import org.kde.kdeconnect.generated.resources.sftp_storage_preference_display_name_cannot_be_empty
import org.kde.kdeconnect.generated.resources.sftp_storage_preference_storage_location_already_configured
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.sftp.SftpPlugin

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
                val jsonArray = Json.parseToJsonElement(jsonString) as JsonArray
                for (i in jsonArray.indices) {
                    storageInfoList.add(SftpPlugin.StorageInfo.fromJson(jsonArray[i].jsonObject))
                }
            } catch (e: SerializationException) {
                LoggerTagged.e(e) { "Couldn't load storage info" }
            }
            storageInfoList.sortBy { it.displayName.lowercase() }
            SftpSettingsUiState(storageInfoList = storageInfoList)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SftpSettingsUiState()
        )

    private fun saveSettings(storageInfoList: List<SftpPlugin.StorageInfo>) {
        val jsonArray = buildJsonArray {
            for (storageInfo in storageInfoList) {
                add(storageInfo.toJson())
            }
        }

        viewModelScope.launch {
            dataStore.setStorageInfoListJson(jsonArray.toString())
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
                    LoggerTagged.e(e) { "Exception releasing permission" }
                }
                false
            } else {
                true
            }
        }
        saveSettings(newList)
    }

    fun isDisplayNameAllowed(displayName: String, excludeUri: Uri? = null): String? {
        if (displayName.isBlank()) {
            return runBlocking { getString(Res.string.sftp_storage_preference_display_name_cannot_be_empty) }
        }
        val alreadyUsed = uiState.value.storageInfoList.any {
            it.displayName == displayName && it.uri != excludeUri
        }
        if (alreadyUsed) {
            return runBlocking { getString(Res.string.sftp_storage_preference_display_name_already_used) }
        }
        return null
    }

    fun isUriAllowed(uri: Uri): String? {
        val alreadyConfigured = uiState.value.storageInfoList.any { it.uri == uri }
        if (alreadyConfigured) {
            return runBlocking { getString(Res.string.sftp_storage_preference_storage_location_already_configured) }
        }
        return null
    }
}
