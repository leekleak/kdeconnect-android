package org.kde.kdeconnect.plugins.findmyphone

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kde.kdeconnect_tp.R

data class FindMyPhoneSettingsUiState(
    val ringtoneUri: String = "",
    val ringtoneTitle: String = "",
    val flashlightEnabled: Boolean = false
)

class FindMyPhoneSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(FindMyPhonePlugin.PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(FindMyPhoneSettingsUiState())
    val uiState: StateFlow<FindMyPhoneSettingsUiState> = _uiState.asStateFlow()

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
        val ringtoneKey = getApplication<Application>().getString(R.string.findmyphone_preference_key_ringtone)
        val flashlightKey = getApplication<Application>().getString(R.string.findmyphone_preference_key_flashlight)

        val ringtoneUri = prefs.getString(ringtoneKey, Settings.System.DEFAULT_RINGTONE_URI.toString()) ?: Settings.System.DEFAULT_RINGTONE_URI.toString()
        val flashlightEnabled = prefs.getBoolean(flashlightKey, false)

        val ringtoneTitle = try {
            RingtoneManager.getRingtone(getApplication(), ringtoneUri.toUri()).getTitle(getApplication())
        } catch (_: Exception) {
            ringtoneUri
        }

        _uiState.update { state ->
            state.copy(
                ringtoneUri = ringtoneUri,
                ringtoneTitle = ringtoneTitle,
                flashlightEnabled = flashlightEnabled
            )
        }
    }

    fun setRingtone(uri: String) {
        val key = getApplication<Application>().getString(R.string.findmyphone_preference_key_ringtone)
        prefs.edit { putString(key, uri) }
    }

    fun setFlashlightEnabled(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.findmyphone_preference_key_flashlight)
        prefs.edit { putBoolean(key, enabled) }
    }
}
