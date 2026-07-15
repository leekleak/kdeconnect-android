/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.plugins.battery.BatteryPluginInfo
import org.kde.kdeconnect.plugins.clipboard.ClipboardPluginInfo
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityReportPluginInfo
import org.kde.kdeconnect.plugins.contacts.ContactsPluginInfo
import org.kde.kdeconnect.plugins.digitizer.DigitizerPluginInfo
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePluginInfo
import org.kde.kdeconnect.plugins.findremotedevice.FindRemoteDevicePluginInfo
import org.kde.kdeconnect.plugins.inputdevicesreceiver.InputDevicesReceiverPluginInfo
import org.kde.kdeconnect.plugins.mousepad.MousePadPluginSettings
import org.kde.kdeconnect.plugins.mousereceiver.MouseReceiverPluginInfo
import org.kde.kdeconnect.plugins.mpris.MprisPluginSettings
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPluginInfo
import org.kde.kdeconnect.plugins.notifications.NotificationsPluginInfo
import org.kde.kdeconnect.plugins.ping.PingPluginInfo
import org.kde.kdeconnect.plugins.presenter.PresenterPluginInfo
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPluginInfo
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPluginInfo
import org.kde.kdeconnect.plugins.runcommand.RunCommandPluginInfo
import org.kde.kdeconnect.plugins.sftp.SftpPluginInfo
import org.kde.kdeconnect.plugins.share.SharePluginInfo
import org.kde.kdeconnect.plugins.sms.SMSPluginInfo
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePluginInfo
import org.kde.kdeconnect.plugins.telephony.TelephonyPluginInfo
import org.kde.kdeconnect.ui.AlertDialogFragment
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect_tp.R
import org.koin.core.Koin

object PluginFactory {

    private val pluginInfo: Map<String, PluginInfo> = mapOf(
        TelephonyPluginInfo.pluginKey to TelephonyPluginInfo,
        BatteryPluginInfo.pluginKey to BatteryPluginInfo,
        ConnectivityReportPluginInfo.pluginKey to ConnectivityReportPluginInfo,
        ClipboardPluginInfo.pluginKey to ClipboardPluginInfo,
        ContactsPluginInfo.pluginKey to ContactsPluginInfo,
        DigitizerPluginInfo.pluginKey to DigitizerPluginInfo,
        FindRemoteDevicePluginInfo.pluginKey to FindRemoteDevicePluginInfo,
        InputDevicesReceiverPluginInfo.pluginKey to InputDevicesReceiverPluginInfo,
        MousePadPluginSettings.pluginKey to MousePadPluginSettings,
        MouseReceiverPluginInfo.pluginKey to MouseReceiverPluginInfo,
        MprisPluginSettings.pluginKey to MprisPluginSettings,
        NotificationsPluginInfo.pluginKey to NotificationsPluginInfo,
        PingPluginInfo.pluginKey to PingPluginInfo,
        PresenterPluginInfo.pluginKey to PresenterPluginInfo,
        ReceiveNotificationsPluginInfo.pluginKey to ReceiveNotificationsPluginInfo,
        SftpPluginInfo.pluginKey to SftpPluginInfo,
        SMSPluginInfo.pluginKey to SMSPluginInfo,
        SystemVolumePluginInfo.pluginKey to SystemVolumePluginInfo,

        // Java plugins
        FindMyPhonePluginInfo.pluginKey to FindMyPhonePluginInfo,
        MprisReceiverPluginInfo.pluginKey to MprisReceiverPluginInfo,
        RemoteKeyboardPluginInfo.pluginKey to RemoteKeyboardPluginInfo,
        RunCommandPluginInfo.pluginKey to RunCommandPluginInfo,
        SharePluginInfo.pluginKey to SharePluginInfo,
    )

    private fun instantiatePlugin(context: Context, device: Device, clazz: Class<out Plugin>): Plugin {
        return try {
            clazz.getDeclaredConstructor(Context::class.java, Device::class.java).newInstance(context, device)
        } catch (_: Exception) {
            clazz.getDeclaredConstructor().newInstance()
        }
    }

    val availablePlugins: Set<String>
        get() = pluginInfo.keys
    val incomingCapabilities: Set<String>
        get() = pluginInfo.values.flatMap { plugin -> plugin.supportedPacketTypes }.toSet()
    val outgoingCapabilities: Set<String>
        get() = pluginInfo.values.flatMap { plugin -> plugin.outgoingPacketTypes }.toSet()

    @JvmStatic
    fun getPluginInfo(pluginKey: String): PluginInfo = pluginInfo[pluginKey]!!

    @JvmStatic
    fun sortPluginList(context: Context, plugins: List<String>): List<String> {
        return plugins.sortedBy { pluginInfo[it]?.getDisplayName(context) }
    }

    fun instantiatePluginForDevice(context: Context, pluginKey: String, device: Device): Plugin? {
        try {
            val clazz = pluginInfo[pluginKey]?.instantiableClass ?: return null
            val plugin = device.koinScope.getOrNull(clazz.kotlin) ?: instantiatePlugin(context, device, clazz)
            return plugin
        } catch (e: Exception) {
            Log.e("PluginFactory", "Could not instantiate plugin: $pluginKey", e)
            return null
        }
    }

    fun pluginsForCapabilities(incoming: Set<String>, outgoing: Set<String>): Set<String> {
        fun hasCommonCapabilities(info: PluginInfo): Boolean =
            outgoing.any { it in info.supportedPacketTypes } ||
            incoming.any { it in info.outgoingPacketTypes }

        val (used, unused) = pluginInfo.entries.partition { hasCommonCapabilities(it.value) }

        for (pluginId in unused.map { it.key }) {
            Log.d("PluginFactory", "Won't load $pluginId because of unmatched capabilities")
        }

        return used.map { it.key }.toSet()
    }
}
