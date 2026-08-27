package org.kde.kdeconnect.plugins

import org.jetbrains.compose.resources.StringResource

interface PluginInfo {
    val pluginKey: String
    val displayNameRes: StringResource
    val descriptionRes: StringResource
    val isEnabledByDefault: Boolean
    val supportedPacketTypes: Set<String>
    val outgoingPacketTypes: Set<String>
    val lazy: Boolean // If lazy, plugin should be instanced on use only.
}