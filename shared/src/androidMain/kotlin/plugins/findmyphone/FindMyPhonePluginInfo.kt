package org.kde.kdeconnect.plugins.findmyphone

import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.findmydevice_title
import org.kde.kdeconnect.generated.resources.findmyphone_description
import org.kde.kdeconnect.plugins.PermissionPluginInfo

object FindMyPhonePluginInfo : PermissionPluginInfo(
    pluginKey = "FindMyPhonePlugin",
    instantiableClass = FindMyPhonePlugin::class.java,
    displayNameRes = Res.string.findmydevice_title,
    descriptionRes = Res.string.findmyphone_description,
    supportedPacketTypes = setOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    lazy = false
)
