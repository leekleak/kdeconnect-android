package org.kde.kdeconnect

import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.helpers.filterInvalidCharactersFromDeviceNameAndLimitLength
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.clipboard.ClipboardPluginInfo
import org.kde.kdeconnect.plugins.share.SharePluginInfo

val DEFAULT_SHORTCUTS = listOf(ClipboardPluginInfo.pluginKey, SharePluginInfo.pluginKey)

actual fun DeviceInfo.withPopulatedSettings(): DeviceInfo {
    val missingSettings = PluginFactory.availablePlugins.toSet().minus(settings.keys)
    val newInfo = this.copy(
        settings = settings.plus(missingSettings.map { it to PluginFactory.getPluginInfo(it).isEnabledByDefault }),
    )
    return newInfo
}

fun DeviceInfo.Companion.isValidIdentityPacket(identityPacket: NetworkPacket): Boolean = with(identityPacket) {
    type == NetworkPacket.PACKET_TYPE_IDENTITY &&
            filterInvalidCharactersFromDeviceNameAndLimitLength(getString("deviceName", "")).isNotBlank() &&
            isValidDeviceId(getString("deviceId", ""))
}
