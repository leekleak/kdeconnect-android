/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.loopback

import android.content.Context
import android.net.Network
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.helpers.DeviceHelper

class LoopbackLinkProvider(
    private val context: Context,
    private val deviceHelper: DeviceHelper
) : BaseLinkProvider() {

    override fun getName(): String = "LoopbackLinkProvider"
    override fun getPriority(): Int = 0

    override fun onStart() {
        onNetworkChange(null)
    }

    override fun onStop() { }

    override fun onNetworkChange(network: Network?) {
        val link = LoopbackLink(context, this, deviceHelper)
        onConnectionReceived(link)
    }
}
