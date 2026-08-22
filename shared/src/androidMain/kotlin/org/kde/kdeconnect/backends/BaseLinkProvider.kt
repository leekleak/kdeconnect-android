/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends

import android.net.Network
import androidx.annotation.WorkerThread
import org.jetbrains.compose.resources.DrawableResource
import org.kde.kdeconnect.DeviceInfo
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

@OptIn(ExperimentalAtomicApi::class)
abstract class BaseLinkProvider {
    interface ConnectionReceiver {
        @WorkerThread
        fun onConnectionReceived(link: BaseLink)

        @WorkerThread
        fun onDeviceInfoUpdated(deviceInfo: DeviceInfo)

        @WorkerThread
        fun onConnectionLost(link: BaseLink)
    }

    private val connectionReceivers = AtomicReference<List<ConnectionReceiver>>(emptyList())

    fun addConnectionReceiver(cr: ConnectionReceiver) {
        connectionReceivers.update { it + cr }
    }

    fun removeConnectionReceiver(cr: ConnectionReceiver) {
        connectionReceivers.update { it - cr }
    }

    /**
     * To be called from the child classes when a link to a new device is established
     */
    @WorkerThread
    protected fun onConnectionReceived(link: BaseLink) {
        for (cr in connectionReceivers.load()) {
            cr.onConnectionReceived(link)
        }
    }

    /**
     * To be called from the child classes when a link to an existing device is disconnected
     */
    @WorkerThread
    open fun onConnectionLost(link: BaseLink) {
        for (cr in connectionReceivers.load()) {
            cr.onConnectionLost(link)
        }
    }

    /**
     * To be called from the child classes when we discover new DeviceInfo for an already linked device.
     */
    @WorkerThread
    protected fun onDeviceInfoUpdated(deviceInfo: DeviceInfo) {
        for (cr in connectionReceivers.load()) {
            cr.onDeviceInfoUpdated(deviceInfo)
        }
    }

    abstract suspend fun onStart()
    abstract fun onStop()
    abstract suspend fun onNetworkChange(network: Network?)
    abstract val name: String
    abstract val icon: DrawableResource

    abstract val priority: Int
}
