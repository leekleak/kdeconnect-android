package org.kde.kdeconnect.plugins.mousepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.datastore.MousePadSettingsDataStore

data class MousePadSettingsUiState(
    val singleTap: String = "",
    val doubleTap: String = "",
    val tripleTap: String = "",
    val sensitivity: Int = 4,
    val acceleration: Int = 0,
    val scrollDirection: Boolean = false,
    val scrollSensitivity: Long = 100,
    val gyroEnabled: Boolean = false,
    val gyroSensitivity: Long = 100,
    val doubleTapDragEnabled: Boolean = true,
    val sendKeystrokesEnabled: Boolean = true,
    val sendSafeTextImmediately: Boolean = true,
    val showKeyboard: Boolean = false,
)

class MousePadSettingsViewModel(
    private val dataStore: MousePadSettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<MousePadSettingsUiState> = combine(
        dataStore.singleTap,
        dataStore.doubleTap,
        dataStore.tripleTap,
        dataStore.sensitivity,
        dataStore.acceleration,
        dataStore.scrollDirection,
        dataStore.scrollSensitivity,
        dataStore.gyroEnabled,
        dataStore.gyroSensitivity,
        dataStore.doubleTapDragEnabled,
        dataStore.sendKeystrokesEnabled,
        dataStore.sendSafeTextImmediately,
    ) { params: Array<Any> ->
        MousePadSettingsUiState(
            singleTap = params[0] as String,
            doubleTap = params[1] as String,
            tripleTap = params[2] as String,
            sensitivity = params[3] as Int,
            acceleration = params[4] as Int,
            scrollDirection = params[5] as Boolean,
            scrollSensitivity = (params[6] as Int).toLong(),
            gyroEnabled = params[7] as Boolean,
            gyroSensitivity = (params[8] as Int).toLong(),
            doubleTapDragEnabled = params[9] as Boolean,
            sendKeystrokesEnabled = params[10] as Boolean,
            sendSafeTextImmediately = params[11] as Boolean,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MousePadSettingsUiState()
    )

    fun setSingleTap(value: String) = viewModelScope.launch { dataStore.setSingleTap(value) }
    fun setDoubleTap(value: String) = viewModelScope.launch { dataStore.setDoubleTap(value) }
    fun setTripleTap(value: String) = viewModelScope.launch { dataStore.setTripleTap(value) }
    fun setSensitivity(value: Long) = viewModelScope.launch { dataStore.setSensitivity(value.toInt()) }
    fun setAccelerationProfile(value: Long) = viewModelScope.launch { dataStore.setAccelerationProfile(value.toInt()) }
    fun setScrollDirection(value: Boolean) = viewModelScope.launch { dataStore.setScrollDirection(value) }
    fun setScrollSensitivity(value: Long) = viewModelScope.launch { dataStore.setScrollSensitivity(value.toInt()) }
    fun setGyroSensitivity(value: Long) = viewModelScope.launch { dataStore.setGyroSensitivity(value.toInt()) }
    fun setDoubleTapDragEnabled(value: Boolean) = viewModelScope.launch { dataStore.setDoubleTapDragEnabled(value) }
    fun setSendKeystrokesEnabled(value: Boolean) = viewModelScope.launch { dataStore.setSendKeystrokesEnabled(value) }
    fun setSendSafeTextImmediately(value: Boolean) = viewModelScope.launch { dataStore.setSendSafeTextImmediately(value) }
}
