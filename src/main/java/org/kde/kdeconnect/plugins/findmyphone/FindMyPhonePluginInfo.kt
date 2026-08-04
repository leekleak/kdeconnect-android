package org.kde.kdeconnect.plugins.findmyphone

import android.content.Context
import android.provider.Settings
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect_tp.R

object FindMyPhonePluginInfo : PluginInfo(
    instantiableClass = FindMyPhonePlugin::class.java,
    displayNameRes = R.string.findmydevice_title,
    descriptionRes = R.string.findmyphone_description,
    requiredPermissions = emptyArray(),
    supportedPacketTypes = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    outgoingPacketTypes = emptyArray(),
) {
    override fun checkRequiredPermissions(context: Context): Boolean {
        return super.checkRequiredPermissions(context) && Settings.canDrawOverlays(context)
    }
}
