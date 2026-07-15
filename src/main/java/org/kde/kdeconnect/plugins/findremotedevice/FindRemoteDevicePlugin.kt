/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.findremotedevice

import android.content.Context
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

class FindRemoteDevicePlugin(context: Context, device: Device) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = FindRemoteDevicePluginInfo

    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton(
            name = context.getString(R.string.ring),
            iconRes = R.drawable.arrow_upward,
            category = ButtonCategory.CONTROL
        ) { _ ->
            device.sendPacket(NetworkPacket(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST))
        })

    override fun onPacketReceived(np: NetworkPacket): Boolean = true
}

object FindRemoteDevicePluginInfo: PluginInfo(
    instantiableClass = FindRemoteDevicePlugin::class.java,
    displayNameRes = R.string.pref_plugin_findremotedevice,
    descriptionRes = R.string.pref_plugin_findremotedevice_desc,
    supportedPacketTypes = emptyArray(),
    outgoingPacketTypes = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
)
