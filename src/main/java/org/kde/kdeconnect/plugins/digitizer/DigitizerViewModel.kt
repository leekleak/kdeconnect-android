package org.kde.kdeconnect.plugins.digitizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.kde.kdeconnect.KdeConnect
import org.koin.core.annotation.InjectedParam

class DigitizerViewModel(
    application: Application,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application) {

    val plugin: DigitizerPlugin? = KdeConnect.getInstance().getDevicePlugin(deviceId, DigitizerPlugin::class.java)

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
