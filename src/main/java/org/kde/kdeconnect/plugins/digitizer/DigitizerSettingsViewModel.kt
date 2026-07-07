package org.kde.kdeconnect.plugins.digitizer

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kde.kdeconnect_tp.R

data class DigitizerSettingsUiState(
    val hideDrawButton: Boolean = false,
    val drawButtonSide: String = "bottom_left"
)

class DigitizerSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences(
        DigitizerPlugin.PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    private val _uiState = MutableStateFlow(DigitizerSettingsUiState())
    val uiState: StateFlow<DigitizerSettingsUiState> = _uiState.asStateFlow()

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
        val hideDrawButtonKey = getApplication<Application>().getString(R.string.digitizer_preference_key_hide_draw_button)
        val drawButtonSideKey = getApplication<Application>().getString(R.string.digitizer_preference_key_draw_button_side)
        val defaultDrawButtonSide = getApplication<Application>().getString(R.string.digitizer_preference_value_default_draw_button_side)

        _uiState.update { state ->
            state.copy(
                hideDrawButton = prefs.getBoolean(hideDrawButtonKey, false),
                drawButtonSide = prefs.getString(drawButtonSideKey, defaultDrawButtonSide) ?: defaultDrawButtonSide
            )
        }
    }

    fun setHideDrawButton(hide: Boolean) {
        val key = getApplication<Application>().getString(R.string.digitizer_preference_key_hide_draw_button)
        prefs.edit { putBoolean(key, hide) }
    }

    fun setDrawButtonSide(side: String) {
        val key = getApplication<Application>().getString(R.string.digitizer_preference_key_draw_button_side)
        prefs.edit { putString(key, side) }
    }
}
