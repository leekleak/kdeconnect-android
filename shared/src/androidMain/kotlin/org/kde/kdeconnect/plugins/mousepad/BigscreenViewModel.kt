package org.kde.kdeconnect.plugins.mousepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.device.DeviceManager
import org.koin.core.annotation.InjectedParam

class BigscreenViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    private val pluginFlow: StateFlow<MousePadPlugin?> = deviceManager.getDevicePluginFlow(deviceId, MousePadPlugin::class.java)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun sendUp() = viewModelScope.launch { pluginFlow.value?.sendUp() }
    fun sendDown() = viewModelScope.launch { pluginFlow.value?.sendDown() }
    fun sendLeft() = viewModelScope.launch { pluginFlow.value?.sendLeft() }
    fun sendRight() = viewModelScope.launch { pluginFlow.value?.sendRight() }
    fun sendSelect() = viewModelScope.launch { pluginFlow.value?.sendSelect() }
    fun sendHome() = viewModelScope.launch { pluginFlow.value?.sendHome() }
    fun sendBack() = viewModelScope.launch { pluginFlow.value?.sendBack() }
    fun sendText(text: String) = viewModelScope.launch { pluginFlow.value?.sendText(text) }
}
