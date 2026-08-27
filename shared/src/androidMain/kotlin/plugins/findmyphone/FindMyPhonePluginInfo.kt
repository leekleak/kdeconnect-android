package org.kde.kdeconnect.plugins.findmyphone

import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.generated.resources.*

object FindMyPhonePluginInfo : PluginInfo(
    pluginKey = "FindMyPhonePlugin",
    instantiableClass = FindMyPhonePlugin::class.java,
    displayNameRes = Res.string.findmydevice_title,
    descriptionRes = Res.string.findmyphone_description,
    requiredPermissions = emptyArray(),
    supportedPacketTypes = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    outgoingPacketTypes = emptyArray(),
    lazy = false
)
