package org.kde.kdeconnect.helpers

import kotlinx.coroutines.flow.first
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType

actual class DeviceHelper(
    val dataStore: SettingsDataStore,
) {
    actual val deviceType: DeviceType
        get() = DeviceType.DESKTOP

    actual suspend fun getDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            id = dataStore.deviceId.first(),
            certificate = ByteArray(0),
            name = dataStore.deviceName.first(),
            type = deviceType
        )
    }
}