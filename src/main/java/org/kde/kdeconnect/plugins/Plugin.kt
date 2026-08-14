/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin.ButtonCategory

abstract class Plugin(
    @JvmField protected val context: Context,
    @JvmField protected val device: Device
) {

    abstract val pluginInfo: PluginInfo

    enum class ButtonCategory {
        SEND,
        CONTROL
    }

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // Todo: Make private

    /**
     * Return entries to display as buttons in the Device main view
     */
    open fun getUiButtons(): List<PluginUiButton> = listOf()

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
    val pluginKey: String get() = pluginInfo.pluginKey
    val deviceId: String get() = device.deviceId

    @get:CallSuper
    open val isCompatible: Boolean
        /**
         * Returns false when we should avoid loading this Plugin for [device].
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
     * If not [isCompatible] or permissions are missing, this
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
    @CallSuper
    open fun onDestroy() {
        coroutineScope.cancel()
    }

    /**
     * Called when a plugin receives a packet.
     * By convention, we return true when we have done something in response to the packet or false otherwise,
     * even though that value is unused as of now.
     */
    open suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        return false
    }

    open val minSdk: Int = Build.VERSION_CODES.BASE

    companion object {
        @JvmStatic
        fun getPluginKey(p: Class<out Plugin>): String {
            return p.simpleName
        }
    }
}

data class PluginUiButton(
    val pluginKey: String,
    val name: String,
    val nameFull: String = name,
    @get:DrawableRes val iconRes: Int,
    val category: ButtonCategory,
    val onClick: (parentActivity: Activity) -> Unit,
)
