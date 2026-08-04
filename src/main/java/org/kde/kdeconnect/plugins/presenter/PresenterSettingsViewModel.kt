package org.kde.kdeconnect.plugins.presenter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.datastore.SettingsDataStore

data class PresenterSettingsUiState(
    val enableVolumeKeys: Boolean = true,
    val sensitivity: Int = 50
)

class PresenterSettingsViewModel(
    application: Application,
    private val settingsDataStore: SettingsDataStore
) : AndroidViewModel(application) {

    val uiState: StateFlow<PresenterSettingsUiState> = combine(
        settingsDataStore.presenterVolumeKeysEnabled,
        settingsDataStore.presenterSensitivity
    ) { volumeKeysEnabled, sensitivity ->
        PresenterSettingsUiState(
            enableVolumeKeys = volumeKeysEnabled,
            sensitivity = sensitivity
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PresenterSettingsUiState()
    )

    fun setEnableVolumeKeys(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPresenterVolumeKeysEnabled(enabled)
        }
    }

    fun setSensitivity(value: Long) {
        viewModelScope.launch {
            settingsDataStore.setPresenterSensitivity(value.toInt())
        }
    }
}
