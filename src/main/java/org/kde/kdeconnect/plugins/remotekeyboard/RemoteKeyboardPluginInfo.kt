package org.kde.kdeconnect.plugins.remotekeyboard

import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

object RemoteKeyboardPluginInfo : PluginInfo(
    instantiableClass = RemoteKeyboardPlugin::class.java,
    displayNameRes = R.string.pref_plugin_remotekeyboard,
    descriptionRes = R.string.pref_plugin_remotekeyboard_desc,
    supportedPacketTypes = arrayOf("kdeconnect.mousepad.request"),
    outgoingPacketTypes = arrayOf("kdeconnect.mousepad.echo", "kdeconnect.mousepad.keyboardstate")
)
