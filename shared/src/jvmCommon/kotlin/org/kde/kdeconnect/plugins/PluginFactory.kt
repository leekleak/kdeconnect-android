package org.kde.kdeconnect.plugins

import org.kde.kdeconnect.Device

expect class PluginFactoryInfo : PluginInfo

expect object PluginFactory {
    val availablePlugins: Set<String>
    val incomingCapabilities: Set<String>
    val outgoingCapabilities: Set<String>

    fun getPluginInfo(pluginKey: String): PluginFactoryInfo
    fun instantiatePluginForDevice(pluginKey: String, device: Device): Plugin?
    fun pluginsForCapabilities(incoming: Set<String>, outgoing: Set<String>): Set<String>
}

expect fun PluginInfo.getUiButtons(device: Device): List<PluginUiButton>
