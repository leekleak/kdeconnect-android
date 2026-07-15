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
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect_tp.R
import java.util.UUID

class DeviceHelper(val dataStore: SettingsDataStore) {
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

    fun getDeviceName(): String = dataStore.getDeviceNameBlocking()

    fun initializeDeviceId() {
        val deviceId = dataStore.getDeviceIdBlocking()
        if (DeviceInfo.isValidDeviceId(deviceId)) {
            return // We already have an ID
        }
        val deviceName = UUID.randomUUID().toString().replace("-", "")
        runBlocking {
            dataStore.setDeviceId(deviceName)
        }
    }

    fun getDeviceId(): String {
        return dataStore.getDeviceIdBlocking()
    }

    fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            getDeviceId(),
            SslHelper.certificate,
            getDeviceName(),
            deviceType,
            PROTOCOL_VERSION,
            PluginFactory.incomingCapabilities,
            PluginFactory.outgoingCapabilities
        )
    }

    fun getBatterySubtitle(context: Context, device: Device): String? {
        val batteryPlugin = device.getPlugin(BatteryPlugin::class.java)
        val info = batteryPlugin?.remoteBatteryInfo ?: return null

        @StringRes
        val resId = when {
            info.isCharging -> R.string.battery_status_charging_format
            BatteryPlugin.isLowBattery(info) -> R.string.battery_status_low_format
            else -> R.string.battery_status_format
        }

        return context.getString(resId, info.currentCharge)
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
