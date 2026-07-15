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

abstract class Plugin(
    @JvmField protected val context: Context,
    @JvmField protected val device: Device
) {

    abstract val pluginInfo: PluginInfo
    protected val isDeviceInitialized: Boolean
        get() = device != null

    val preferences: SharedPreferences? by lazy {
        context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
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
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it.
     * Use the class name as `key`.
     */
    val pluginKey: String = getPluginKey(this.javaClass)
    val deviceId: String = device.deviceId

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

    open fun onDeviceUnpaired(context: Context, deviceId: String) {}

    open val minSdk: Int = Build.VERSION_CODES.BASE

    companion object {
        @JvmStatic
        fun getPluginKey(p: Class<out Plugin>): String {
            return p.simpleName
        }
    }
}
