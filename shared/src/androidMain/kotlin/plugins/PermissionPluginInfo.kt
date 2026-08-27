package org.kde.kdeconnect.plugins

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.grant
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionRequest
import org.kde.kdeconnect.ui.navigation.Navigator

enum class ButtonCategory {
    SEND,
    CONTROL
}

data class PluginUiButton(
    val pluginKey: String,
    val name: StringResource,
    val nameFull: StringResource = name,
    val iconRes: DrawableResource,
    val category: ButtonCategory,
    val onClick: suspend (parentActivity: Activity, navigator: Navigator) -> Unit,
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
        result = 31 * result + name.hashCode()
        result = 31 * result + nameFull.hashCode()
        result = 31 * result + iconRes.hashCode()
        result = 31 * result + category.hashCode()
        return result
    }
}

open class PermissionPluginInfo(
    override val pluginKey: String,
    val instantiableClass: Class<out Plugin>,
    override val displayNameRes: StringResource,
    override val descriptionRes: StringResource,
    override val isEnabledByDefault: Boolean = true,
    val requiredPermissions: Set<String> = emptySet(),
    override val supportedPacketTypes: Set<String> = emptySet(),
    override val outgoingPacketTypes: Set<String> = emptySet(),
    override val lazy: Boolean
): PluginInfo {

    open suspend fun getPermissionRequests(): List<PermissionRequest> {
        return requiredPermissions.map { permission ->
            PermissionRequest(
                title = getString(displayNameRes),
                description = getString(descriptionRes),
                intentAction = permission,
                positiveButton = getString(Res.string.grant)
            )
        }
    }

    protected fun arePermissionsGranted(context: Context, permissions: Set<String>): Boolean {
        return permissions.all { permission -> isPermissionGranted(context, permission) }
    }

    open fun getUiButtons(device: Device): List<PluginUiButton> = listOf()

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
        fun isPermissionGranted(context: Context, permission: String): Boolean {
            val result = ContextCompat.checkSelfPermission(context, permission)
            return result == PackageManager.PERMISSION_GRANTED
        }
    }
}