/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.digitizer

import android.content.Context
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin.Companion.PACKET_TYPE_DIGITIZER
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin.Companion.PACKET_TYPE_DIGITIZER_SESSION
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.generated.resources.*

class DigitizerPlugin(context: Context, device: Device) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = DigitizerPluginInfo

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        LoggerTagged.e { "The drawing tablet plugin should not be able to receive any packets!" }
        return false
    }

    suspend fun startSession(width: Int, height: Int, resolutionX: Int, resolutionY: Int) {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).apply {
            set("action", "start")
            set("width", width)
            set("height", height)
            set("resolutionX", resolutionX)
            set("resolutionY", resolutionY)
        }
        device.sendPacket(np)
    }

    suspend fun endSession() {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).apply {
            set("action", "end")
        }
        device.sendPacket(np)
    }

    suspend fun reportEvent(event: ToolEvent) {
        LoggerTagged.d { "reportEvent: $event" }

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
    }
}

object DigitizerPluginInfo: PluginInfo(
    pluginKey = "DigitizerPlugin",
    instantiableClass = DigitizerPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_digitizer,
    descriptionRes = Res.string.pref_plugin_digitizer_desc,
    supportedPacketTypes = emptyArray(),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_DIGITIZER_SESSION, PACKET_TYPE_DIGITIZER),
    lazy = true
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> = listOf(
        PluginUiButton(
            pluginKey = pluginKey,
            name = Res.string.use_digitizer,
            iconRes = Res.drawable.stylus_note,
            category = ButtonCategory.CONTROL
        ) { _, navigator ->
            navigator.goTo(DigitizerKey(device.deviceId))
        })
}
