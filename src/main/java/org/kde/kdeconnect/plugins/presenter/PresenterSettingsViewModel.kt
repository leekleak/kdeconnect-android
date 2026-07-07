package org.kde.kdeconnect.plugins.presenter

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

data class PresenterSettingsUiState(
    val enableVolumeKeys: Boolean = true,
    val sensitivity: Long = 50
)

class PresenterSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(PresenterSettingsUiState())
    val uiState: StateFlow<PresenterSettingsUiState> = _uiState.asStateFlow()

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
        val volumeKeysKey = getApplication<Application>().getString(R.string.pref_presenter_enable_volume_keys)
        val sensitivityKey = getApplication<Application>().getString(R.string.pref_presenter_sensitivity)

        _uiState.update { state ->
            state.copy(
                enableVolumeKeys = prefs.getBoolean(volumeKeysKey, true),
                sensitivity = prefs.getInt(sensitivityKey, 50).toLong()
            )
        }
    }

    fun setEnableVolumeKeys(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.pref_presenter_enable_volume_keys)
        prefs.edit { putBoolean(key, enabled) }
    }

    fun setSensitivity(value: Long) {
        val key = getApplication<Application>().getString(R.string.pref_presenter_sensitivity)
        prefs.edit { putInt(key, value.toInt()) }
    }
}
