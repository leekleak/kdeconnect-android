package org.kde.kdeconnect.plugins.mpris

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kde.kdeconnect_tp.R

data class MprisSettingsUiState(
    val seekTime: String = "20000",
    val notificationEnabled: Boolean = true,
    val keepWatchingEnabled: Boolean = true
)

class MprisSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(MprisSettingsUiState())
    val uiState: StateFlow<MprisSettingsUiState> = _uiState.asStateFlow()

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
        val timeKey = getApplication<Application>().getString(R.string.mpris_time_key)
        val timeDefault = getApplication<Application>().getString(R.string.mpris_time_default)
        val notificationKey = getApplication<Application>().getString(R.string.mpris_notification_key)
        val keepWatchingKey = getApplication<Application>().getString(R.string.mpris_keepwatching_key)

        _uiState.update { state ->
            state.copy(
                seekTime = prefs.getString(timeKey, timeDefault) ?: timeDefault,
                notificationEnabled = prefs.getBoolean(notificationKey, true),
                keepWatchingEnabled = prefs.getBoolean(keepWatchingKey, true)
            )
        }
    }

    fun setSeekTime(time: String) {
        val key = getApplication<Application>().getString(R.string.mpris_time_key)
        prefs.edit { putString(key, time) }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.mpris_notification_key)
        prefs.edit { putBoolean(key, enabled) }
    }

    fun setKeepWatchingEnabled(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.mpris_keepwatching_key)
        prefs.edit { putBoolean(key, enabled) }
    }
}
