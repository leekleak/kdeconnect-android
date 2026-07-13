/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.plugin

import android.app.Application
import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.ui.AlertDialogFragment
import org.koin.core.annotation.InjectedParam

data class PluginSettingsUiState(
    val deviceName: String = "",
    val plugins: List<PluginSettingsItem> = emptyList()
)

data class PluginSettingsItem(
    val key: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
)

class PluginSettingsViewModel(
    application: Application,
    @InjectedParam private val deviceId: String
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PluginSettingsUiState())
    val uiState: StateFlow<PluginSettingsUiState> = _uiState.asStateFlow()

    private val device: Device?
        get() = KdeConnect.getInstance().getDevice(deviceId)

    private val pluginsChangedListener = Device.PluginsChangedListener {
        viewModelScope.launch { refreshUI() }
    }

    init {
        device?.addPluginsChangedListener(pluginsChangedListener)
        refreshUI()
    }

    override fun onCleared() {
        device?.removePluginsChangedListener(pluginsChangedListener)
    }

    fun refreshUI() {
        val device = device ?: return
        val supportedPlugins = device.supportedPlugins
        val sortedPlugins = PluginFactory.sortPluginList(supportedPlugins)

        val pluginItems = sortedPlugins.map { pluginKey ->
            val info = PluginFactory.getPluginInfo(pluginKey)
            PluginSettingsItem(
                key = pluginKey,
                name = info.displayName,
                description = info.description,
                isEnabled = device.isPluginEnabled(pluginKey),
            )
        }

        _uiState.update { state ->
            state.copy(
                deviceName = device.name,
                plugins = pluginItems
            )
        }
    }


    fun setPluginEnabled(context: Context, pluginKey: String, isEnabled: Boolean) {
        device?.setPluginEnabled(pluginKey, isEnabled)
        if (!isEnabled) return

        val missingPermission = device?.pluginsWithoutPermissions?.contains(pluginKey) ?: return
        val plugin = device?.getPluginIncludingWithoutPermissions(pluginKey) ?: return
        if (missingPermission) {
            val dialog = plugin.permissionExplanationDialog
            if (dialog is AlertDialogFragment) {
                dialog.callback = object : AlertDialogFragment.Callback() {
                    var isPositiveButtonClicked = false
                    override fun onPositiveButtonClicked(): Boolean {
                        isPositiveButtonClicked = true
                        return true
                    }

                    override fun onDismiss() {
                        if (!isPositiveButtonClicked) {
                            device?.setPluginEnabled(pluginKey, false)
                            refreshUI()
                        }
                    }
                }
            }
            (context as? FragmentActivity)?.let {
                dialog.show(it.supportFragmentManager, "permission_explanation")
            }
        }
    }
}
