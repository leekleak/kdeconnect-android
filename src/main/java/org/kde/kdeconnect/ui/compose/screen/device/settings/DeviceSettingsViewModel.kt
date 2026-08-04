package org.kde.kdeconnect.ui.compose.screen.device.settings

import android.content.Context
import androidx.fragment.app.FragmentActivity
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
import org.kde.kdeconnect.ui.AlertDialogFragment
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

    fun setPluginEnabled(context: Context, pluginKey: String, isEnabled: Boolean) {
        val device = device ?: return
        device.setPluginEnabled(pluginKey, isEnabled)
        if (!isEnabled) return

        val missingPermission = device.pluginsWithoutPermissions.containsKey(pluginKey)
        val plugin = device.getPluginIncludingWithoutPermissions(pluginKey) ?: return
        if (missingPermission) {
            val dialog = plugin.pluginInfo.getPermissionExplanationDialog(context)
            if (dialog is AlertDialogFragment) {
                dialog.callback = object : AlertDialogFragment.Callback() {
                    var isPositiveButtonClicked = false
                    override fun onPositiveButtonClicked(): Boolean {
                        isPositiveButtonClicked = true
                        return true
                    }

                    override fun onDismiss() {
                        if (!isPositiveButtonClicked) {
                            device.setPluginEnabled(pluginKey, false)
                        }
                    }
                }
            }
            (context as? FragmentActivity)?.let {
                dialog.show(it.supportFragmentManager, "permission_explanation")
            }
        }
    }

    fun unpair() {
        device?.unpair()
        navigator.setTo(PairingKey)
    }
}
