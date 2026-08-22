package org.kde.kdeconnect.plugins.share

import android.Manifest
import android.app.Activity
import android.os.Build
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.ui.ShareHandler
import org.kde.kdeconnect.generated.resources.*

object SharePluginInfo : PluginInfo(
    pluginKey = "SharePlugin",
    instantiableClass = SharePlugin::class.java,
    displayNameRes = Res.string.pref_plugin_sharereceiver,
    descriptionRes = Res.string.pref_plugin_sharereceiver_desc,
    supportedPacketTypes = arrayOf("kdeconnect.share.request", "kdeconnect.share.request.update"),
    outgoingPacketTypes = arrayOf("kdeconnect.share.request"),
    requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        emptyArray()
    } else {
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    },
    lazy = false
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> {
        return listOf(
            PluginUiButton(
                pluginKey = pluginKey,
                name = Res.string.files,
                nameFull = Res.string.send_files,
                iconRes = Res.drawable.description,
                category = ButtonCategory.SEND
            ) { parentActivity: Activity, _ ->
                if (parentActivity is ShareHandler && parentActivity.shareGetResultCallback == null) {
                    device.getPlugin(SharePlugin::class.java)?.let {
                        parentActivity.shareGetResultCallback = { uris -> it.sendUriList(uris) }
                        parentActivity.launchSharePicker("*/*")
                    }
                }
            })
    }
}
