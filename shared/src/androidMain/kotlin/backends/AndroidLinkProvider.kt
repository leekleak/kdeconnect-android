package org.kde.kdeconnect.backends

import android.net.Network

interface AndroidLinkProvider {
    suspend fun onNetworkChange(network: Network?)
}