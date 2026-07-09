package org.kde.kdeconnect.plugins.digitizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import org.kde.kdeconnect.KdeConnect
import org.koin.core.annotation.InjectedParam

class DigitizerViewModel(
    application: Application,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application) {

    val plugin: DigitizerPlugin? = KdeConnect.getInstance().getDevicePlugin(deviceId, DigitizerPlugin::class.java)
    
    private val settingsViewModel = DigitizerSettingsViewModel(application)
    val settingsUiState: StateFlow<DigitizerSettingsUiState> = settingsViewModel.uiState

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
