/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins

import androidx.annotation.CallSuper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.kde.kdeconnect.NetworkPacket

abstract class Plugin {

    abstract val pluginInfo: PluginInfo

    val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // Todo: Make private

    /**
     * Return the internal plugin name, that will be used as a
     * unique key to distinguish it.
     * Use the class name as `key`.
     */
    val pluginKey: String get() = pluginInfo.pluginKey

    @get:CallSuper
    open val isCompatible: Boolean
        /**
         * Returns false when we should avoid loading this Plugin for [device].
         *
         * @return true if it's safe to call [onCreate]
         */
        get() = true

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

    companion object {
        @JvmStatic
        fun getPluginKey(p: Class<out Plugin>): String {
            return p.simpleName
        }
    }
}