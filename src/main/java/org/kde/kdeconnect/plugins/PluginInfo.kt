package org.kde.kdeconnect.plugins

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionRequest
import org.kde.kdeconnect_tp.R

open class PluginInfo(
    val instantiableClass: Class<out Plugin>,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    val isEnabledByDefault: Boolean = true,
    val requiredPermissions: Array<String> = emptyArray(),
    supportedPacketTypes: Array<String> = emptyArray(),
    outgoingPacketTypes: Array<String> = emptyArray(),
) {
    val pluginKey: String = getPluginKey(instantiableClass)
    open fun getDisplayName(context: Context): String = context.getString(displayNameRes)
    open fun getDescription(context: Context): String = context.getString(descriptionRes)
    val supportedPacketTypes: Set<String> = supportedPacketTypes.toSet()
    val outgoingPacketTypes: Set<String> = outgoingPacketTypes.toSet()

    open fun getPermissionRequests(): List<PermissionRequest> {
        return requiredPermissions.map { permission ->
            PermissionRequest(
                title = displayNameRes,
                description = descriptionRes,
                intentAction = permission,
                positiveButton = R.string.grant
            )
        }
    }

    protected fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission -> isPermissionGranted(context, permission) }
    }


    open suspend fun checkRequiredPermissions(context: Context): Boolean {
        return arePermissionsGranted(context, requiredPermissions)
    }

    /**
     * Shows the permissionExplanationDialog if required permissions are not granted.
     */
    suspend fun showPermissionExplanation(context: Context, helper: PermissionRequestHelper) {
        if (!checkRequiredPermissions(context)) {
            if (helper.isExplanationShown(pluginKey)) return

            val intent = Intent(context, PermissionExplanationActivity::class.java).apply {
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
}