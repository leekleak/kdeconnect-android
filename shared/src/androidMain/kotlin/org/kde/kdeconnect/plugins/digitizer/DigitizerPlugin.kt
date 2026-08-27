/*
 * SPDX-FileCopyrightText: 2025 Martin Sh <hemisputnik@proton.me>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.digitizer

import kotlinx.serialization.json.put
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.pref_plugin_digitizer
import org.kde.kdeconnect.generated.resources.pref_plugin_digitizer_desc
import org.kde.kdeconnect.generated.resources.stylus_note
import org.kde.kdeconnect.generated.resources.use_digitizer
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin.Companion.PACKET_TYPE_DIGITIZER
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin.Companion.PACKET_TYPE_DIGITIZER_SESSION
import org.kde.kdeconnect.ui.navigation.DigitizerKey

class DigitizerPlugin(private val device: Device) : Plugin() {
    override val pluginInfo: PermissionPluginInfo = DigitizerPluginInfo

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        LoggerTagged.e { "The drawing tablet plugin should not be able to receive any packets!" }
        return false
    }

    suspend fun startSession(width: Int, height: Int, resolutionX: Int, resolutionY: Int) {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).update {
            put("action", "start")
            put("width", width)
            put("height", height)
            put("resolutionX", resolutionX)
            put("resolutionY", resolutionY)
        }
        device.sendPacket(np)
    }

    suspend fun endSession() {
        val np = NetworkPacket(PACKET_TYPE_DIGITIZER_SESSION).update {
            put("action", "end")
        }
        device.sendPacket(np)
    }

    suspend fun reportEvent(event: ToolEvent) {
        LoggerTagged.d { "reportEvent: $event" }

        val np = NetworkPacket(PACKET_TYPE_DIGITIZER).update {
            event.active?.let { put("active", it) }
            event.touching?.let { put("touching", it) }
            event.tool?.let { put("tool", it.name) }
            event.x?.let { put("x", it) }
            event.y?.let { put("y", it) }
            event.pressure?.let { put("pressure", it) }
        }
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_DIGITIZER_SESSION = "kdeconnect.digitizer.session"
        const val PACKET_TYPE_DIGITIZER = "kdeconnect.digitizer"
    }
}

object DigitizerPluginInfo: PermissionPluginInfo(
    pluginKey = "DigitizerPlugin",
    instantiableClass = DigitizerPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_digitizer,
    descriptionRes = Res.string.pref_plugin_digitizer_desc,
    outgoingPacketTypes = setOf(PACKET_TYPE_DIGITIZER_SESSION, PACKET_TYPE_DIGITIZER),
    lazy = true
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> = listOf(
        PluginUiButton(
            pluginKey = pluginKey,
            name = Res.string.use_digitizer,
            iconRes = Res.drawable.stylus_note,
            category = ButtonCategory.CONTROL
        ) { navigator ->
            navigator.goTo(DigitizerKey(device.deviceId))
        })
}
