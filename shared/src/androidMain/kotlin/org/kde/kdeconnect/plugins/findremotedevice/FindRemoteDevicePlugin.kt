/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.findremotedevice

import kotlinx.coroutines.launch
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.e911_emergency
import org.kde.kdeconnect.generated.resources.find_device
import org.kde.kdeconnect.generated.resources.pref_plugin_findremotedevice
import org.kde.kdeconnect.generated.resources.pref_plugin_findremotedevice_desc
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin

class FindRemoteDevicePlugin(private val device: Device) : Plugin() {
    override val pluginInfo: PermissionPluginInfo = FindRemoteDevicePluginInfo

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean = true
}

object FindRemoteDevicePluginInfo: PermissionPluginInfo(
    pluginKey = "FindRemoteDevicePlugin",
    instantiableClass = FindRemoteDevicePlugin::class.java,
    displayNameRes = Res.string.pref_plugin_findremotedevice,
    descriptionRes = Res.string.pref_plugin_findremotedevice_desc,
    outgoingPacketTypes = setOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    lazy = true
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> = listOf(
        PluginUiButton(
            pluginKey = pluginKey,
            name = Res.string.find_device,
            iconRes = Res.drawable.e911_emergency,
            category = ButtonCategory.CONTROL
        ) { _ ->
            device.getPlugin(FindRemoteDevicePlugin::class.java)?.let {
                it.coroutineScope.launch {
                    device.sendPacket(NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST))
                }
            }
        })
}
