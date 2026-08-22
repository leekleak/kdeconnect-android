/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update

@OptIn(ExperimentalAtomicApi::class)
abstract class BaseLink protected constructor(
    open val linkProvider: BaseLinkProvider
) {
    interface PacketReceiver {
        suspend fun onPacketReceived(np: NetworkPacket)
    }

    private val receivers = AtomicReference<List<PacketReceiver>>(emptyList())

    /* To be implemented by each link for pairing handlers */
    abstract val name: String

    abstract val deviceInfo: DeviceInfo

    val deviceId: String
        get() = this.deviceInfo.id

    fun addPacketReceiver(pr: PacketReceiver) {
        receivers.update { it + pr }
    }

    fun removePacketReceiver(pr: PacketReceiver) {
        receivers.update { it - pr }
    }

    //Should be called from a background thread listening for packets
    suspend fun packetReceived(np: NetworkPacket) {
        for (pr in receivers.load()) {
            pr.onPacketReceived(np)
        }
    }

    open suspend fun disconnect() {
        linkProvider.onConnectionLost(this)
    }

    abstract suspend fun sendPacket(
        np: NetworkPacket,
        callback: Device.SendPacketStatusCallback,
    ): Boolean
}
