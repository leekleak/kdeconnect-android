package org.kde.kdeconnect.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface KdeConnectKey : NavKey

@Serializable
data object PairingKey : KdeConnectKey

@Serializable
data object SettingsKey : KdeConnectKey

@Serializable
data object AboutKey : KdeConnectKey

@Serializable
data class DeviceKey(val deviceId: String, val fromDeviceList: Boolean = false) : KdeConnectKey
