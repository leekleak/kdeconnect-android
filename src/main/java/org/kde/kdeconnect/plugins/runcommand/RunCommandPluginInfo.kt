package org.kde.kdeconnect.plugins.runcommand

import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

object RunCommandPluginInfo : PluginInfo(
    instantiableClass = RunCommandPlugin::class.java,
    displayNameRes = R.string.pref_plugin_runcommand,
    descriptionRes = R.string.pref_plugin_runcommand_desc,
    supportedPacketTypes = arrayOf("kdeconnect.runcommand", "kdeconnect.runcommand.output"),
    outgoingPacketTypes = arrayOf("kdeconnect.runcommand.request")
)
