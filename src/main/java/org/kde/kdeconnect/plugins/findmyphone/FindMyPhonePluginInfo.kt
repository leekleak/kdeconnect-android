package org.kde.kdeconnect.plugins.findmyphone

import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

object FindMyPhonePluginInfo : PluginInfo(
    instantiableClass = FindMyPhonePlugin::class.java,
    displayNameRes = R.string.findmydevice_title,
    descriptionRes = R.string.findmyphone_description,
    requiredPermissions = emptyArray(),
    supportedPacketTypes = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    outgoingPacketTypes = emptyArray(),
)
