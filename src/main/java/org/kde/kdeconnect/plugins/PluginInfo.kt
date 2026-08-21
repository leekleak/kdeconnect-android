package org.kde.kdeconnect.plugins

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionRequest
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R

enum class ButtonCategory {
    SEND,
    CONTROL
}

data class PluginUiButton(
    val pluginKey: String,
    @get:StringRes val name: Int,
    @get:StringRes val nameFull: Int = name,
    @get:DrawableRes val iconRes: Int,
    val category: ButtonCategory,
    val onClick: (parentActivity: Activity, navigator: Navigator) -> Unit,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginUiButton) return false

        if (pluginKey != other.pluginKey) return false
        if (name != other.name) return false
        if (nameFull != other.nameFull) return false
        if (iconRes != other.iconRes) return false
        if (category != other.category) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pluginKey.hashCode()
        result = 31 * result + name
        result = 31 * result + nameFull
        result = 31 * result + iconRes
        result = 31 * result + category.hashCode()
        return result
    }
}

open class PluginInfo(
    val pluginKey: String,
    val instantiableClass: Class<out Plugin>,
    @StringRes val displayNameRes: Int,
    @StringRes val descriptionRes: Int,
    val isEnabledByDefault: Boolean = true,
    val requiredPermissions: Array<String> = emptyArray(),
    supportedPacketTypes: Array<String> = emptyArray(),
    outgoingPacketTypes: Array<String> = emptyArray(),
    val lazy: Boolean // If lazy, plugin should be instanced on use only.
) {
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

    /**
     * Return entries to display as buttons in the Device main view
     */
    open fun getUiButtons(device: Device): List<PluginUiButton> = listOf()

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