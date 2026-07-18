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
data object ConnectionsSettingsKey : KdeConnectKey

@Serializable
data object AboutKey : KdeConnectKey
@Serializable
data object LicensesKey : KdeConnectKey

@Serializable
data class DeviceKey(val deviceId: String, val fromDeviceList: Boolean = false) : KdeConnectKey

@Serializable
data class RunCommandKey(val deviceId: String) : KdeConnectKey

@Serializable
data class DigitizerKey(val deviceId: String) : KdeConnectKey

@Serializable
data class PluginSettingsKey(val deviceId: String) : KdeConnectKey

@Serializable
data class PresenterKey(val deviceId: String) : KdeConnectKey

@Serializable
data class MousePadKey(val deviceId: String) : KdeConnectKey


/**
 * Plugin setting keys
 */
@Serializable
data object MousePadPluginSettingsKey : KdeConnectKey
@Serializable
data object SftpPluginSettingsKey : KdeConnectKey
@Serializable
data object TelephonyPluginSettingsKey : KdeConnectKey
@Serializable
data object SharePluginSettingsKey : KdeConnectKey
@Serializable
data object PresenterPluginSettingsKey : KdeConnectKey
@Serializable
data object NotificationSettingsKey : KdeConnectKey