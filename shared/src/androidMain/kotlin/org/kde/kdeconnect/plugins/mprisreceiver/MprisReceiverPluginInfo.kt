package org.kde.kdeconnect.plugins.mprisreceiver

import org.kde.kdeconnect.generated.resources.*
import org.kde.kdeconnect.plugins.PluginInfo

object MprisReceiverPluginInfo : PluginInfo(
    pluginKey = "MprisReceiverPlugin",
    instantiableClass = MprisReceiverPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_mprisreceiver,
    descriptionRes = Res.string.pref_plugin_mprisreceiver_desc,
    supportedPacketTypes = arrayOf("kdeconnect.mpris.request"),
    outgoingPacketTypes = arrayOf("kdeconnect.mpris"),
    lazy = false,
)
