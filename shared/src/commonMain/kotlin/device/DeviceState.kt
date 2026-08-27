package org.kde.kdeconnect.device

import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.device.DeviceBatteryInfo.Companion.PACKET_TYPE_BATTERY
import org.kde.kdeconnect.plugins.PluginUiButton

data class DeviceState(
    val deviceInfo: DeviceInfo,
    val pairState: PairState,
    val batteryInfo: DeviceBatteryInfo? = null,
    val verificationKey: String? = null,
    val supportedPlugins: List<String> = emptyList(),
    val pluginsByIncomingInterface: Map<String, List<String>> = emptyMap(),
    val links: List<BaseLink> = emptyList(),
    val uiButtons: List<PluginUiButton> = emptyList(),
) {
    val isReachable: Boolean get() = links.isNotEmpty()
}

enum class PairState {
    NotPaired,
    Requested,
    RequestedByPeer,
    Paired
}

/**
 * Specialised data representation of the packets received by [BatteryPlugin].
 *
 * Constants for [thresholdEvent] may be found in [BatteryPlugin].
 *
 * @param currentCharge the amount of charge in the device's battery
 * @param isCharging whether the device is charging
 * @param thresholdEvent status classifier (used to indicate low battery, etc.)
 * @see BatteryPlugin.isLowBattery
 */
data class DeviceBatteryInfo(
    val currentCharge: Int,
    val isCharging: Boolean,
    val thresholdEvent: Int,
) {
    companion object {
        private const val PACKET_TYPE_BATTERY = "kdeconnect.battery"

        /**
         * For use with packets of type [PACKET_TYPE_BATTERY].
         *
         * @throws IllegalArgumentException if the packet type is not [PACKET_TYPE_BATTERY].
         */
        fun fromPacket(np: NetworkPacket): DeviceBatteryInfo {
            require(np.type == PACKET_TYPE_BATTERY) {
                "Packet type must be $PACKET_TYPE_BATTERY"
            }
            return DeviceBatteryInfo(
                np.getInt("currentCharge", 100),
                np.getBoolean("isCharging", false),
                np.getInt("thresholdEvent", 0)
            )
        }
    }
}
