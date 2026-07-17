/*
 * SPDX-FileCopyrightText: 2015 Aleix Pol Gonzalez <aleixpol@kde.org>
 * SPDX-FileCopyrightText: 2015 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.runcommand

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.preference.PreferenceManager
import org.apache.commons.collections4.iterators.IteratorIterable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.kde.kdeconnect_tp.R
import java.util.Collections
import java.util.stream.Collectors
import androidx.core.content.edit

class RunCommandPlugin(context: Context, device: Device) : Plugin(context, device) {
    val commandList: ArrayList<JSONObject> = ArrayList()
    private val callbacks = ArrayList<CommandsChangedCallback>()
    val commandItems: ArrayList<CommandEntry> = ArrayList()
    val output: SnapshotStateList<RunCommandOutput> = SnapshotStateList()

    private var sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var canAddCommand = false

    override val pluginInfo: RunCommandPluginInfo = RunCommandPluginInfo

    fun addCommandsUpdatedCallback(newCallback: CommandsChangedCallback) {
        callbacks.add(newCallback)
    }

    fun removeCommandsUpdatedCallback(theCallback: CommandsChangedCallback) {
        callbacks.remove(theCallback)
    }

    fun interface CommandsChangedCallback {
        fun update()
    }

    var commandRunning: MutableState<Boolean> = mutableStateOf(false)

    override fun getUiButtons(): List<PluginUiButton> {
        return listOf(
            PluginUiButton(
                context.getString(R.string.pref_plugin_runcommand),
                R.drawable.run_command_plugin_icon_24dp
            ) { parentActivity: Activity ->
                val navigator = (parentActivity as MainActivity).scope.get<Navigator>(
                    Navigator::class.java.kotlin, null, null
                )
                navigator.goTo(RunCommandKey(device.deviceId))
            })
    }

    override fun onCreate(): Boolean {
        requestCommandList()
        return true
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.has("commandList")) {
            commandList.clear()
            try {
                commandItems.clear()
                val obj = JSONObject(np.getString("commandList"))
                for (s in IteratorIterable(obj.keys())) {
                    val o = obj.getJSONObject(s)
                    o.put("key", s)
                    commandList.add(o)

                    try {
                        commandItems.add(
                            CommandEntry(o)
                        )
                    } catch (e: JSONException) {
                        Log.e("RunCommand", "Error parsing JSON", e)
                    }
                }

                Collections.sort(
                    commandItems,
                    Comparator.comparing(CommandEntry::name)
                )

                // Used only by RunCommandControlsProviderService to display controls correctly even when device is not available
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val array = JSONArray()

                    for (command in commandList) {
                        array.put(command)
                    }

                    sharedPreferences.edit {
                        putString(KEY_COMMANDS_PREFERENCE + device.deviceId, array.toString())
                    }
                }

                forceRefreshWidgets(context)
            } catch (e: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", e)
            }

            for (aCallback in callbacks) {
                aCallback.update()
            }

            device.onPluginsChanged()

            canAddCommand = np.getBoolean("canAddCommand", false)

            return true
        } else if (np.has("stdout")) {
            val stdOut = np.getStringList("stdout")
            val stdErr = np.getStringList("stderr")
            checkNotNull(stdOut)
            checkNotNull(stdErr)
            for (line in stdOut) {
                Log.d("STDOUT", "Line:$line")
                output.add(RunCommandOutput(line, false))
            }
            for (line in stdErr) {
                Log.d("STDERR", "Line:$line")
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

    fun runCommand(cmdKey: String) {
        Log.d("RunCommand", "Sending $cmdKey")
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["key"] = cmdKey
        device.sendPacket(np)
        commandRunning.value = true
    }

    private fun requestCommandList() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["requestCommandList"] = true
        device.sendPacket(np)
    }

    fun canAddCommand(): Boolean {
        return canAddCommand
    }

    fun sendSetupPacket() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["setup"] = true
        device.sendPacket(np)
    }

    fun sendStop() {
        val np = NetworkPacket(PACKET_TYPE_RUNCOMMAND_REQUEST)
        np["stop"] = true
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_RUNCOMMAND: String = "kdeconnect.runcommand"
        const val PACKET_TYPE_RUNCOMMAND_OUTPUT: String = "kdeconnect.runcommand.output"
        const val PACKET_TYPE_RUNCOMMAND_REQUEST: String = "kdeconnect.runcommand.request"
        const val KEY_COMMANDS_PREFERENCE: String = "commands_preference_"
    }
}
