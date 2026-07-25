package org.kde.kdeconnect.plugins.digitizer

import androidx.lifecycle.ViewModel
import org.kde.kdeconnect.DeviceManager
import org.koin.core.annotation.InjectedParam

class DigitizerViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    val plugin: DigitizerPlugin? = deviceManager.getDevicePlugin(deviceId, DigitizerPlugin::class.java)

    fun startSession(width: Int, height: Int, xdpi: Float, ydpi: Float) {
        plugin?.startSession(
            width,
            height,
            (xdpi * INCHES_TO_MM).toInt(),
            (ydpi * INCHES_TO_MM).toInt()
        )
    }

    fun endSession() {
        plugin?.endSession()
    }

    fun reportEvent(event: ToolEvent) {
        plugin?.reportEvent(event)
    }

    companion object {
        private const val INCHES_TO_MM = 0.0393701
    }
}
