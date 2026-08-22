/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.loopback

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.helpers.DeviceHelper

class LoopbackLink(
    linkProvider: BaseLinkProvider,
    deviceHelper: DeviceHelper
) : BaseLink(linkProvider) {

    override val name: String = "LoopbackLink"
    override val deviceInfo: DeviceInfo = deviceHelper.getDeviceInfo()

    override suspend fun sendPacket(np: NetworkPacket, callback: Device.SendPacketStatusCallback): Boolean {
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
