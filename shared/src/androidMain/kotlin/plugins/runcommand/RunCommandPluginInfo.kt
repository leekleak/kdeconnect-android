package org.kde.kdeconnect.plugins.runcommand

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND_OUTPUT
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin.Companion.PACKET_TYPE_RUNCOMMAND_REQUEST
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.kde.kdeconnect.generated.resources.*

object RunCommandPluginInfo : PluginInfo(
    pluginKey = "RunCommandPlugin",
    instantiableClass = RunCommandPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_runcommand,
    descriptionRes = Res.string.pref_plugin_runcommand_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_RUNCOMMAND, PACKET_TYPE_RUNCOMMAND_OUTPUT),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_RUNCOMMAND_REQUEST),
    lazy = false
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> {
        return listOf(
            PluginUiButton(
                pluginKey = pluginKey,
                name = Res.string.pref_plugin_runcommand,
                iconRes = Res.drawable.code,
                category = ButtonCategory.CONTROL
            ) { _, navigator ->
                navigator.goTo(RunCommandKey(device.deviceId))
            })
    }
}
