package org.kde.kdeconnect.helpers

import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType

expect class DeviceHelper {
    val deviceType: DeviceType
    suspend fun getDeviceInfo(): DeviceInfo
}