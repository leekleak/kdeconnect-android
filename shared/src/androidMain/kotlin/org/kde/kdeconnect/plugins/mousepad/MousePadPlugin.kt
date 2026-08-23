/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mousepad

import android.content.Context
import android.view.KeyEvent
import kotlinx.serialization.json.put
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.open_mousepad
import org.kde.kdeconnect.generated.resources.open_mousepad_tv
import org.kde.kdeconnect.generated.resources.pref_plugin_mousepad
import org.kde.kdeconnect.generated.resources.pref_plugin_mousepad_desc_nontv
import org.kde.kdeconnect.generated.resources.trackpad_input_2
import org.kde.kdeconnect.generated.resources.tv_remote
import org.kde.kdeconnect.helpers.SPECIAL_KEY_ENCODING_MAP
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.ui.navigation.BigscreenKey
import org.kde.kdeconnect.ui.navigation.MousePadKey

class MousePadPlugin(
    context: Context,
    device: Device,
) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = MousePadPluginSettings

    var isKeyboardEnabled: Boolean = true
        private set

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        this.isKeyboardEnabled = np.getBoolean("state", true)
        return true
    }

    suspend fun sendMouseDelta(dx: Float, dy: Float) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("dx", dx.toDouble())
            put("dy", dy.toDouble())
        }
        sendPacket(np)
    }

    suspend fun sendLeftClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("singleclick", true)
        }
        sendPacket(np)
    }

    suspend fun sendDoubleClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("doubleclick", true)
        }
        sendPacket(np)
    }

    suspend fun sendMiddleClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("middleclick", true)
        }
        sendPacket(np)
    }

    suspend fun sendRightClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("rightclick", true)
        }
        sendPacket(np)
    }

    suspend fun sendSingleHold() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("singlehold", true)
        }
        sendPacket(np)
    }

    suspend fun sendSingleRelease() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("singlerelease", true)
        }
        sendPacket(np)
    }

    suspend fun sendScroll(dx: Double, dy: Double) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("scroll", true)
            put("dx", dx)
            put("dy", dy)
        }
        sendPacket(np)
    }

    suspend fun sendLeft() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_LEFT])
        }
        sendPacket(np)
    }

    suspend fun sendRight() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_RIGHT])
        }
        sendPacket(np)
    }

    suspend fun sendUp() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_UP])
        }
        sendPacket(np)
    }

    suspend fun sendDown() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_DOWN])
        }
        sendPacket(np)
    }

    suspend fun sendSelect() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_ENTER])
        }
        sendPacket(np)
    }

    suspend fun sendHome() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("alt", true)
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_F4])
        }
        device.sendPacket(np)
    }

    suspend fun sendBack() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("specialKey", SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_ESCAPE])
        }
        device.sendPacket(np)
    }

    suspend fun sendText(content: String) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST).update {
            put("key", content)
        }
        sendPacket(np)
    }

    suspend fun sendPacket(np: NetworkPacket) {
        device.sendPacket(np)
    }

    companion object {
        internal const val PACKET_TYPE_MOUSEPAD_REQUEST: String = "kdeconnect.mousepad.request"
        internal const val PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate"
    }
}


object MousePadPluginSettings: PluginInfo(
    pluginKey = "MousePadPlugin",
    instantiableClass = MousePadPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_mousepad,
    descriptionRes = Res.string.pref_plugin_mousepad_desc_nontv,
    supportedPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST),
    lazy = true
) {
    override fun getUiButtons(device: Device): List<PluginUiButton> {
        val mouseAndKeyboardInput = PluginUiButton(
            pluginKey = pluginKey,
            name = Res.string.open_mousepad,
            iconRes = Res.drawable.trackpad_input_2,
            category = ButtonCategory.CONTROL
        ) { _, navigator ->
            navigator.goTo(MousePadKey(device.deviceId))
        }
        return if (device.deviceType == DeviceType.TV) {
            val tvInput = PluginUiButton(
                pluginKey = pluginKey,
                name = Res.string.open_mousepad_tv,
                iconRes = Res.drawable.tv_remote,
                category = ButtonCategory.CONTROL
            ) { _, navigator ->
                navigator.goTo(BigscreenKey(device.deviceId))
            }
            listOf(mouseAndKeyboardInput, tvInput)
        } else {
            listOf(mouseAndKeyboardInput)
        }
    }
}
