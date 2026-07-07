package org.kde.kdeconnect.plugins.telephony

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TelephonySettingsUiState(
    val blockedNumbers: String = ""
)

class TelephonySettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(TelephonySettingsUiState())
    val uiState: StateFlow<TelephonySettingsUiState> = _uiState.asStateFlow()

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
        _uiState.update { state ->
            state.copy(
                blockedNumbers = prefs.getString(KEY_PREF_BLOCKED_NUMBERS, "") ?: ""
            )
        }
    }

    fun setBlockedNumbers(numbers: String) {
        prefs.edit { putString(KEY_PREF_BLOCKED_NUMBERS, numbers) }
    }

    companion object {
        private const val KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers"
    }
}
