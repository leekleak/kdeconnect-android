package org.kde.kdeconnect.ui.compose.screen.settings.advanced.calls_and_messages

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
    val blockedNumbers: Set<String> = emptySet(),
    val groupMessageAsMms: Boolean = true,
    val longTextAsMms: Boolean = false,
    val convertToMmsAfter: String = "3"
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
                blockedNumbers = prefs.getString(KEY_PREF_BLOCKED_NUMBERS, "")
                    ?.split(',')?.filter { it != "" }?.toSet() ?: emptySet(),
                groupMessageAsMms = prefs.getBoolean(KEY_PREF_MMS_GROUP, true),
                longTextAsMms = prefs.getBoolean(KEY_PREF_MMS_LONG_TEXT, false),
                convertToMmsAfter = prefs.getString(KEY_PREF_CONVERT_TO_MMS, KEY_PREF_CONVERT_TO_MMS_DEFAULT) ?: KEY_PREF_CONVERT_TO_MMS_DEFAULT
            )
        }
    }

    fun blockNumber(number: String) {
        val numbers = uiState.value.blockedNumbers.plus(number).joinToString(",")
        prefs.edit { putString(KEY_PREF_BLOCKED_NUMBERS, numbers) }
    }

    fun unblockNumber(number: String) {
        val numbers = uiState.value.blockedNumbers.minus(number).joinToString(",")
        prefs.edit { putString(KEY_PREF_BLOCKED_NUMBERS, numbers) }
    }

    fun setGroupMessageAsMms(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PREF_MMS_GROUP, enabled) }
    }

    fun setLongTextAsMms(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PREF_MMS_LONG_TEXT, enabled) }
    }

    fun setConvertToMmsAfter(value: String) {
        prefs.edit { putString(KEY_PREF_CONVERT_TO_MMS, value) }
    }

    companion object {
        private const val KEY_PREF_BLOCKED_NUMBERS = "telephony_blocked_numbers"
        const val KEY_PREF_MMS_GROUP = "set_group_message_as_mms"
        const val KEY_PREF_MMS_LONG_TEXT = "set_long_text_as_mms"
        const val KEY_PREF_CONVERT_TO_MMS = "convert_to_mms_after"
        const val KEY_PREF_CONVERT_TO_MMS_DEFAULT = "3"
    }
}
