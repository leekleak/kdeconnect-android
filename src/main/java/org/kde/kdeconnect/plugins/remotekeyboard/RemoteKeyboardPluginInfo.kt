package org.kde.kdeconnect.plugins.remotekeyboard

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin.Companion.PACKET_TYPE_MOUSEPAD_ECHO
import org.kde.kdeconnect.ui.PermissionRequest
import org.kde.kdeconnect_tp.R


object RemoteKeyboardPluginInfo : PluginInfo(
    instantiableClass = RemoteKeyboardPlugin::class.java,
    displayNameRes = R.string.pref_plugin_remotekeyboard,
    descriptionRes = R.string.pref_plugin_remotekeyboard_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_ECHO, PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)
) {
    override fun checkRequiredPermissions(context: Context): Boolean {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethodList = inputMethodManager.enabledInputMethodList
        return inputMethodList.stream().anyMatch { info -> context.packageName.equals(info.packageName) }
    }

    override fun getPermissionRequests(): List<PermissionRequest> {
        return listOf(
            PermissionRequest(
                title = R.string.pref_plugin_remotekeyboard,
                description = R.string.no_permissions_remotekeyboard,
                intentAction = Settings.ACTION_INPUT_METHOD_SETTINGS,
                positiveButton = R.string.open_settings
            )
        )
    }
}
