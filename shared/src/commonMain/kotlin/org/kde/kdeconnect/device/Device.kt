package org.kde.kdeconnect.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.plugins.Plugin

expect class Device {
    val deviceId: String
    val isReachable: Boolean
    val isPaired: Boolean

    val state: StateFlow<DeviceState>
    fun <T : Plugin> pluginFlow(pluginClass: Class<T>): Flow<T?>
    suspend fun <T : Plugin> getPlugin(pluginClass: Class<T>): T?
    fun addLink(link: BaseLink)
    fun removeLink(link: BaseLink)
    fun isValid(): Boolean
    fun updateDeviceInfo(newDeviceInfo: DeviceInfo)
    fun kill()
}