/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.runcommand

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.CommandAction
import android.service.controls.actions.ControlAction
import android.service.controls.templates.StatelessTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.jdk9.asPublisher
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.ui.MainActivity
import org.koin.android.ext.android.inject
import org.kde.kdeconnect_tp.R
import java.util.concurrent.Flow
import java.util.function.Consumer

private class CommandEntryWithDevice(o: JSONObject, val device: Device) : CommandEntry(o)

@RequiresApi(Build.VERSION_CODES.R)
class RunCommandControlsProviderService : ControlsProviderService() {
    private val deviceManager: DeviceManager by inject()
    private val updateFlow = MutableSharedFlow<Control>(replay = 10, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private lateinit var sharedPreferences: SharedPreferences

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        return flow {
            getAllCommandsList().forEach { commandEntry ->
                emit(Control.StatelessBuilder(commandEntry.device.deviceId + ":" + commandEntry.key, getIntent(commandEntry.device))
                    .setTitle(commandEntry.name)
                    .setSubtitle(commandEntry.command)
                    .setStructure(commandEntry.device.name)
                    .setCustomIcon(Icon.createWithResource(this@RunCommandControlsProviderService, R.drawable.run_command_plugin_icon_24dp))
                    .build())
            }
        }.asPublisher()
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        for (controlId in controlIds) {
            val commandEntry = getCommandByControlId(controlId)
            if (commandEntry != null && commandEntry.device.isReachable) {
                updateFlow.tryEmit(createStatefulBuilder(commandEntry, controlId)
                        .setStatus(Control.STATUS_OK)
                        .setStatusText(getString(R.string.tap_to_execute))
                        .build())
            } else if (commandEntry != null && commandEntry.device.isPaired && !commandEntry.device.isReachable) {
                updateFlow.tryEmit(createStatefulBuilder(commandEntry, controlId)
                        .setStatus(Control.STATUS_DISABLED)
                        .build())
            } else {
                updateFlow.tryEmit(Control.StatefulBuilder(controlId, getIntent(commandEntry?.device))
                        .setStatus(Control.STATUS_NOT_FOUND)
                        .build())
            }
        }

        return updateFlow.asPublisher()
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        if (action is CommandAction) {
            val commandEntry = getCommandByControlId(controlId)
            if (commandEntry != null) {
                val deviceId = controlId.split(":")[0]
                val plugin = deviceManager.getDevicePlugin(deviceId, RunCommandPlugin::class.java)
                if (plugin != null) {
                    plugin.runCommand(commandEntry.key)
                    consumer.accept(ControlAction.RESPONSE_OK)
                } else {
                    consumer.accept(ControlAction.RESPONSE_FAIL)
                }

                updateFlow.tryEmit(createStatefulBuilder(commandEntry, controlId)
                        .setStatus(Control.STATUS_OK)
                        .setStatusText(getString(R.string.tap_to_execute))
                        .build())
            }
        }
    }

    private fun getSavedCommandsList(device: Device): List<CommandEntryWithDevice> {
        if (!this::sharedPreferences.isInitialized) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        }

        val commandList = mutableListOf<CommandEntryWithDevice>()

        return try {
            val jsonArray = JSONArray(sharedPreferences.getString(RunCommandPlugin.KEY_COMMANDS_PREFERENCE + device.deviceId, "[]"))

            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(index)
                commandList.add(CommandEntryWithDevice(jsonObject, device))
            }

            commandList
        } catch (error: JSONException) {
            Log.e("RunCommand", "Error parsing JSON", error)
            listOf()
        }
    }

    private fun getAllCommandsList(): List<CommandEntryWithDevice> {
        val commandList = mutableListOf<CommandEntryWithDevice>()

        for (device in deviceManager.devices.values) {
            if (!device.isReachable) {
                commandList.addAll(getSavedCommandsList(device))
                continue
            } else if (!device.isPaired) {
                continue
            }

            val plugin = device.getPlugin(RunCommandPlugin::class.java)
            if (plugin != null) {
                for (jsonObject in plugin.commandList) {
                    try {
                        commandList.add(CommandEntryWithDevice(jsonObject, device))
                    } catch (error: JSONException) {
                        Log.e("RunCommand", "Error parsing JSON", error)
                    }
                }
            }
        }

        return commandList
    }

    private fun getCommandByControlId(controlId: String): CommandEntryWithDevice? {
        val controlIdParts = controlId.split(":")

        val device = deviceManager.getDevice(controlIdParts[0])

        if (device == null || !device.isPaired) return null

        val commandList = if (device.isReachable) {
            device.getPlugin(RunCommandPlugin::class.java)?.commandList?.map { jsonObject ->
                CommandEntryWithDevice(jsonObject, device)
            }
        } else {
            getSavedCommandsList(device)
        }

        return commandList?.find { command ->
            try {
                command.key == controlIdParts[1]
            } catch (error: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", error)
                false
            }
        }
    }

    private fun createStatefulBuilder(commandEntry: CommandEntryWithDevice, controlId: String): Control.StatefulBuilder {
        if (!this::sharedPreferences.isInitialized) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        }

        return Control.StatefulBuilder(controlId, getIntent(commandEntry.device))
                .setTitle(commandEntry.name)
                .setSubtitle(commandEntry.command)
                .setStructure(commandEntry.device.name)
                .setControlTemplate(StatelessTemplate(commandEntry.key))
                .setDeviceType(DeviceTypes.TYPE_ROUTINE)
                .setCustomIcon(Icon.createWithResource(this, R.drawable.run_command_plugin_icon_24dp))
    }

    private fun getIntent(device: Device?): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_DEVICE_ID, device?.deviceId)

        if (device?.isReachable == true) {
            intent.putExtra(org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants.EXTRA_PLUGIN_KEY, "RunCommandPlugin")
        }

        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
