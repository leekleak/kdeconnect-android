/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.pref_plugin_battery
import org.kde.kdeconnect.generated.resources.pref_plugin_battery_desc
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.battery.BatteryPlugin.Companion.PACKET_TYPE_BATTERY
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class BatteryPlugin(private val context: Context, device: Device) : Plugin(device) {
    override val pluginInfo: PluginInfo = BatteryPluginInfo

    /**
     * The latest battery information about the linked device. Will be null if the linked device
     * has not sent us any such information yet.
     *
     *
     * See [DeviceBatteryInfo] for info on which fields we expect to find.
     *
     *
     * @return the most recent packet received from the remote device. May be null
     */

    val lastCharging = AtomicBoolean(false)
    val lastCharge = AtomicInt(-1)
    val lastThresholdEvent = AtomicInt(-1)
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        var isLowBattery = false
        var thresholdEvent = THRESHOLD_EVENT_NONE

        override fun onReceive(context: Context, batteryIntent: Intent) {
            if (batteryIntent.action != Intent.ACTION_BATTERY_CHANGED) {
                thresholdEvent = when (batteryIntent.action) {
                    Intent.ACTION_BATTERY_LOW if !isLowBattery -> THRESHOLD_EVENT_BATTERY_LOW
                    else -> THRESHOLD_EVENT_NONE
                }

                isLowBattery = when (batteryIntent.action) {
                    Intent.ACTION_BATTERY_OKAY -> false
                    Intent.ACTION_BATTERY_LOW -> true
                    else -> isLowBattery
                }

                // Intents with action ACTION_BATTERY_OKAY and ACTION_BATTERY_LOW do not have extras
                // Wait for next ACTION_BATTERY_CHANGED (which should come after) to send the threshold event.
                return
            }

            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

            val currentCharge = level * 100 / scale
            val isCharging = 0 != plugged

            val changedCharging = lastCharging.exchange(isCharging) != isCharging
            val changedCharge = lastCharge.exchange(currentCharge) != currentCharge
            val changedThresholdEvent = lastThresholdEvent.exchange(thresholdEvent) != thresholdEvent

            if (changedCharging || changedCharge || changedThresholdEvent) {
                val np = NetworkPacket(PACKET_TYPE_BATTERY).update {
                    put("currentCharge", currentCharge)
                    put("isCharging", isCharging)
                    put("thresholdEvent", thresholdEvent)
                }

                coroutineScope.launch {
                    device.sendPacket(np)
                }

                // We just send a possible threshold event so reset it so we not create notifications on each change
                thresholdEvent = THRESHOLD_EVENT_NONE
            }
        }
    }

    override fun onCreate(): Boolean {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        val currentState = context.registerReceiver(receiver, intentFilter)
        receiver.onReceive(context, currentState)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        // It's okay to call this only once, even though we registered it for two filters
        context.unregisterReceiver(receiver)
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if (PACKET_TYPE_BATTERY != np.type) {
            return false
        }
        device.updateBatteryInfo(DeviceBatteryInfo.fromPacket(np))
        return true
    }

    companion object {
        const val PACKET_TYPE_BATTERY = "kdeconnect.battery"

        // keep these fields in sync with kdeconnect-kded:BatteryPlugin.h:ThresholdBatteryEvent
        private const val THRESHOLD_EVENT_NONE = 0
        private const val THRESHOLD_EVENT_BATTERY_LOW = 1

        fun isLowBattery(info: DeviceBatteryInfo): Boolean {
            return info.thresholdEvent == THRESHOLD_EVENT_BATTERY_LOW
        }
    }
}

object BatteryPluginInfo : PluginInfo(
    pluginKey = "BatteryPlugin",
    displayNameRes = Res.string.pref_plugin_battery,
    descriptionRes = Res.string.pref_plugin_battery_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_BATTERY),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_BATTERY),
    instantiableClass = BatteryPlugin::class.java,
    lazy = false
)
