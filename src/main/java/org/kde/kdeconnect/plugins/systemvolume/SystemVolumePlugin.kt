/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONException
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin.Companion.PACKET_TYPE_SYSTEMVOLUME
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin.Companion.PACKET_TYPE_SYSTEMVOLUME_REQUEST
import org.kde.kdeconnect_tp.R

object SystemVolumePluginInfo : PluginInfo(
    instantiableClass = SystemVolumePlugin::class.java,
    displayNameRes = R.string.pref_plugin_systemvolume,
    descriptionRes = R.string.pref_plugin_systemvolume_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_SYSTEMVOLUME),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_SYSTEMVOLUME_REQUEST),
)

class SystemVolumePlugin(context: Context, device: Device) : Plugin(context, device) {

    override val pluginInfo: PluginInfo = SystemVolumePluginInfo

    private val _sinks = MutableStateFlow<List<Sink>>(emptyList())
    val sinks: StateFlow<List<Sink>> = _sinks.asStateFlow()

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if ("sinkList" in np) {
            try {
                val sinkArray = checkNotNull(np.getJSONArray("sinkList"))
                val newList = mutableListOf<Sink>()
                for (i in 0..< sinkArray.length()) {
                    val sinkObj = sinkArray.getJSONObject(i)
                    newList.add(Sink(sinkObj))
                }
                _sinks.value = newList
            } catch (e: JSONException) {
                Log.e("KDEConnect", "Exception", e)
            }
        } else {
            val name = np.getString("name")
            _sinks.update { current ->
                current.map {
                    if (it.name == name) {
                        it.copy(
                            volume = np.getInt("volume", it.volume),
                            isMuted = np.getBoolean("muted", it.isMuted),
                            isDefault = np.getBoolean("enabled", it.isDefault)
                        )
                    } else it
                }
            }
        }
        return true
    }

    internal suspend fun sendVolume(name: String, volume: Int) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["volume"] = volume
        np["name"] = name
        device.sendPacket(np)
    }

    internal suspend fun sendMute(name: String, mute: Boolean) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["muted"] = mute
        np["name"] = name
        device.sendPacket(np)
    }

    internal suspend fun sendEnable(name: String) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["enabled"] = true
        np["name"] = name
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_SYSTEMVOLUME = "kdeconnect.systemvolume"
        const val PACKET_TYPE_SYSTEMVOLUME_REQUEST = "kdeconnect.systemvolume.request"
    }
}
