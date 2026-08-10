package org.kde.kdeconnect.ui.compose.screen.device.settings

import android.app.Activity.RESULT_OK
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.HomeKey
import org.koin.core.annotation.InjectedParam

data class DeviceSettingsUiState(
    val deviceName: String = "",
    val plugins: Map<String, Boolean> = emptyMap()
)

class DeviceSettingsViewModel(
    deviceManager: DeviceManager,
    private val navigator: Navigator,
    @InjectedParam private val deviceId: String
) : ViewModel() {

    private val device: Device = deviceManager.getDevice(deviceId)!!

    val uiState: StateFlow<DeviceSettingsUiState> = device.state.map { state ->
        DeviceSettingsUiState(
            deviceName = state.deviceInfo.name,
            plugins = state.deviceInfo.settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DeviceSettingsUiState()
    )

    private var pendingPluginKey: String? = null
    private var pendingIsEnabled: Boolean = false

    /**
     * Tries to enable plugin.
     *
     * @return True - Everything went fine. False - unable to set plugin state, probably due to missing permission
     */
    suspend fun setPluginEnabled(pluginKey: String, isEnabled: Boolean, context: Context): Boolean {
        val device = device ?: return false
        device.setPluginEnabled(pluginKey, isEnabled)
        if (!isEnabled) return true // If we're disabling, we don't care about permissions
        val gotPermission = device.getPlugin(pluginKey)?.pluginInfo?.checkRequiredPermissions(context)
        if (gotPermission == true) return true // If plugin is not in "pluginsWithoutPermissions" after being enabled, we know we have been successful

        // Otherwise disable. This method is to be called again after permission has been granted
        device.setPluginEnabled(pluginKey, false)

        // Save what plugin we're waiting for after receiving permssion request result
        pendingPluginKey = pluginKey
        pendingIsEnabled = true
        return false
    }

    fun onPermissionResult(resultCode: Int) {
        viewModelScope.launch {
            val pluginKey = pendingPluginKey ?: return@launch
            if (resultCode == RESULT_OK) {
                device.setPluginEnabled(pluginKey, pendingIsEnabled)
            }
            pendingPluginKey = null
        }
    }

    fun unpair() {
        viewModelScope.launch {
            device.unpair()
            navigator.setTo(HomeKey)
        }
    }
}
