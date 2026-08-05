package org.kde.kdeconnect.ui.compose.screen.device.settings

import android.app.Activity.RESULT_OK
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.koin.core.annotation.InjectedParam

data class DeviceSettingsUiState(
    val deviceName: String = "",
    val plugins: List<Plugin> = emptyList()
)

class DeviceSettingsViewModel(
    private val deviceManager: DeviceManager,
    private val navigator: Navigator,
    @InjectedParam private val deviceId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceSettingsUiState())
    val uiState: StateFlow<DeviceSettingsUiState> = _uiState.asStateFlow()

    private val device: Device?
        get() = deviceManager.getDevice(deviceId)

    init {
        viewModelScope.launch {
            device?.state?.collect { deviceState ->
                _uiState.update { state ->
                    state.copy(
                        deviceName = deviceState.deviceInfo.name,
                        plugins = deviceState.loadedPlugins.values.toList()
                    )
                }
            }
        }
    }

    private var pendingPluginKey: String? = null
    private var pendingIsEnabled: Boolean = false

    suspend fun setPluginEnabled(pluginKey: String, isEnabled: Boolean): Boolean {
        val device = device ?: return false
        device.setPluginEnabled(pluginKey, isEnabled)
        val missingPermission = device.pluginsWithoutPermissions.containsKey(pluginKey)
        if (!missingPermission) return false
        if (!isEnabled) return false

        device.setPluginEnabled(pluginKey, false)

        pendingPluginKey = pluginKey
        pendingIsEnabled = isEnabled
        return true
    }

    fun onPermissionResult(resultCode: Int) {
        viewModelScope.launch {
            val pluginKey = pendingPluginKey ?: return@launch
            if (resultCode == RESULT_OK) {
                device?.setPluginEnabled(pluginKey, pendingIsEnabled)
            }
            pendingPluginKey = null
        }
    }

    fun unpair() {
        viewModelScope.launch {
            device?.unpair()
        }
        navigator.setTo(PairingKey)
    }
}
