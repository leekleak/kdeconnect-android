package org.kde.kdeconnect.ui.compose.screen.settings.advanced.filesystem

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONException
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.sftp.SftpPlugin
import org.kde.kdeconnect_tp.R

data class SftpSettingsUiState(
    val storageInfoList: List<SftpPlugin.StorageInfo> = emptyList(),
)

class SftpSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val pluginKey = Plugin.getPluginKey(SftpPlugin::class.java)
    private val prefs: SharedPreferences = application.getSharedPreferences(
        pluginKey + "_preferences",
        Context.MODE_PRIVATE
    )

    private val _uiState = MutableStateFlow(SftpSettingsUiState())
    val uiState: StateFlow<SftpSettingsUiState> = _uiState.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == getApplication<Application>().getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST)) {
            loadSettings()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        loadSettings()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadSettings() {
        val storageInfoList = mutableListOf<SftpPlugin.StorageInfo>()
        val key = getApplication<Application>().getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST)
        val jsonString = prefs.getString(key, "[]")

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                storageInfoList.add(SftpPlugin.StorageInfo.fromJSON(jsonArray.getJSONObject(i)))
            }
        } catch (e: JSONException) {
            Log.e("SFTPSettingsViewModel", "Couldn't load storage info", e)
        }

        storageInfoList.sortBy { it.displayName.lowercase() }

        _uiState.update { state ->
            state.copy(storageInfoList = storageInfoList)
        }
    }

    private fun saveSettings(storageInfoList: List<SftpPlugin.StorageInfo>) {
        val jsonArray = JSONArray()
        try {
            for (storageInfo in storageInfoList) {
                jsonArray.put(storageInfo.toJSON())
            }
        } catch (ignored: JSONException) {
        }

        val key = getApplication<Application>().getString(SftpPlugin.PREFERENCE_KEY_STORAGE_INFO_LIST)
        prefs.edit {
            putString(key, jsonArray.toString())
        }

        KdeConnect.getInstance().devices.values.forEach { it.launchBackgroundReloadPluginsFromSettings() }
    }

    fun addStorage(storageInfo: SftpPlugin.StorageInfo, takeFlags: Int) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(storageInfo.uri, takeFlags)
        val newList = _uiState.value.storageInfoList + storageInfo
        saveSettings(newList)
    }

    fun updateStorage(oldUri: Uri, newDisplayName: String) {
        val newList = _uiState.value.storageInfoList.map {
            if (it.uri == oldUri) {
                it.copy(displayName = newDisplayName)
            } else {
                it
            }
        }
        saveSettings(newList)
    }

    fun deleteStorages(uris: Set<Uri>) {
        val newList = _uiState.value.storageInfoList.filter { storageInfo ->
            if (uris.contains(storageInfo.uri)) {
                try {
                    getApplication<Application>().contentResolver.releasePersistableUriPermission(
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

    fun isDisplayNameAllowed(displayName: String, excludeUri: Uri? = null): String? {
        if (displayName.isBlank()) {
            return getApplication<Application>().getString(R.string.sftp_storage_preference_display_name_cannot_be_empty)
        }
        val alreadyUsed = _uiState.value.storageInfoList.any {
            it.displayName == displayName && it.uri != excludeUri
        }
        if (alreadyUsed) {
            return getApplication<Application>().getString(R.string.sftp_storage_preference_display_name_already_used)
        }
        return null
    }

    fun isUriAllowed(uri: Uri): String? {
        val alreadyConfigured = _uiState.value.storageInfoList.any { it.uri == uri }
        if (alreadyConfigured) {
            return getApplication<Application>().getString(R.string.sftp_storage_preference_storage_location_already_configured)
        }
        return null
    }
}
