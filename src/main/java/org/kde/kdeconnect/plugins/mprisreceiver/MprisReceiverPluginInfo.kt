package org.kde.kdeconnect.plugins.mprisreceiver

import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

object MprisReceiverPluginInfo : PluginInfo(
    pluginKey = "MprisReceiverPlugin",
    instantiableClass = MprisReceiverPlugin::class.java,
    displayNameRes = R.string.pref_plugin_mprisreceiver,
    descriptionRes = R.string.pref_plugin_mprisreceiver_desc,
    supportedPacketTypes = arrayOf("kdeconnect.mpris.request"),
    outgoingPacketTypes = arrayOf("kdeconnect.mpris")
)
