package org.kde.kdeconnect

import org.kde.kdeconnect.device.DeviceType

fun DeviceType.toShortcutIconRes(): Int = when (this) {
    DeviceType.PHONE -> R.drawable.ic_device_phone_shortcut
    DeviceType.TABLET -> R.drawable.ic_device_tablet_shortcut
    DeviceType.TV -> R.drawable.ic_device_tv_shortcut
    DeviceType.LAPTOP -> R.drawable.ic_device_laptop_shortcut
    else -> R.drawable.ic_device_desktop_shortcut
}
