package org.kde.kdeconnect.plugins.runcommand

import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND_OUTPUT
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND_REQUEST
import org.kde.kdeconnect_tp.R

object RunCommandPluginInfo : PluginInfo(
    instantiableClass = RunCommandPlugin::class.java,
    displayNameRes = R.string.pref_plugin_runcommand,
    descriptionRes = R.string.pref_plugin_runcommand_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_RUNCOMMAND, PACKET_TYPE_RUNCOMMAND_OUTPUT),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_RUNCOMMAND_REQUEST)
)
