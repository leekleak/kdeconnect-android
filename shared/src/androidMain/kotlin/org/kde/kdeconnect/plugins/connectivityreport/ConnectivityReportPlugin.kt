/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.connectivityreport

import android.Manifest
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.pref_plugin_connectivity_report
import org.kde.kdeconnect.generated.resources.pref_plugin_connectivity_report_desc
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityListener.Companion.getInstance
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityListener.SubscriptionState

class ConnectivityReportPlugin(private val context: Context, private val device: Device) : Plugin() {
    override val pluginInfo: PermissionPluginInfo = ConnectivityReportPluginInfo

    /**
     * Packet used to report the current connectivity state
     *
     * The body should contain a key "signalStrengths" which has a dict that maps
     * a SubscriptionID (opaque value) to a dict with the connection info (See below)
     *
     * For example:
     * {
     *     "signalStrengths": {
     *         "6": {
     *             "networkType": "4G",
     *             "signalStrength": 3
     *         },
     *         "17": {
     *             "networkType": "HSPA",
     *             "signalStrength": 2
     *         },
     *         ...
     *     }
     * }
     */

    var listener = object : ConnectivityListener.StateCallback {
        override fun statesChanged(states : Map<Int, SubscriptionState>) {
            if (states.isEmpty()) {
                return
            }
            val signalStrengths = buildJsonObject {
                states.forEach { (subID: Int, subscriptionState: SubscriptionState) ->
                    val subInfo = buildJsonObject {
                        put("networkType", subscriptionState.networkType)
                        put("signalStrength", subscriptionState.signalStrength)
                    }
                    put(subID.toString(), subInfo)
                }
            }
            val packet = NetworkPacket(PACKET_TYPE_CONNECTIVITY_REPORT).update {
                put("signalStrengths", signalStrengths)
            }

            coroutineScope.launch {
                device.sendPacket(packet)
            }
        }
    }

    override fun onCreate(): Boolean {
        getInstance(context).listenStateChanges(listener)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        getInstance(context).cancelActiveListener(listener)
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    companion object {
        private const val PACKET_TYPE_CONNECTIVITY_REPORT = "kdeconnect.connectivity_report"
    }
}

object ConnectivityReportPluginInfo : PermissionPluginInfo(
    pluginKey = "ConnectivityReportPlugin",
    instantiableClass = ConnectivityReportPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_connectivity_report,
    descriptionRes = Res.string.pref_plugin_connectivity_report_desc,
    isEnabledByDefault = false,
    outgoingPacketTypes = setOf("kdeconnect.connectivity_report"),
    requiredPermissions = setOf(Manifest.permission.READ_PHONE_STATE),
    lazy = false
)
