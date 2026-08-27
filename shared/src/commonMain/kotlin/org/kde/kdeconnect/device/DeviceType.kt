package org.kde.kdeconnect.device

import org.jetbrains.compose.resources.DrawableResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.desktop_windows
import org.kde.kdeconnect.generated.resources.ic_device_desktop_shortcut
import org.kde.kdeconnect.generated.resources.ic_device_laptop_shortcut
import org.kde.kdeconnect.generated.resources.ic_device_phone_shortcut
import org.kde.kdeconnect.generated.resources.ic_device_tablet_shortcut
import org.kde.kdeconnect.generated.resources.ic_device_tv_shortcut
import org.kde.kdeconnect.generated.resources.laptop_windows
import org.kde.kdeconnect.generated.resources.mobile
import org.kde.kdeconnect.generated.resources.tablet
import org.kde.kdeconnect.generated.resources.tv

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
            PHONE -> Res.drawable.mobile
            TABLET -> Res.drawable.tablet
            TV -> Res.drawable.tv
            LAPTOP -> Res.drawable.laptop_windows
            else -> Res.drawable.desktop_windows
        }

    fun toShortcutDrawableRes(): DrawableResource =
        when (this) {
            PHONE -> Res.drawable.ic_device_phone_shortcut
            TABLET -> Res.drawable.ic_device_tablet_shortcut
            TV -> Res.drawable.ic_device_tv_shortcut
            LAPTOP -> Res.drawable.ic_device_laptop_shortcut
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
