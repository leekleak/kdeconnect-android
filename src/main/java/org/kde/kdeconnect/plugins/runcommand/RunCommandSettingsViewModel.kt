package org.kde.kdeconnect.plugins.runcommand

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

data class RunCommandSettingsUiState(
    val nameAsTitle: Boolean = true
)

class RunCommandSettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _uiState = MutableStateFlow(RunCommandSettingsUiState())
    val uiState: StateFlow<RunCommandSettingsUiState> = _uiState.asStateFlow()

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
        val nameAsTitleKey = getApplication<Application>().getString(R.string.set_runcommand_name_as_title)

        _uiState.update { state ->
            state.copy(
                nameAsTitle = prefs.getBoolean(nameAsTitleKey, true)
            )
        }
    }

    fun setNameAsTitle(enabled: Boolean) {
        val key = getApplication<Application>().getString(R.string.set_runcommand_name_as_title)
        prefs.edit { putBoolean(key, enabled) }
    }
}
