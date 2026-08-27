package org.kde.kdeconnect.plugins.share

import android.Manifest
import android.app.Activity
import android.os.Build
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.description
import org.kde.kdeconnect.generated.resources.files
import org.kde.kdeconnect.generated.resources.pref_plugin_sharereceiver
import org.kde.kdeconnect.generated.resources.pref_plugin_sharereceiver_desc
import org.kde.kdeconnect.generated.resources.send_files
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.ui.ShareHandler

object SharePluginInfo : PermissionPluginInfo(
    pluginKey = "SharePlugin",
    instantiableClass = SharePlugin::class.java,
    displayNameRes = Res.string.pref_plugin_sharereceiver,
    descriptionRes = Res.string.pref_plugin_sharereceiver_desc,
    supportedPacketTypes = setOf("kdeconnect.share.request", "kdeconnect.share.request.update"),
    outgoingPacketTypes = setOf("kdeconnect.share.request"),
    requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setOf(Manifest.permission.POST_NOTIFICATIONS)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        emptySet()
    } else {
        setOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
