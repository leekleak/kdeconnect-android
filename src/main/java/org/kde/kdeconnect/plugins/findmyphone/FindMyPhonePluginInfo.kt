package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.R

object FindMyPhonePluginInfo : PluginInfo(
    instantiableClass = FindMyPhonePlugin::class.java,
    displayNameRes = R.string.findmydevice_title,
    descriptionRes = R.string.findmyphone_description,
    requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.USE_FULL_SCREEN_INTENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.SYSTEM_ALERT_WINDOW)
    }.toTypedArray(),
    supportedPacketTypes = arrayOf(FindMyPhonePlugin.PACKET_TYPE_FINDMYPHONE_REQUEST),
    outgoingPacketTypes = emptyArray(),
) {
    override val permissionExplanation: Int = R.string.findmyphone_notifications_explanation

    override fun checkRequiredPermissions(context: Context): Boolean {
        return super.checkRequiredPermissions(context) && Settings.canDrawOverlays(context)
    }

    override fun getPermissionExplanationDialog(context: Context): DialogFragment { //Todo: require draw overlays permission on app launch
        return StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_remotekeyboard) // Todo: Fix up the text
            .setMessage(R.string.no_permissions_remotekeyboard)
            .setPositiveButton(R.string.open_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()
    }
}
