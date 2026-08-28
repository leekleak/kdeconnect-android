package org.kde.kdeconnect.helpers

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType

actual class DeviceHelper(
    val dataStore: SettingsDataStore,
) {
    actual val deviceType: DeviceType
        get() = DeviceType.DESKTOP

    actual fun getDeviceId(): String = runBlocking { dataStore.deviceId.first() }

    actual suspend fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            id = getDeviceId(),
            certificate = ByteArray(0),
            name = dataStore.deviceName.first(),
            type = deviceType,
            protocolVersion = PROTOCOL_VERSION
        )
    }
}
