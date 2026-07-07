package org.kde.kdeconnect.plugins.sms

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.kde.kdeconnect_tp.R

data class SmsSettingsUiState(
    val groupMessageAsMms: Boolean = true,
    val longTextAsMms: Boolean = false,
    val convertToMmsAfter: String = "3"
)

class SmsSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(SmsSettingsUiState())
    val uiState: StateFlow<SmsSettingsUiState> = _uiState.asStateFlow()

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
        val groupKey = getApplication<Application>().getString(R.string.set_group_message_as_mms)
        val longTextKey = getApplication<Application>().getString(R.string.set_long_text_as_mms)
        val convertAfterKey = getApplication<Application>().getString(R.string.convert_to_mms_after)
        val convertAfterDefault = getApplication<Application>().getString(R.string.convert_to_mms_after_default)

        _uiState.update { state ->
            state.copy(
                groupMessageAsMms = prefs.getBoolean(groupKey, true),
                longTextAsMms = prefs.getBoolean(longTextKey, false),
                convertToMmsAfter = prefs.getString(convertAfterKey, convertAfterDefault) ?: convertAfterDefault
            )
        }
    }

    fun setGroupMessageAsMms(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.set_group_message_as_mms)
        prefs.edit { putBoolean(key, enabled) }
    }

    fun setLongTextAsMms(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.set_long_text_as_mms)
        prefs.edit { putBoolean(key, enabled) }
    }

    fun setConvertToMmsAfter(value: String) {
        val key = getApplication<Application>().getString(R.string.convert_to_mms_after)
        prefs.edit { putString(key, value) }
    }
}
