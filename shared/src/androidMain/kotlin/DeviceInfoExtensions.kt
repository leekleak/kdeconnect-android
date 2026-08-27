package org.kde.kdeconnect

import kotlinx.serialization.json.put
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.clipboard.ClipboardPluginInfo
import org.kde.kdeconnect.plugins.share.SharePluginInfo
import java.security.cert.Certificate

val DEFAULT_SHORTCUTS = listOf(ClipboardPluginInfo.pluginKey, SharePluginInfo.pluginKey)

/**
 * Serializes to a NetworkPacket, which LanLinkProvider uses to send this data over the network.
 * The serialization doesn't include the certificate, since LanLink can query that from the socket.
 * Can be deserialized using fromIdentityPacketAndCert(), given a certificate.
 */
fun DeviceInfo.toIdentityPacket(): NetworkPacket =
    NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY).update {
        put("deviceId", id)
        put("deviceName", name)
        put("protocolVersion", protocolVersion)
        put("deviceType", type.toString())
        put("incomingCapabilities", incomingCapabilities.toJsonArray())
        put("outgoingCapabilities", outgoingCapabilities.toJsonArray())
    }



actual fun DeviceInfo.withPopulatedSettings(): DeviceInfo {
    val missingSettings = PluginFactory.availablePlugins.toSet().minus(settings.keys)
    val newInfo = this.copy(
        settings = settings.plus(missingSettings.map { it to PluginFactory.getPluginInfo(it).isEnabledByDefault }),
    )
    return newInfo
}

/**
 * Recreates a DeviceInfo object that was serialized using toIdentityPacket().
 * Since toIdentityPacket() doesn't serialize the certificate, this needs to be passed separately.
 */
fun DeviceInfo.Companion.fromIdentityPacketAndCert(identityPacket: NetworkPacket, certificate: Certificate) =
    with(identityPacket) {
        DeviceInfo(
            id = getString(
                "deviceId",
                ""
            ), // Redundant: We could read this from the certificate instead
            name = DeviceHelper.filterInvalidCharactersFromDeviceNameAndLimitLength(
                getString(
                    "deviceName",
                    "unknown"
                )
            ),
            type = DeviceType.fromString(getString("deviceType", "desktop")),
            certificate = certificate.encoded,
            protocolVersion = getInt("protocolVersion", 0),
            incomingCapabilities = getStringSet("incomingCapabilities") ?: emptySet(),
            outgoingCapabilities = getStringSet("outgoingCapabilities") ?: emptySet(),
            shortcuts = DEFAULT_SHORTCUTS
        )
    }

fun DeviceInfo.Companion.isValidIdentityPacket(identityPacket: NetworkPacket): Boolean = with(identityPacket) {
    type == NetworkPacket.PACKET_TYPE_IDENTITY &&
            DeviceHelper.filterInvalidCharactersFromDeviceNameAndLimitLength(getString("deviceName", "")).isNotBlank() &&
            isValidDeviceId(getString("deviceId", ""))
}
