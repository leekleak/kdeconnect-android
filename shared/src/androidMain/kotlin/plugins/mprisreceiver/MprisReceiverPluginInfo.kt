package org.kde.kdeconnect.plugins.mprisreceiver

import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.pref_plugin_mprisreceiver
import org.kde.kdeconnect.generated.resources.pref_plugin_mprisreceiver_desc
import org.kde.kdeconnect.plugins.PermissionPluginInfo

object MprisReceiverPluginInfo : PermissionPluginInfo(
    pluginKey = "MprisReceiverPlugin",
    instantiableClass = MprisReceiverPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_mprisreceiver,
    descriptionRes = Res.string.pref_plugin_mprisreceiver_desc,
    supportedPacketTypes = setOf("kdeconnect.mpris.request"),
    outgoingPacketTypes = setOf("kdeconnect.mpris"),
    lazy = false,
)
