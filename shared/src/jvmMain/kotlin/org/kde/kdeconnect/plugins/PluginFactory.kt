package org.kde.kdeconnect.plugins

import org.jetbrains.compose.resources.StringResource
import org.kde.kdeconnect.Device

actual class PluginFactoryInfo(
    override val pluginKey: String,
    override val displayNameRes: StringResource,
    override val descriptionRes: StringResource,
    override val isEnabledByDefault: Boolean = true,
    override val supportedPacketTypes: Set<String> = emptySet(),
    override val outgoingPacketTypes: Set<String> = emptySet(),
    override val lazy: Boolean
) : PluginInfo

actual object PluginFactory {
    actual val availablePlugins: Set<String> = emptySet()
    actual val incomingCapabilities: Set<String> = emptySet()
    actual val outgoingCapabilities: Set<String> = emptySet()

    actual fun getPluginInfo(pluginKey: String): PluginFactoryInfo {
        throw NoSuchElementException("No plugin info for $pluginKey")
    }

    actual fun instantiatePluginForDevice(pluginKey: String, device: Device): Plugin? {
        return null
    }

    actual fun pluginsForCapabilities(incoming: Set<String>, outgoing: Set<String>): Set<String> {
        return emptySet()
    }
}

actual fun PluginInfo.getUiButtons(device: Device): List<PluginUiButton> = emptyList()
