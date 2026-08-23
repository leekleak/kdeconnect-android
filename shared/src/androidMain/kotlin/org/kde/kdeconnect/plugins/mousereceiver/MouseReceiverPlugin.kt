/*
 * SPDX-FileCopyrightText: 2021 SohnyBohny <sohny.bean@streber24.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mousereceiver

import android.content.Context
import android.os.Build
import android.provider.Settings
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.mouse_receiver_no_permissions
import org.kde.kdeconnect.generated.resources.mouse_receiver_plugin_description
import org.kde.kdeconnect.generated.resources.mouse_receiver_plugin_name
import org.kde.kdeconnect.generated.resources.open_settings
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin.Companion.PACKET_TYPE_MOUSEPAD_REQUEST
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin
import org.kde.kdeconnect.ui.PermissionRequest
import kotlin.math.ceil
import kotlin.math.floor

class MouseReceiverPlugin(
    context: Context,
    device: Device,
    private val permissionRequestHelper: PermissionRequestHelper
) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = MouseReceiverPluginInfo

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type != PACKET_TYPE_MOUSEPAD_REQUEST) {
            LoggerTagged.e { "Invalid packet type for MouseReceiverPlugin: ${np.type}" }
            return false
        }

        if (RemoteKeyboardPlugin.getMousePadPacketType(np) != RemoteKeyboardPlugin.MousePadPacketType.Mouse) {
            return false // This packet will be handled by the remotekeyboard instead, silently ignore
        }

        if (!pluginInfo.checkRequiredPermissions(context)) {
            pluginInfo.showPermissionExplanation(context, permissionRequestHelper)
            return true
        }

        val dx = np.getDouble("dx", 0.0).let { if (it < 0) floor(it) else ceil(it) }.toInt()
        val dy = np.getDouble("dy", 0.0).let { if (it < 0) floor(it) else ceil(it) }.toInt()
        val x = np.getInt("x", 0)
        val y = np.getInt("y", 0)

        val isSingleClick = np.getBoolean("singleclick", false)
        val isDoubleClick = np.getBoolean("doubleclick", false)
        val isMiddleClick = np.getBoolean("middleclick", false)
        val isForwardClick = np.getBoolean("forwardclick", false)
        val isBackClick = np.getBoolean("backclick", false)

        val isRightClick = np.getBoolean("rightclick", false)
        val isSingleHold = np.getBoolean("singlehold", false)
        val isSingleRelease = np.getBoolean("singlerelease", false)
        val isScroll = np.getBoolean("scroll", false)

        if (isSingleClick || isDoubleClick || isMiddleClick || isRightClick || isSingleHold || isSingleRelease || isScroll || isForwardClick || isBackClick) {
            // Perform click
            return when {
                isSingleClick -> {
                    // LoggerTagged.i { "MouseReceiverPlugin", "singleClick")
                    MouseReceiverService.click()
                }
                isDoubleClick -> { // left & right
                    // LoggerTagged.i { "MouseReceiverPlugin", "doubleClick")
                    MouseReceiverService.recentButton()
                }
                isMiddleClick -> {
                    // LoggerTagged.i { "MouseReceiverPlugin", "middleClick")
                    MouseReceiverService.homeButton()
                }
                isRightClick -> {
                    // TODO right-click menu emulation
                    MouseReceiverService.backButton()
                }
                isForwardClick -> {
                    MouseReceiverService.recentButton()
                }
                isBackClick -> {
                    MouseReceiverService.backButton()
                }
                isSingleHold -> {
                    // For drag'n drop
                    // LoggerTagged.i { "MouseReceiverPlugin", "singleHold")
                    MouseReceiverService.longClickSwipe()
                }
                isSingleRelease -> {
                    MouseReceiverService.instance?.stopSwipe() ?: false
                }
                isScroll -> {
                    // LoggerTagged.i { "MouseReceiverPlugin", "scroll dx: $dx dy: $dy")
                    MouseReceiverService.scroll(dy) // dx is always 0
                }
                else -> false
            }
        } else {
            // Mouse Move
            if (dx != 0 || dy != 0) {
                // LoggerTagged.i { "MouseReceiverPlugin", "move Mouse dx: $dx dy: $dy")
                return MouseReceiverService.move(dx, dy)
            } else if (x != 0 || y != 0) {
                return MouseReceiverService.setPos(x, y)
            } else {
                // To hide the cursor once it crosses the barrier.
                MouseReceiverService.instance?.hide(0)
            }
        }
        return true
    }

    override val minSdk: Int
        get() = Build.VERSION_CODES.N

    companion object {
        private const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
    }
}

object MouseReceiverPluginInfo : PluginInfo(
    pluginKey = "MouseReceiverPlugin",
    instantiableClass = MouseReceiverPlugin::class.java,
    displayNameRes = Res.string.mouse_receiver_plugin_name,
    descriptionRes = Res.string.mouse_receiver_plugin_description,
    supportedPacketTypes = arrayOf(PACKET_TYPE_MOUSEPAD_REQUEST),
    outgoingPacketTypes = emptyArray(),
    lazy = true,
) {
    override suspend fun checkRequiredPermissions(context: Context): Boolean {
        return MouseReceiverService.instance != null
    }

    override suspend fun getPermissionRequests(): List<PermissionRequest> {
        return listOf(
            PermissionRequest(
                title = getString(Res.string.mouse_receiver_plugin_description),
                description = getString(Res.string.mouse_receiver_no_permissions),
                intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                positiveButton = getString(Res.string.open_settings)
            )
        )
    }
}
