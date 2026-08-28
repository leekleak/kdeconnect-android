package org.kde.kdeconnect.helpers

import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType

const val PROTOCOL_VERSION = 8

expect class DeviceHelper {
    val deviceType: DeviceType
    suspend fun getDeviceInfo(): DeviceInfo
    suspend fun getDeviceId(): String
    suspend fun getDeviceName(): String
}

private val NAME_INVALID_CHARACTERS_REGEX = "[\"',;:.!?()\\[\\]<>]".toRegex()
const val MAX_DEVICE_NAME_LENGTH = 32

fun filterInvalidCharactersFromDeviceNameAndLimitLength(input: String): String =
    filterInvalidCharactersFromDeviceName(input).trim().take(MAX_DEVICE_NAME_LENGTH)

fun filterInvalidCharactersFromDeviceName(input: String): String =
    input.replace(NAME_INVALID_CHARACTERS_REGEX, "")
