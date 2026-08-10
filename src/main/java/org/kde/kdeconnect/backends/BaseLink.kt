/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends

import android.content.Context
import androidx.annotation.WorkerThread
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import java.io.IOException

abstract class BaseLink protected constructor(
    protected val context: Context,
    open val linkProvider: BaseLinkProvider
) {
    interface PacketReceiver {
        suspend fun onPacketReceived(np: NetworkPacket)
    }

    private val receivers = ArrayList<PacketReceiver>()

    /* To be implemented by each link for pairing handlers */
    abstract val name: String

    abstract val deviceInfo: DeviceInfo

    val deviceId: String
        get() = this.deviceInfo.id

    fun addPacketReceiver(pr: PacketReceiver) {
        receivers.add(pr)
    }

    fun removePacketReceiver(pr: PacketReceiver) {
        receivers.remove(pr)
    }

    //Should be called from a background thread listening for packets
    suspend fun packetReceived(np: NetworkPacket) {
        for (pr in receivers) {
            pr.onPacketReceived(np)
        }
    }

    open suspend fun disconnect() {
        linkProvider.onConnectionLost(this)
    }

    @WorkerThread
    @Throws(IOException::class)
    abstract suspend fun sendPacket(
        np: NetworkPacket,
        callback: Device.SendPacketStatusCallback,
    ): Boolean
}
