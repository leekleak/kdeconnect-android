package org.kde.kdeconnect.plugins.digitizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.koin.core.annotation.InjectedParam

class DigitizerViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    private val pluginFlow: StateFlow<DigitizerPlugin?> = deviceManager.getDevicePluginFlow(deviceId, DigitizerPlugin::class.java)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun startSession(width: Int, height: Int, xdpi: Float, ydpi: Float) {
        viewModelScope.launch {
            pluginFlow.value?.startSession(
                width,
                height,
                (xdpi * INCHES_TO_MM).toInt(),
                (ydpi * INCHES_TO_MM).toInt()
            )
        }
    }

    fun endSession() {
        viewModelScope.launch {
            pluginFlow.value?.endSession()
        }
    }

    fun reportEvent(event: ToolEvent) {
        viewModelScope.launch {
            pluginFlow.value?.reportEvent(event)
        }
    }

    companion object {
        private const val INCHES_TO_MM = 0.0393701
    }
}
