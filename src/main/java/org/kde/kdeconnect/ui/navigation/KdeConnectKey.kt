package org.kde.kdeconnect.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface KdeConnectKey : NavKey

object KdeConnectKeyConstants {
    const val EXTRA_DEVICE_ID = "deviceId"
    const val EXTRA_PLUGIN_KEY = "pluginKey"
}

@Serializable
data object PairingKey : KdeConnectKey

@Serializable
data object SettingsKey : KdeConnectKey

@Serializable
data object AboutKey : KdeConnectKey
@Serializable
data object LicensesKey : KdeConnectKey

@Serializable
data class DeviceKey(val deviceId: String, val fromDeviceList: Boolean = false) : KdeConnectKey

@Serializable
data class PluginSettingsKey(val deviceId: String) : KdeConnectKey

@Serializable
data class PluginIndividualSettingsKey(val pluginKey: String) : KdeConnectKey

data class PresenterKey(val deviceId: String) : KdeConnectKey
