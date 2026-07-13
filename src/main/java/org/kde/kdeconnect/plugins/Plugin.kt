/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.ui.AlertDialogFragment
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect_tp.R

abstract class Plugin {
    protected lateinit var device: Device
    protected lateinit var context: Context

    protected val isDeviceInitialized: Boolean
        get() = ::device.isInitialized

    var preferences: SharedPreferences? = null
        protected set

    fun setContext(context: Context, device: Device?) {
        this.context = context

        if (device != null) {
            this.device = device
        }
        this.preferences =
            this.context.getSharedPreferences(this.sharedPreferencesName, Context.MODE_PRIVATE)
    }

    enum class ButtonCategory {
        SEND,
        CONTROL
    }

    data class PluginUiButton @JvmOverloads constructor(
        val name: String,
        @get:DrawableRes val iconRes: Int,
        val category: ButtonCategory = ButtonCategory.CONTROL,
        val onClick: (parentActivity: Activity) -> Unit,
    )
    data class PluginUiMenuEntry(val name: String, val onClick: (parentActivity: Activity) -> Unit)

    /**
     * Return entries to display as buttons in the Device main view
     */
    open fun getUiButtons(): List<PluginUiButton> = listOf()

    val sharedPreferencesName: String
        get() = pluginKey + "_preferences"

    /**
     * To receive the network packet from the unpaired device, override
     * listensToUnpairedDevices to return true and this method.
     */
    open fun onUnpairedDevicePacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    /**
     * Returns whether this plugin should be loaded or not, to listen to NetworkPackets
     * from the unpaired devices. By default, returns false.
     */
    open fun listensToUnpairedDevices(): Boolean {
        return false
    }

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it.
     * Use the class name as `key`.
     */
    val pluginKey: String = getPluginKey(this.javaClass)

    /**
     * Return the human-readable plugin name. This function can
     * access this.context to provide translated text.
     */
    abstract val displayName: String

    /**
     * Return the human-readable description of this plugin. This
     * function can access this.context to provide translated text.
     */
    abstract val description: String

    /**
     * Return true if this plugin should be enabled on new devices.
     * This function can access this.context and perform compatibility
     * checks with the Android version, but cannot access this.device.
     */
    open val isEnabledByDefault: Boolean = true

    @get:CallSuper
    open val isCompatible: Boolean
        /**
         * Returns false when we should avoid loading this Plugin for [device].
         *
         * Called after [setContext] but before [onCreate].
         *
         * By default, this just checks if [minSdk] is smaller or equal than the
         * [SDK version][Build.VERSION.SDK_INT] of this Android device.
         *
         * @return true if it's safe to call [onCreate]
         */
        get() = Build.VERSION.SDK_INT >= minSdk

    /**
     * Initialize the listeners and structures in your plugin.
     *
     * If [isCompatible] or [checkRequiredPermissions] returns false, this
     * will *not* be called.
     *
     * @return true if initialization was successful, false otherwise
     */
    open fun onCreate(): Boolean {
        return true
    }

    /**
     * Finish any ongoing operations, remove listeners... so
     * this object could be garbage collected. Note that this gets
     * called as well if onCreate threw an exception, so your plugin
     * could be not fully initialized.
     */
    open fun onDestroy() {}

    /**
     * Called when a plugin receives a packet.
     * By convention, we return true when we have done something in response to the packet or false otherwise,
     * even though that value is unused as of now.
     */
    open fun onPacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    /**
     * Should return the list of NetworkPacket types that this plugin can handle
     */
    abstract val supportedPacketTypes: Array<String>

    /**
     * Should return the list of NetworkPacket types that this plugin can send
     */
    abstract val outgoingPacketTypes: Array<String>

    protected open val requiredPermissions: Array<String>
        /**
         * Should return the list of permissions from Manifest.permission.* that, if not present,
         * mean the plugin can't be loaded.
         */
        get() = emptyArray()

    protected open val optionalPermissions: Array<String>
        /**
         * Should return the list of permissions from Manifest.permission.* that enable additional
         * functionality in the plugin (without preventing the plugin to load).
         */
        get() = emptyArray()

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

    // Permission from Manifest.permission.*
    protected fun isPermissionGranted(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(context, permission)
        return result == PackageManager.PERMISSION_GRANTED
    }

    protected fun arePermissionsGranted(permissions: Array<String>): Boolean {
        return permissions.all(::isPermissionGranted)
    }

    private fun requestPermissionDialog(
        permissions: Array<String>,
        @StringRes reason: Int
    ): PermissionsAlertDialogFragment {
        return PermissionsAlertDialogFragment.Builder()
            .setTitle(displayName)
            .setMessage(reason)
            .setPermissions(permissions)
            .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
            .create()
    }

    /**
     * If onCreate returns false, should create a dialog explaining
     * the problem (and how to fix it, if possible) to the user.
     */
    open val permissionExplanationDialog: DialogFragment
        get() = requestPermissionDialog(requiredPermissions, permissionExplanation)

    open val optionalPermissionExplanationDialog: AlertDialogFragment
        get() = requestPermissionDialog(optionalPermissions, optionalPermissionExplanation)

    open fun checkRequiredPermissions(): Boolean {
        return arePermissionsGranted(requiredPermissions)
    }

    open fun checkOptionalPermissions(): Boolean {
        return arePermissionsGranted(optionalPermissions)
    }

    /**
     * Shows the permissionExplanationDialog if required permissions are not granted.
     */
    fun showPermissionExplanation(deviceId: String) {
        if (!checkRequiredPermissions()) {
            val intent = Intent(context, org.kde.kdeconnect.ui.PermissionExplanationActivity::class.java).apply {
                putExtra("deviceId", deviceId)
                putExtra("pluginKey", pluginKey)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    open fun loadPluginWhenRequiredPermissionsMissing(): Boolean = false

    open fun onDeviceUnpaired(context: Context, deviceId: String) {}

    open val minSdk: Int = Build.VERSION_CODES.BASE

    companion object {
        @JvmStatic
        fun getPluginKey(p: Class<out Plugin>): String {
            return p.simpleName
        }
    }
}
