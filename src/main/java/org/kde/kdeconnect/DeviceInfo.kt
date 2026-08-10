/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect_tp.R
import java.security.cert.Certificate

/**
 * DeviceInfo contains all the properties needed to instantiate a Device.
 */
@Entity(tableName = "devices")
data class DeviceInfo(
    @PrimaryKey @ColumnInfo(name = "deviceId") val id: String,
    val certificate: ByteArray,
    val name: String,
    val type: DeviceType,
    val protocolVersion: Int = 0,
    val incomingCapabilities: Set<String> = emptySet(),
    val outgoingCapabilities: Set<String> = emptySet(),
    val settings: Map<String, Boolean> = emptyMap(),
    val trusted: Boolean = false,
) {

    /**
     * Serializes to a NetworkPacket, which LanLinkProvider uses to send this data over the network.
     * The serialization doesn't include the certificate, since LanLink can query that from the socket.
     * Can be deserialized using fromIdentityPacketAndCert(), given a certificate.
     */
    fun toIdentityPacket(): NetworkPacket =
        NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY).also { np ->
            np["deviceId"] = id
            np["deviceName"] = name
            np["protocolVersion"] = protocolVersion
            np["deviceType"] = type.toString()
            np["incomingCapabilities"] = incomingCapabilities
            np["outgoingCapabilities"] = outgoingCapabilities
        }

    fun withPopulatedSettings(): DeviceInfo {
        val missingSettings = PluginFactory.availablePlugins.toSet().minus(settings.keys)
        val newInfo = this.copy(
            settings = settings.plus(missingSettings.map { it to PluginFactory.getPluginInfo(it).isEnabledByDefault }),
        )
        return newInfo
    }

    companion object {
        /**
         * Recreates a DeviceInfo object that was serialized using toIdentityPacket().
         * Since toIdentityPacket() doesn't serialize the certificate, this needs to be passed separately.
         */
        fun fromIdentityPacketAndCert(identityPacket: NetworkPacket, certificate: Certificate) =
            with(identityPacket) {
                DeviceInfo(
                    id = getString("deviceId"), // Redundant: We could read this from the certificate instead
                    name = DeviceHelper.filterInvalidCharactersFromDeviceNameAndLimitLength(getString("deviceName", "unknown")),
                    type = DeviceType.fromString(getString("deviceType", "desktop")),
                    certificate = certificate.encoded,
                    protocolVersion = getInt("protocolVersion"),
                    incomingCapabilities = getStringSet("incomingCapabilities") ?: emptySet(),
                    outgoingCapabilities = getStringSet("outgoingCapabilities") ?: emptySet()
                )
            }

        fun isValidIdentityPacket(identityPacket: NetworkPacket): Boolean = with(identityPacket) {
            type == NetworkPacket.PACKET_TYPE_IDENTITY &&
                    DeviceHelper.filterInvalidCharactersFromDeviceNameAndLimitLength(getString("deviceName", "")).isNotBlank() &&
                    isValidDeviceId(getString("deviceId", ""))
        }

        private val DEVICE_ID_REGEX = "^[a-zA-Z0-9_-]{32,38}$".toRegex()

        fun isValidDeviceId(deviceId: String): Boolean = deviceId.matches(DEVICE_ID_REGEX)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeviceInfo

        if (protocolVersion != other.protocolVersion) return false
        if (id != other.id) return false
        if (!certificate.contentEquals(other.certificate)) return false
        if (name != other.name) return false
        if (type != other.type) return false
        if (incomingCapabilities != other.incomingCapabilities) return false
        if (outgoingCapabilities != other.outgoingCapabilities) return false
        if (settings != other.settings) return false
        if (trusted != other.trusted) return false

        return true
    }

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + id.hashCode()
        result = 31 * result + certificate.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + incomingCapabilities.hashCode()
        result = 31 * result + outgoingCapabilities.hashCode()
        result = 31 * result + settings.hashCode()
        result = 31 * result + trusted.hashCode()
        return result
    }
}

enum class DeviceType {
    PHONE, TABLET, DESKTOP, LAPTOP, TV;

    override fun toString() =
        when (this) {
            TABLET -> "tablet"
            PHONE -> "phone"
            TV -> "tv"
            LAPTOP -> "laptop"
            else -> "desktop"
        }

    fun getIcon(context: Context) =
        ContextCompat.getDrawable(context, toDrawableId())!!

    @DrawableRes
    fun toDrawableId() =
        when (this) {
            PHONE -> R.drawable.mobile
            TABLET -> R.drawable.tablet
            TV -> R.drawable.tv
            LAPTOP -> R.drawable.laptop_windows
            else -> R.drawable.desktop_windows
        }

    fun toShortcutDrawableId() =
        when (this) {
            PHONE -> R.drawable.ic_device_phone_shortcut
            TABLET -> R.drawable.ic_device_tablet_shortcut
            TV -> R.drawable.ic_device_tv_shortcut
            LAPTOP -> R.drawable.ic_device_laptop_shortcut
            else -> R.drawable.ic_device_desktop_shortcut
        }

    companion object {
        @JvmStatic
        fun fromString(s: String) =
            when (s) {
                "phone" -> PHONE
                "tablet" -> TABLET
                "tv" -> TV
                "laptop" -> LAPTOP
                else -> DESKTOP
            }
    }
}
