/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mousepad

import android.content.Context
import android.view.KeyEvent
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.SPECIAL_KEY_ENCODING_MAP
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.navigation.BigscreenKey
import org.kde.kdeconnect.ui.navigation.MousePadKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R

class MousePadPlugin(
    context: Context,
    device: Device,
) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = MousePadPluginSettings

    override fun getUiButtons(): List<PluginUiButton> {
        val mouseAndKeyboardInput = PluginUiButton(
            pluginKey = pluginKey,
            name = context.getString(R.string.open_mousepad),
            iconRes = R.drawable.trackpad_input_2,
            category = ButtonCategory.CONTROL
        ) { parentActivity ->
            val navigator: Navigator = (parentActivity as MainActivity).scope.get(Navigator::class, null, null)
            device.let { navigator.goTo(MousePadKey(it.deviceId)) }
        }
        return if (device.deviceType == DeviceType.TV) {
            val tvInput = PluginUiButton(
                pluginKey = pluginKey,
                name = context.getString(R.string.open_mousepad_tv),
                iconRes = R.drawable.tv_remote,
                category = ButtonCategory.CONTROL
            ) { parentActivity ->
                val navigator: Navigator = (parentActivity as MainActivity).scope.get(Navigator::class, null, null)
                navigator.goTo(BigscreenKey(device.deviceId))
            }
            listOf(mouseAndKeyboardInput, tvInput)
        } else {
            listOf(mouseAndKeyboardInput)
        }
    }

    var isKeyboardEnabled: Boolean = true
        private set

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        this.isKeyboardEnabled = np.getBoolean("state", true)
        return true
    }

    suspend fun sendMouseDelta(dx: Float, dy: Float) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["dx"] = dx.toDouble()
        np["dy"] = dy.toDouble()
        sendPacket(np)
    }

    suspend fun sendLeftClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["singleclick"] = true
        sendPacket(np)
    }

    suspend fun sendDoubleClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["doubleclick"] = true
        sendPacket(np)
    }

    suspend fun sendMiddleClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["middleclick"] = true
        sendPacket(np)
    }

    suspend fun sendRightClick() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["rightclick"] = true
        sendPacket(np)
    }

    suspend fun sendSingleHold() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["singlehold"] = true
        sendPacket(np)
    }

    suspend fun sendSingleRelease() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["singlerelease"] = true
        sendPacket(np)
    }

    suspend fun sendScroll(dx: Double, dy: Double) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["scroll"] = true
        np["dx"] = dx
        np["dy"] = dy
        sendPacket(np)
    }

    suspend fun sendLeft() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_LEFT]!!
        sendPacket(np)
    }

    suspend fun sendRight() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_RIGHT]!!
        sendPacket(np)
    }

    suspend fun sendUp() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_UP]!!
        sendPacket(np)
    }

    suspend fun sendDown() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_DPAD_DOWN]!!
        sendPacket(np)
    }

    suspend fun sendSelect() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_ENTER]!!
        sendPacket(np)
    }

    suspend fun sendHome() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["alt"] = true
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_F4]!!
        device.sendPacket(np)
    }

    suspend fun sendBack() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_ENCODING_MAP[KeyEvent.KEYCODE_ESCAPE]!!
        device.sendPacket(np)
    }

    suspend fun sendText(content: String) {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["key"] = content
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
    displayNameRes = R.string.pref_plugin_mousepad,
    descriptionRes = R.string.pref_plugin_mousepad_desc_nontv,
    supportedPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST),
)
