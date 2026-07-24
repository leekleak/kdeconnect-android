/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.loopback

import android.content.Context
import androidx.annotation.WorkerThread
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.NetworkPacket
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LoopbackLink(context: Context, linkProvider: BaseLinkProvider) : BaseLink(context, linkProvider), KoinComponent {
    private val deviceHelper: DeviceHelper by inject()

    override val name: String = "LoopbackLink"
    override val deviceInfo: DeviceInfo = deviceHelper.getDeviceInfo()

    @WorkerThread
    override fun sendPacket(np: NetworkPacket, callback: Device.SendPacketStatusCallback, sendPayloadFromSameThread: Boolean): Boolean {
        packetReceived(np)
        if (np.hasPayload()) {
            callback.onPayloadProgressChanged(0)
            np.payload = np.payload // this triggers logic in the setter
            callback.onPayloadProgressChanged(100)
        }
        callback.onSuccess()
        return true
    }
}
