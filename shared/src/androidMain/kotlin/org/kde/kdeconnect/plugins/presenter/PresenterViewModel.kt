package org.kde.kdeconnect.plugins.presenter

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.koin.core.annotation.InjectedParam

class PresenterViewModel(
    deviceManager: DeviceManager,
    settingsDataStore: SettingsDataStore,
    @InjectedParam private val deviceId: String
) : ViewModel(), SensorEventListener {

    private val pluginFlow: StateFlow<PresenterPlugin?> = deviceManager.getDevicePluginFlow(deviceId, PresenterPlugin::class.java)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sensitivity: StateFlow<Float> = settingsDataStore.presenterSensitivity
        .map { (it + 10) * 0.0006f }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.03f)

    val volumeKeys: StateFlow<Boolean> = settingsDataStore.presenterVolumeKeysEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val xPos = -event.values[2] * sensitivity.value
            val yPos = -event.values[0] * sensitivity.value

            viewModelScope.launch {
                pluginFlow.value?.sendPointer(xPos, yPos)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //ignored
    }
    
    fun stopPointer() = viewModelScope.launch { pluginFlow.value?.stopPointer() }
    fun sendNext() = viewModelScope.launch { pluginFlow.value?.sendNext() }
    fun sendPrevious() = viewModelScope.launch { pluginFlow.value?.sendPrevious() }
    fun sendFullscreen() = viewModelScope.launch { pluginFlow.value?.sendFullscreen() }
    fun sendEsc() = viewModelScope.launch { pluginFlow.value?.sendEsc() }
}
