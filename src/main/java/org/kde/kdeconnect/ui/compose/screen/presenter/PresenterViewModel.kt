package org.kde.kdeconnect.ui.compose.screen.presenter

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.koin.core.annotation.InjectedParam

class PresenterViewModel(
    application: Application,
    deviceManager: DeviceManager,
    settingsDataStore: SettingsDataStore,
    @InjectedParam private val deviceId: String
) : AndroidViewModel(application), SensorEventListener {

    val plugin: PresenterPlugin? = deviceManager.getDevicePlugin(deviceId, PresenterPlugin::class.java)

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
                plugin?.sendPointer(xPos, yPos)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //ignored
    }
    
    fun stopPointer() = viewModelScope.launch { plugin?.stopPointer() }
    fun sendNext() = viewModelScope.launch { plugin?.sendNext() }
    fun sendPrevious() = viewModelScope.launch { plugin?.sendPrevious() }
    fun sendFullscreen() = viewModelScope.launch { plugin?.sendFullscreen() }
    fun sendEsc() = viewModelScope.launch { plugin?.sendEsc() }
}
