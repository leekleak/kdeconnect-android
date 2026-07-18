/*
 * SPDX-FileCopyrightText: 2014 Ahmed I. Khalil <ahmedibrahimkhali@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.presenter

import android.content.Context
import android.view.KeyEvent
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.helpers.SPECIAL_KEY_MAP
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin.Companion.PACKET_TYPE_PRESENTER
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect_tp.R
import org.koin.java.KoinJavaComponent.inject

object PresenterPluginInfo : PluginInfo(
    instantiableClass = PresenterPlugin::class.java,
    displayNameRes = R.string.pref_plugin_presenter,
    descriptionRes = R.string.pref_plugin_presenter_desc,
    supportedPacketTypes = emptyArray(),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST, PACKET_TYPE_PRESENTER),
)

class PresenterPlugin(context: Context, device: Device) : Plugin(context, device) {

    override val pluginInfo: PluginInfo = PresenterPluginInfo
    override val isCompatible: Boolean
        get() = device.deviceType != DeviceType.PHONE && super.isCompatible

    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton(
            context.getString(R.string.pref_plugin_presenter),
            R.drawable.ic_presenter_24dp
        ) {
            val navigator: Navigator by inject(Navigator::class.java)
            navigator.goTo(PresenterKey(device.deviceId))
        })

    fun sendNext() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_MAP.get(KeyEvent.KEYCODE_PAGE_DOWN)
        device.sendPacket(np)
    }

    fun sendPrevious() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_MAP.get(KeyEvent.KEYCODE_PAGE_UP)
        device.sendPacket(np)
    }

    fun sendFullscreen() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_MAP.get(KeyEvent.KEYCODE_F5)
        device.sendPacket(np)
    }

    fun sendEsc() {
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_REQUEST)
        np["specialKey"] = SPECIAL_KEY_MAP.get(KeyEvent.KEYCODE_ESCAPE)
        device.sendPacket(np)
    }

    fun sendPointer(xDelta: Float, yDelta: Float) {
        val np = NetworkPacket(PACKET_TYPE_PRESENTER)
        np["dx"] = xDelta.toDouble()
        np["dy"] = yDelta.toDouble()
        device.sendPacket(np)
    }

    fun stopPointer() {
        val np = NetworkPacket(PACKET_TYPE_PRESENTER)
        np["stop"] = true
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_PRESENTER = "kdeconnect.presenter"
        const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
    }
}
