package org.kde.kdeconnect.plugins.remotekeyboard

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

data class RemoteKeyboardSettingsUiState(
    val editingOnly: Boolean = true
)

class RemoteKeyboardSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(RemoteKeyboardSettingsUiState())
    val uiState: StateFlow<RemoteKeyboardSettingsUiState> = _uiState.asStateFlow()

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
        val editingOnlyKey = getApplication<Application>().getString(R.string.remotekeyboard_editing_only)

        _uiState.update { state ->
            state.copy(
                editingOnly = prefs.getBoolean(editingOnlyKey, true)
            )
        }
    }

    fun setEditingOnly(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.remotekeyboard_editing_only)
        prefs.edit { putBoolean(key, enabled) }
    }
}
