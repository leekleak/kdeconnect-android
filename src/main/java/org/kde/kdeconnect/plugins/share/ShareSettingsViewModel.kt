package org.kde.kdeconnect.plugins.share

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class ShareSettingsUiState(
    val customDestinationEnabled: Boolean = false,
    val destinationFolderUri: String? = null,
    val destinationFolderSummary: String = "",
    val notificationEnabled: Boolean = true
)

class ShareSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(ShareSettingsUiState())
    val uiState: StateFlow<ShareSettingsUiState> = _uiState.asStateFlow()

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        loadSettings()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        loadSettings()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun loadSettings() {
        val customEnabled = prefs.getBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, false)
        val folderUri = prefs.getString(PREFERENCE_DESTINATION, null)
        val notificationEnabled = prefs.getBoolean(PREFERENCE_NOTIFICATION, true)

        val summary = if (customEnabled && folderUri != null) {
            Uri.parse(folderUri).path ?: folderUri
        } else {
            getDefaultDestinationDirectory().absolutePath
        }

        _uiState.update { state ->
            state.copy(
                customDestinationEnabled = customEnabled,
                destinationFolderUri = folderUri,
                destinationFolderSummary = summary,
                notificationEnabled = notificationEnabled
            )
        }
    }

    fun setCustomDestinationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, enabled) }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(PREFERENCE_NOTIFICATION, enabled) }
    }

    fun saveStorageLocation(uri: Uri) {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        prefs.edit {
            putString(PREFERENCE_DESTINATION, uri.toString())
            putBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, true)
        }
    }

    private fun getDefaultDestinationDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    companion object {
        private const val PREFERENCE_CUSTOMIZE_DESTINATION = "share_destination_custom"
        private const val PREFERENCE_DESTINATION = "share_destination_folder_uri"
        private const val PREFERENCE_NOTIFICATION = "share_notification_preference"

        @JvmStatic
        fun saveStorageLocationPreference(context: Context, uri: Uri) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit {
                putString(PREFERENCE_DESTINATION, uri.toString())
                putBoolean(PREFERENCE_CUSTOMIZE_DESTINATION, true)
            }
        }
    }
}
