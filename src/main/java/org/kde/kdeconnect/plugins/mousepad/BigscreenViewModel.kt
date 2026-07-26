package org.kde.kdeconnect.plugins.mousepad

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.MousePadSettingsDataStore
import org.koin.core.annotation.InjectedParam

class BigscreenViewModel(
    application: Application,
    deviceManager: DeviceManager,
    private val dataStore: MousePadSettingsDataStore,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application) {

    val plugin: MousePadPlugin? = deviceManager.getDevicePlugin(deviceId, MousePadPlugin::class.java)

    var showBack by mutableStateOf(true)
    var showHome by mutableStateOf(false)
    var micEnabled by mutableStateOf(true)

    init {
        micEnabled = SpeechRecognizer.isRecognitionAvailable(application)

        viewModelScope.launch {
            combine(
                dataStore.showBack,
                dataStore.showHome
            ) { back, home ->
                back to home
            }.collect { (back, home) ->
                showBack = back
                showHome = home
            }
        }
    }

    fun sendUp() = plugin?.sendUp()
    fun sendDown() = plugin?.sendDown()
    fun sendLeft() = plugin?.sendLeft()
    fun sendRight() = plugin?.sendRight()
    fun sendSelect() = plugin?.sendSelect()
    fun sendHome() = plugin?.sendHome()
    fun sendBack() = plugin?.sendBack()

    fun sendText(text: String) {
        plugin?.sendText(text)
    }
}
