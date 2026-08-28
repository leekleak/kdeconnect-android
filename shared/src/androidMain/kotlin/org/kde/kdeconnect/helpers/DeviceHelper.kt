/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.res.Configuration
import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.PluginFactory
import java.util.UUID

actual class DeviceHelper(
    val dataStore: SettingsDataStore,
    private val sslHelper: SslHelper
) {
    init {
        initializeDeviceId()
    }
    val isTablet: Boolean by lazy {
        val config = Resources.getSystem().configuration
        //This assumes that the values for the screen sizes are consecutive, so XXLARGE > XLARGE > LARGE
        ((config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE)
    }

    val isTv: Boolean by lazy {
        val uiMode = Resources.getSystem().configuration.uiMode
        (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    actual val deviceType: DeviceType by lazy {
        if (isTv) {
            DeviceType.TV
        } else if (isTablet) {
            DeviceType.TABLET
        } else {
            DeviceType.PHONE
        }
    }

    actual suspend fun getDeviceName(): String = withContext(Dispatchers.IO) { dataStore.deviceName.first() }

    private fun initializeDeviceId() = runBlocking {
        val deviceId = dataStore.deviceId.first()
        if (DeviceInfo.isValidDeviceId(deviceId)) {
            return@runBlocking // We already have an ID
        }
        val deviceName = UUID.randomUUID().toString().replace("-", "")
        dataStore.setDeviceId(deviceName)
    }

    actual suspend fun getDeviceId(): String = withContext(Dispatchers.IO) { dataStore.deviceId.first() }

    actual suspend fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            getDeviceId(),
            sslHelper.getCertificate().encoded,
            getDeviceName(),
            deviceType,
            PROTOCOL_VERSION,
            PluginFactory.incomingCapabilities,
            PluginFactory.outgoingCapabilities
        )
    }
}
