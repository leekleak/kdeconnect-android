package org.kde.kdeconnect.plugins.runcommand

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.code
import org.kde.kdeconnect.generated.resources.pref_plugin_runcommand
import org.kde.kdeconnect.generated.resources.pref_plugin_runcommand_desc
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND_OUTPUT
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND_REQUEST
import org.kde.kdeconnect.ui.navigation.RunCommandKey

object RunCommandPluginInfo : PermissionPluginInfo(
    pluginKey = "RunCommandPlugin",
    instantiableClass = RunCommandPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_runcommand,
    descriptionRes = Res.string.pref_plugin_runcommand_desc,
    supportedPacketTypes = setOf(PACKET_TYPE_RUNCOMMAND, PACKET_TYPE_RUNCOMMAND_OUTPUT),
    outgoingPacketTypes = setOf(PACKET_TYPE_RUNCOMMAND_REQUEST),
    lazy = false
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> {
        return listOf(
            PluginUiButton(
                pluginKey = pluginKey,
                name = Res.string.pref_plugin_runcommand,
                iconRes = Res.drawable.code,
                category = ButtonCategory.CONTROL
            ) { navigator ->
                navigator.goTo(RunCommandKey(device.deviceId))
            })
    }
}
