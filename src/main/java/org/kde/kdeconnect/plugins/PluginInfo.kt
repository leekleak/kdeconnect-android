package org.kde.kdeconnect.plugins

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.ui.AlertDialogFragment
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect_tp.R

open class PluginInfo(
    val instantiableClass: Class<out Plugin>,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    val isEnabledByDefault: Boolean = true,
    val requiredPermissions: Array<String> = emptyArray(),
    val optionalPermissions: Array<String> = emptyArray(),
    supportedPacketTypes: Array<String> = emptyArray(),
    outgoingPacketTypes: Array<String> = emptyArray(),
) {
    val pluginKey: String = getPluginKey(instantiableClass)
    open fun getDisplayName(context: Context): String = context.getString(displayNameRes)
    open fun getDescription(context: Context): String = context.getString(descriptionRes)
    val supportedPacketTypes: Set<String> = supportedPacketTypes.toSet()
    val outgoingPacketTypes: Set<String> = outgoingPacketTypes.toSet()

    private fun requestPermissionDialog(
        context: Context,
        permissions: Array<String>,
        @StringRes reason: Int
    ): PermissionsAlertDialogFragment {
        return PermissionsAlertDialogFragment.Builder()
            .setTitle(getDisplayName(context))
            .setMessage(reason)
            .setPermissions(permissions)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()
    }

    open fun getPermissionExplanationDialog(context: Context): DialogFragment
            = requestPermissionDialog(context, requiredPermissions, permissionExplanation)

    open fun getOptionalPermissionExplanationDialog(context: Context): AlertDialogFragment
            = requestPermissionDialog(context, optionalPermissions, optionalPermissionExplanation)


    /**
     * Returns the string to display before asking for the required permissions for the plugin.
     */
    @get:StringRes
    protected open val permissionExplanation: Int = R.string.permission_explanation

    /**
     * Returns the string to display before asking for the optional permissions for the plugin.
     */
    @get:StringRes
    protected open val optionalPermissionExplanation: Int = R.string.optional_permission_explanation

    protected fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission -> isPermissionGranted(context, permission) }
    }


    open fun checkRequiredPermissions(context: Context): Boolean {
        return arePermissionsGranted(context, requiredPermissions)
    }

    open fun checkOptionalPermissions(context: Context): Boolean {
        return arePermissionsGranted(context, optionalPermissions)
    }

    /**
     * Shows the permissionExplanationDialog if required permissions are not granted.
     */
    fun showPermissionExplanation(context: Context, deviceId: String) {
        if (!checkRequiredPermissions(context)) {
            val intent = Intent(context, PermissionExplanationActivity::class.java).apply {
                putExtra("deviceId", deviceId)
                putExtra("pluginKey", pluginKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    companion object {
        // Permission from Manifest.permission.*
        @JvmStatic
        fun isPermissionGranted(context: Context, permission: String): Boolean {
            val result = ContextCompat.checkSelfPermission(context, permission)
            return result == PackageManager.PERMISSION_GRANTED
        }
    }

    open fun checkRequiredPermissions(preferences: SharedPreferences, context: Context): Boolean {
        return checkRequiredPermissions(context)
    }

    open fun getPermissionExplanationDialog(
        preferences: SharedPreferences,
        context: Context,
        device: Device
    ): DialogFragment {
        return getPermissionExplanationDialog(context)
    }
}