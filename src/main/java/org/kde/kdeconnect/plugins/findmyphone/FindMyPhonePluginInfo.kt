package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.content.Context
import android.os.Build
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object FindMyPhonePluginInfo : PluginInfo(
    instantiableClass = FindMyPhonePlugin::class.java,
    displayNameRes = R.string.findmyphone_title,
    descriptionRes = R.string.findmyphone_description,
    supportedPacketTypes = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    outgoingPacketTypes = emptyArray(),
), KoinComponent {

    val deviceHelper: DeviceHelper by inject()

    override fun getDisplayName(context: Context): String {
        return when (deviceHelper.deviceType) {
            org.kde.kdeconnect.DeviceType.TV -> context.getString(R.string.findmyphone_title_tv)
            org.kde.kdeconnect.DeviceType.TABLET -> context.getString(R.string.findmyphone_title_tablet)
            else -> context.getString(R.string.findmyphone_title)
        }
    }

    override val permissionExplanation: Int = R.string.findmyphone_notifications_explanation

    override fun checkRequiredPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }
}
