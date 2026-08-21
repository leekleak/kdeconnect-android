/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import android.content.Context
import android.os.Build
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.Plugin
import java.util.stream.Collectors

class RunCommandPlugin(
    context: Context,
    device: Device,
    private val settingsDataStore: RunCommandSettingsDataStore
) : Plugin(context, device) {
    val output: SnapshotStateList<RunCommandOutput> = SnapshotStateList()

    private val _commandList: MutableStateFlow<List<RunCommand>> = MutableStateFlow(emptyList())
    val commandList: StateFlow<List<RunCommand>> = _commandList.asStateFlow()

    private val _canAddCommand: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val canAddCommand: StateFlow<Boolean> = _canAddCommand.asStateFlow()

    override val pluginInfo: RunCommandPluginInfo = RunCommandPluginInfo

    fun interface CommandsChangedCallback {
        fun update()
    }

    var commandRunning: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreate(): Boolean {
        coroutineScope.launch { requestCommandList() }
        return true
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.has("commandList")) {
            _commandList.value = ArrayList()
            try {
                val parsedCommands = RunCommand.fromPacket(np.getString("commandList"))
                _commandList.value = parsedCommands.sortedBy { it.name }

                // Used only by RunCommandControlsProviderService to display controls correctly even when device is not available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    CoroutineScope(Dispatchers.IO).launch {
                        settingsDataStore.setCommands(device.deviceId, commandList.value)
                    }
                }

                forceRefreshWidgets(context)
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Error parsing command list" }
            }

            _canAddCommand.value = np.getBoolean("canAddCommand", false)

            return true
        } else if (np.has("stdout")) {
            val stdOut = np.getStringList("stdout")
            val stdErr = np.getStringList("stderr")
            checkNotNull(stdOut)
            checkNotNull(stdErr)
            for (line in stdOut) {
                LoggerTagged.d { "Line:$line" }
                output.add(RunCommandOutput(line, false))
            }
            for (line in stdErr) {
                LoggerTagged.d { "Line:$line" }
                output.add(RunCommandOutput(line, false))
            }

            return true
        } else if (np.has("commandFinished")) {
            commandRunning.value = false

            val newCommand = RunCommandOutput(">", true)
            if (output[output.size - 1] == newCommand) {
                return true
            }

            output.removeAll(
                output.stream().filter { output -> output.string == ">" }
                    .collect(Collectors.toList())
            )

            output.add(newCommand)

            return true
        }
        return false
    }

    suspend fun runCommand(cmdKey: String) {
        LoggerTagged.d { "Sending $cmdKey" }
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["key"] = cmdKey
        device.sendPacket(np)
        commandRunning.value = true
    }

    private suspend fun requestCommandList() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["requestCommandList"] = true
        device.sendPacket(np)
    }

    fun canAddCommand(): Boolean {
        return canAddCommand.value
    }

    suspend fun sendSetupPacket() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["setup"] = true
        device.sendPacket(np)
    }

    suspend fun sendStop() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["stop"] = true
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_RUNCOMMAND: String = "kdeconnect.runcommand"
        const val PACKET_TYPE_RUNCOMMAND_OUTPUT: String = "kdeconnect.runcommand.output"
        const val PACKET_TYPE_RUNCOMMAND_REQUEST: String = "kdeconnect.runcommand.request"
    }
}
