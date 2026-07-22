package org.kde.kdeconnect.plugins.share

import android.Manifest
import android.os.Build
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

object SharePluginInfo : PluginInfo(
    instantiableClass = SharePlugin::class.java,
    displayNameRes = R.string.pref_plugin_sharereceiver,
    descriptionRes = R.string.pref_plugin_sharereceiver_desc,
    supportedPacketTypes = arrayOf("kdeconnect.share.request", "kdeconnect.share.request.update"),
    outgoingPacketTypes = arrayOf("kdeconnect.share.request"),
    requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        emptyArray()
    } else {
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
) {
    override val optionalPermissionExplanation: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            R.string.share_notifications_explanation
        } else {
            R.string.share_optional_permission_explanation
        }
}
