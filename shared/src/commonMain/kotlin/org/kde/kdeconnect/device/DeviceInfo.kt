package org.kde.kdeconnect.device

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.json.put
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.filterInvalidCharactersFromDeviceNameAndLimitLength
import org.kde.kdeconnect.toJsonArray
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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
    val shortcuts: List<String> = emptyList()
) {

    /**
     * Serializes to a NetworkPacket, which LanLinkProvider uses to send this data over the network.
     * Can be deserialized using fromIdentityPacketAndCert().
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toIdentityPacket(): NetworkPacket =
        NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY).update {
            put("deviceId", id)
            put("deviceName", name)
            put("protocolVersion", protocolVersion)
            put("deviceType", type.toString())
            put("incomingCapabilities", incomingCapabilities.toJsonArray())
            put("outgoingCapabilities", outgoingCapabilities.toJsonArray())

            val base64Certificate = Base64.Mime.encode(certificate)
            val pemEncodedCertificate = "-----BEGIN CERTIFICATE-----\n$base64Certificate\n-----END CERTIFICATE-----\n"
            put("certificate", pemEncodedCertificate)
        }

    companion object {
        private val DEVICE_ID_REGEX = "^[a-zA-Z0-9_-]{32,38}$".toRegex()

        fun isValidDeviceId(deviceId: String): Boolean = deviceId.matches(DEVICE_ID_REGEX)

        /**
         * Recreates a DeviceInfo object that was serialized using toIdentityPacket().
         * If the certificate is not passed, it will be read from the identity packet.
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromIdentityPacketAndCert(identityPacket: NetworkPacket, certificate: ByteArray? = null) =
            with(identityPacket) {
                val certificateBytes = if (certificate != null) {
                    certificate
                } else {
                    val pemEncodedCertificateString = getString("certificate", "")
                    val base64CertificateString = pemEncodedCertificateString
                        .replace("-----BEGIN CERTIFICATE-----\n", "")
                        .replace("-----END CERTIFICATE-----\n", "")
                    Base64.Mime.decode(base64CertificateString)
                }
                DeviceInfo(
                    id = getString("deviceId", ""),
                    name = filterInvalidCharactersFromDeviceNameAndLimitLength(getString("deviceName", "unknown")),
                    type = DeviceType.fromString(getString("deviceType", "desktop")),
                    certificate = certificateBytes,
                    protocolVersion = getInt("protocolVersion", 0),
                    incomingCapabilities = getStringSet("incomingCapabilities") ?: emptySet(),
                    outgoingCapabilities = getStringSet("outgoingCapabilities") ?: emptySet(),
                    shortcuts = emptyList()
                )
            }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceInfo) return false

        if (protocolVersion != other.protocolVersion) return false
        if (id != other.id) return false
        if (!certificate.contentEquals(other.certificate)) return false
        if (name != other.name) return false
        if (type != other.type) return false
        if (incomingCapabilities != other.incomingCapabilities) return false
        if (outgoingCapabilities != other.outgoingCapabilities) return false
        if (settings != other.settings) return false
        if (trusted != other.trusted) return false
        if (shortcuts != other.shortcuts) return false

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
        result = 31 * result + shortcuts.hashCode()
        return result
    }
}
