package org.kde.kdeconnect.plugins.remotekeyboard

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin.Companion.PACKET_TYPE_MOUSEPAD_ECHO
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
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

    override fun getPermissionExplanationDialog(context: Context): DialogFragment {
        return StartActivityAlertDialogFragment.Builder()
            .setTitle(R.string.pref_plugin_remotekeyboard)
            .setMessage(R.string.no_permissions_remotekeyboard)
            .setPositiveButton(R.string.open_settings)
            .setNegativeButton(R.string.cancel)
            .setIntentAction(Settings.ACTION_INPUT_METHOD_SETTINGS)
            .setStartForResult(true)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()
    }
}
