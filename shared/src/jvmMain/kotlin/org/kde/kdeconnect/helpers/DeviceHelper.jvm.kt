package org.kde.kdeconnect.helpers

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
    val sslHelper: SslHelper,
) {
    init {
        initializeDeviceId()
    }

    private fun initializeDeviceId() = runBlocking {
        val deviceId = dataStore.deviceId.first()
        if (DeviceInfo.isValidDeviceId(deviceId)) {
            return@runBlocking
        }
        val newId = UUID.randomUUID().toString().replace("-", "")
        dataStore.setDeviceId(newId)
    }

    actual val deviceType: DeviceType
        get() = DeviceType.DESKTOP

    actual suspend fun getDeviceId(): String = withContext(Dispatchers.IO) { dataStore.deviceId.first() }
    actual suspend fun getDeviceName(): String = withContext(Dispatchers.IO) { dataStore.deviceName.first() }

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
