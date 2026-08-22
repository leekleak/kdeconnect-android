/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.kde.kdeconnect.R
import java.util.UUID

class DeviceHelper(
    val dataStore: SettingsDataStore,
    private val sslHelper: SslHelper
) {
    val isTablet: Boolean by lazy {
        val config = Resources.getSystem().configuration
        //This assumes that the values for the screen sizes are consecutive, so XXLARGE > XLARGE > LARGE
        ((config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
    }

    val isTv: Boolean by lazy {
        val uiMode = Resources.getSystem().configuration.uiMode
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    val deviceType: DeviceType by lazy {
        if (isTv) {
            DeviceType.TV
        } else if (isTablet) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }
    }

    suspend fun getDeviceName(): String = dataStore.deviceName.first()

    suspend fun initializeDeviceId() = withContext(Dispatchers.IO) {
        val deviceId = dataStore.deviceId.first()
        if (DeviceInfo.isValidDeviceId(deviceId)) {
            return@withContext // We already have an ID
        }
        val deviceName = UUID.randomUUID().toString().replace("-", "")
        dataStore.setDeviceId(deviceName)
    }

    fun getDeviceId(): String = runBlocking(Dispatchers.IO) { dataStore.deviceId.first() }

    fun getDeviceInfo(): DeviceInfo = runBlocking(Dispatchers.IO) {
        return@runBlocking DeviceInfo(
            getDeviceId(),
            sslHelper.certificate.encoded,
            getDeviceName(),
            deviceType,
            PROTOCOL_VERSION,
            PluginFactory.incomingCapabilities,
            PluginFactory.outgoingCapabilities
        )
    }

    companion object {
        const val PROTOCOL_VERSION = 8

        private val NAME_INVALID_CHARACTERS_REGEX = "[\"',;:.!?()\\[\\]<>]".toRegex()
        const val MAX_DEVICE_NAME_LENGTH = 32

        @JvmStatic
        fun filterInvalidCharactersFromDeviceNameAndLimitLength(input: String): String =
            filterInvalidCharactersFromDeviceName(input).trim().take(MAX_DEVICE_NAME_LENGTH)

        @JvmStatic
        fun filterInvalidCharactersFromDeviceName(input: String): String =
            input.replace(NAME_INVALID_CHARACTERS_REGEX, "")
    }
}
