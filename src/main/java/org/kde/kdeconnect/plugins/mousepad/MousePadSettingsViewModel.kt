package org.kde.kdeconnect.plugins.mousepad

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

data class MousePadSettingsUiState(
    val singleTap: String = "1",
    val doubleTap: String = "2",
    val tripleTap: String = "3",
    val sensitivity: String = "1",
    val accelerationProfile: String = "1",
    val scrollDirection: Boolean = false,
    val scrollSensitivity: Long = 100,
    val gyroEnabled: Boolean = false,
    val gyroSensitivity: Long = 100,
    val mouseButtonsEnabled: Boolean = true,
    val doubleTapDragEnabled: Boolean = true,
    val sendKeystrokesEnabled: Boolean = true,
    val sendSafeTextImmediately: Boolean = true,
    val showKeyboard: Boolean = false,
    // TV settings
    val showBack: Boolean = true,
    val showHome: Boolean = false,
    val hideMouseInput: Boolean = false
)

class MousePadSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(MousePadSettingsUiState())
    val uiState: StateFlow<MousePadSettingsUiState> = _uiState.asStateFlow()

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
        val app = getApplication<Application>()
        _uiState.update { state ->
            state.copy(
                singleTap = prefs.getString(app.getString(R.string.mousepad_single_tap_key), app.getString(R.string.mousepad_default_single)) ?: app.getString(R.string.mousepad_default_single),
                doubleTap = prefs.getString(app.getString(R.string.mousepad_double_tap_key), app.getString(R.string.mousepad_default_double)) ?: app.getString(R.string.mousepad_default_double),
                tripleTap = prefs.getString(app.getString(R.string.mousepad_triple_tap_key), app.getString(R.string.mousepad_default_triple)) ?: app.getString(R.string.mousepad_default_triple),
                sensitivity = prefs.getString(app.getString(R.string.mousepad_sensitivity_key), app.getString(R.string.mousepad_default_sensitivity)) ?: app.getString(R.string.mousepad_default_sensitivity),
                accelerationProfile = prefs.getString(app.getString(R.string.mousepad_acceleration_profile_key), app.getString(R.string.mousepad_default_acceleration_profile)) ?: app.getString(R.string.mousepad_default_acceleration_profile),
                scrollDirection = prefs.getBoolean(app.getString(R.string.mousepad_scroll_direction), false),
                scrollSensitivity = prefs.getInt(app.getString(R.string.mousepad_scroll_sensitivity), 100).toLong(),
                gyroEnabled = prefs.getBoolean(app.getString(R.string.gyro_mouse_enabled), false),
                gyroSensitivity = prefs.getInt(app.getString(R.string.gyro_mouse_sensitivity), 100).toLong(),
                mouseButtonsEnabled = prefs.getBoolean(app.getString(R.string.mousepad_mouse_buttons_enabled_pref), true),
                doubleTapDragEnabled = prefs.getBoolean(app.getString(R.string.mousepad_doubletap_drag_enabled_pref), true),
                sendKeystrokesEnabled = prefs.getBoolean(app.getString(R.string.pref_sendkeystrokes_enabled), true),
                sendSafeTextImmediately = prefs.getBoolean(app.getString(R.string.pref_send_safe_text_immediately), true),
                showKeyboard = prefs.getBoolean(app.getString(R.string.pref_mousepad_show_keyboard), false),
                showBack = prefs.getBoolean(app.getString(R.string.pref_bigscreen_show_back), true),
                showHome = prefs.getBoolean(app.getString(R.string.pref_bigscreen_show_home), false),
                hideMouseInput = prefs.getBoolean(app.getString(R.string.pref_bigscreen_hide_mouse_input), false)
            )
        }
    }

    fun setSingleTap(value: String) = updatePref(R.string.mousepad_single_tap_key, value)
    fun setDoubleTap(value: String) = updatePref(R.string.mousepad_double_tap_key, value)
    fun setTripleTap(value: String) = updatePref(R.string.mousepad_triple_tap_key, value)
    fun setSensitivity(value: String) = updatePref(R.string.mousepad_sensitivity_key, value)
    fun setAccelerationProfile(value: String) = updatePref(R.string.mousepad_acceleration_profile_key, value)
    fun setScrollDirection(value: Boolean) = updatePref(R.string.mousepad_scroll_direction, value)
    fun setScrollSensitivity(value: Long) = updatePref(R.string.mousepad_scroll_sensitivity, value.toInt())
    fun setGyroEnabled(value: Boolean) = updatePref(R.string.gyro_mouse_enabled, value)
    fun setGyroSensitivity(value: Long) = updatePref(R.string.gyro_mouse_sensitivity, value.toInt())
    fun setMouseButtonsEnabled(value: Boolean) = updatePref(R.string.mousepad_mouse_buttons_enabled_pref, value)
    fun setDoubleTapDragEnabled(value: Boolean) = updatePref(R.string.mousepad_doubletap_drag_enabled_pref, value)
    fun setSendKeystrokesEnabled(value: Boolean) = updatePref(R.string.pref_sendkeystrokes_enabled, value)
    fun setSendSafeTextImmediately(value: Boolean) = updatePref(R.string.pref_send_safe_text_immediately, value)
    fun setShowKeyboard(value: Boolean) = updatePref(R.string.pref_mousepad_show_keyboard, value)
    
    // TV settings
    fun setShowBack(value: Boolean) = updatePref(R.string.pref_bigscreen_show_back, value)
    fun setShowHome(value: Boolean) = updatePref(R.string.pref_bigscreen_show_home, value)
    fun setHideMouseInput(value: Boolean) = updatePref(R.string.pref_bigscreen_hide_mouse_input, value)

    private fun updatePref(keyResId: Int, value: Any) {
        val key = getApplication<Application>().getString(keyResId)
        prefs.edit {
            when (value) {
                is String -> putString(key, value)
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
            }
        }
    }
}
