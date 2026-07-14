/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class TrustedNetworkHelper(private val context: Context) : KoinComponent {

    private val dataStore: ConnectionsSettingsDataStore by inject()

    var trustedNetworks: List<String>
        get() {
            val serializedNetworks = dataStore.getTrustedNetworksRawBlocking()
            return serializedNetworks.split(NETWORK_SSID_DELIMITER).filter { it.isNotEmpty() }
        }
        set(value) {
            runBlocking {
                dataStore.setTrustedNetworksRaw(
                    value.joinToString(NETWORK_SSID_DELIMITER) { it.cleanSsid() }
                )
            }
        }

    var allNetworksAllowed: Boolean
        get() = !hasPermissions || dataStore.areAllNetworksAllowedBlocking()
        set(value) {
            runBlocking {
                dataStore.setAllNetworksAllowed(value)
            }
        }

    val hasPermissions: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** @return The current SSID or null if it's not available for any reason */
    val currentSSID: String?
        get() {
            val wifiManager = ContextCompat.getSystemService(context.applicationContext, WifiManager::class.java) ?: return null
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.supplicantState != SupplicantState.COMPLETED) return null
            val ssid = wifiInfo.ssid
            return when {
                ssid == null -> null
                ssid.equals(NOT_AVAILABLE_SSID_RESULT, ignoreCase = true) -> {
                    Log.d("TrustedNetworkHelper", "Current SSID is unknown")
                    null
                }
                ssid.isBlank() -> null
                else -> ssid.cleanSsid()
            }
        }

    val isTrustedNetwork: Boolean
        get() = this.allNetworksAllowed || this.currentSSID in this.trustedNetworks

    private fun String.cleanSsid(): String {
        return if (startsWith("\"") && endsWith("\"") && length >= 2) {
            substring(1, length - 1)
        } else {
            this
        }
    }

    companion object {
        private const val NETWORK_SSID_DELIMITER = "\u0000"
        private const val NOT_AVAILABLE_SSID_RESULT = "<unknown ssid>"

        @JvmStatic
        fun isTrustedNetwork(context: Context): Boolean = TrustedNetworkHelper(context).isTrustedNetwork
    }
}
