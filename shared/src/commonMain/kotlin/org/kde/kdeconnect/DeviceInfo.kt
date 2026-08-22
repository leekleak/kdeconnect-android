package org.kde.kdeconnect

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

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

    companion object {
        private val DEVICE_ID_REGEX = "^[a-zA-Z0-9_-]{32,38}$".toRegex()

        fun isValidDeviceId(deviceId: String): Boolean = deviceId.matches(DEVICE_ID_REGEX)
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
