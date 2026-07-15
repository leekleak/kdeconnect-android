/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.digitizer

import android.content.Context
import android.util.Log
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin.Companion.PACKET_TYPE_DIGITIZER
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin.Companion.PACKET_TYPE_DIGITIZER_SESSION
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R

class DigitizerPlugin(context: Context, device: Device) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = DigitizerPluginInfo

    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton(
            context.getString(R.string.use_digitizer),
            R.drawable.ic_draw_24dp
        ) { parentActivity ->
            val navigator: Navigator = (parentActivity as MainActivity).scope.get(Navigator::class, null, null)
            navigator.goTo(DigitizerKey(device.deviceId))
        })

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        Log.e(TAG, "The drawing tablet plugin should not be able to receive any packets!")
        return false
    }

    fun startSession(width: Int, height: Int, resolutionX: Int, resolutionY: Int) {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).apply {
            set("action", "start")
            set("width", width)
            set("height", height)
            set("resolutionX", resolutionX)
            set("resolutionY", resolutionY)
        }
        device.sendPacket(np)
    }

    fun endSession() {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).apply {
            set("action", "end")
        }
        device.sendPacket(np)
    }

    fun reportEvent(event: ToolEvent) {
        Log.d(TAG, "reportEvent: $event")

        val np = NetworkPacket(PACKET_TYPE_DIGITIZER).also { packet ->
            event.active?.let { packet["active"] = it }
            event.touching?.let { packet["touching"] = it }
            event.tool?.let { packet["tool"] = it.name }
            event.x?.let { packet["x"] = it }
            event.y?.let { packet["y"] = it }
            event.pressure?.let { packet["pressure"] = it }
        }
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_DIGITIZER_SESSION = "kdeconnect.digitizer.session"
        const val PACKET_TYPE_DIGITIZER = "kdeconnect.digitizer"

        private const val TAG = "DigitizerPlugin"
    }
}

object DigitizerPluginInfo: PluginInfo(
    instantiableClass = DigitizerPlugin::class.java,
    displayNameRes = R.string.pref_plugin_digitizer,
    descriptionRes = R.string.pref_plugin_digitizer_desc,
    supportedPacketTypes = emptyArray(),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_DIGITIZER_SESSION, PACKET_TYPE_DIGITIZER)
)
