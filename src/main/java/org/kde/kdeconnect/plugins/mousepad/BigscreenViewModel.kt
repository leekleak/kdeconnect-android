package org.kde.kdeconnect.plugins.mousepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.koin.core.annotation.InjectedParam

class BigscreenViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    val plugin: MousePadPlugin? = deviceManager.getDevicePlugin(deviceId, MousePadPlugin::class.java)

    fun sendUp() = viewModelScope.launch { plugin?.sendUp() }
    fun sendDown() = viewModelScope.launch { plugin?.sendDown() }
    fun sendLeft() = viewModelScope.launch { plugin?.sendLeft() }
    fun sendRight() = viewModelScope.launch { plugin?.sendRight() }
    fun sendSelect() = viewModelScope.launch { plugin?.sendSelect() }
    fun sendHome() = viewModelScope.launch { plugin?.sendHome() }
    fun sendBack() = viewModelScope.launch { plugin?.sendBack() }
    fun sendText(text: String) = viewModelScope.launch { plugin?.sendText(text) }
}
