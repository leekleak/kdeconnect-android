package org.kde.kdeconnect

import org.jetbrains.compose.resources.*
import org.kde.kdeconnect.generated.resources.*

enum class DeviceType {
    PHONE, TABLET, DESKTOP, LAPTOP, TV;

    override fun toString() =
        when (this) {
            TABLET -> "tablet"
            PHONE -> "phone"
            TV -> "tv"
            LAPTOP -> "laptop"
            else -> "desktop"
        }

    fun toDrawableRes(): DrawableResource =
        when (this) {
            DeviceType.PHONE -> Res.drawable.mobile
            DeviceType.TABLET -> Res.drawable.tablet
            DeviceType.TV -> Res.drawable.tv
            DeviceType.LAPTOP -> Res.drawable.laptop_windows
            else -> Res.drawable.desktop_windows
        }

    fun toShortcutDrawableRes(): DrawableResource =
        when (this) {
            DeviceType.PHONE -> Res.drawable.ic_device_phone_shortcut
            DeviceType.TABLET -> Res.drawable.ic_device_tablet_shortcut
            DeviceType.TV -> Res.drawable.ic_device_tv_shortcut
            DeviceType.LAPTOP -> Res.drawable.ic_device_laptop_shortcut
            else -> Res.drawable.ic_device_desktop_shortcut
        }

    companion object {
        fun fromString(s: String) =
            when (s) {
                "phone" -> PHONE
                "tablet" -> TABLET
                "tv" -> TV
                "laptop" -> LAPTOP
                else -> DESKTOP
            }
    }
}
