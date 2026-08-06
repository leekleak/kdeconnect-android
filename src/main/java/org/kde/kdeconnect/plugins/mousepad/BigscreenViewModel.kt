package org.kde.kdeconnect.plugins.mousepad

import android.app.Application
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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
) : ViewModel() {

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

    fun sendUp() = viewModelScope.launch { plugin?.sendUp() }
    fun sendDown() = viewModelScope.launch { plugin?.sendDown() }
    fun sendLeft() = viewModelScope.launch { plugin?.sendLeft() }
    fun sendRight() = viewModelScope.launch { plugin?.sendRight() }
    fun sendSelect() = viewModelScope.launch { plugin?.sendSelect() }
    fun sendHome() = viewModelScope.launch { plugin?.sendHome() }
    fun sendBack() = viewModelScope.launch { plugin?.sendBack() }
    fun sendText(text: String) = viewModelScope.launch { plugin?.sendText(text) }
}
