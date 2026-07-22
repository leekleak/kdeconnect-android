package org.kde.kdeconnect.ui.compose.screen.presenter

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.lifecycle.AndroidViewModel
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect_tp.R
import org.koin.core.annotation.InjectedParam

class PresenterViewModel(application: Application, deviceManager: DeviceManager, @InjectedParam private val deviceId: String) : AndroidViewModel(application), SensorEventListener {

    val plugin: PresenterPlugin? = deviceManager.getDevicePlugin(deviceId, PresenterPlugin::class.java)

    private var sensitivity = 0.03f
    var volumeKeys = true
        private set

    fun applyPrefs(context: Context) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        var scrollSensitivity = prefs.getInt(context.getString(R.string.pref_presenter_sensitivity), 50)
        scrollSensitivity += 10 // Do not allow near-zero sensitivity
        sensitivity = ((scrollSensitivity / 100f) / 10f) * (6f / 10f)

        volumeKeys = prefs.getBoolean(context.getString(R.string.pref_presenter_enable_volume_keys), true)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val xPos = -event.values[2] * sensitivity
            val yPos = -event.values[0] * sensitivity

            plugin?.sendPointer(xPos, yPos)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //ignored
    }
    
    fun stopPointer() {
        plugin?.stopPointer()
    }
}
