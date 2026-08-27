package org.kde.kdeconnect.plugins.remotekeyboard

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.no_permissions_remotekeyboard
import org.kde.kdeconnect.generated.resources.open_settings
import org.kde.kdeconnect.generated.resources.pref_plugin_remotekeyboard
import org.kde.kdeconnect.generated.resources.pref_plugin_remotekeyboard_desc
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin.Companion.PACKET_TYPE_MOUSEPAD_ECHO
import org.kde.kdeconnect.ui.PermissionRequest


object RemoteKeyboardPluginInfo : PermissionPluginInfo(
    pluginKey = "RemoteKeyboardPlugin",
    instantiableClass = RemoteKeyboardPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_remotekeyboard,
    descriptionRes = Res.string.pref_plugin_remotekeyboard_desc,
    supportedPacketTypes = setOf(PACKET_TYPE_MOUSEPAD_REQUEST),
    outgoingPacketTypes = setOf(PACKET_TYPE_MOUSEPAD_ECHO, PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE),
    lazy = true
) {
    override suspend fun checkRequiredPermissions(context: Context): Boolean {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val inputMethodList = inputMethodManager.enabledInputMethodList
        return inputMethodList.stream().anyMatch { info -> context.packageName.equals(info.packageName) }
    }

    override suspend fun getPermissionRequests(): List<PermissionRequest> {
        return listOf(
            PermissionRequest(
                title = getString(Res.string.pref_plugin_remotekeyboard),
                description = getString(Res.string.no_permissions_remotekeyboard),
                intentAction = Settings.ACTION_INPUT_METHOD_SETTINGS,
                positiveButton = getString(Res.string.open_settings)
            )
        )
    }
}
