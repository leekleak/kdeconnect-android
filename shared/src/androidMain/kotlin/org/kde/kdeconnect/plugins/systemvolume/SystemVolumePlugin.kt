/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.pref_plugin_systemvolume
import org.kde.kdeconnect.generated.resources.pref_plugin_systemvolume_desc
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.PermissionPluginInfo
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin.Companion.PACKET_TYPE_SYSTEMVOLUME
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin.Companion.PACKET_TYPE_SYSTEMVOLUME_REQUEST

object SystemVolumePluginInfo : PermissionPluginInfo(
    pluginKey = "SystemVolumePlugin",
    instantiableClass = SystemVolumePlugin::class.java,
    displayNameRes = Res.string.pref_plugin_systemvolume,
    descriptionRes = Res.string.pref_plugin_systemvolume_desc,
    supportedPacketTypes = setOf(PACKET_TYPE_SYSTEMVOLUME),
    outgoingPacketTypes = setOf(PACKET_TYPE_SYSTEMVOLUME_REQUEST),
    lazy = true
)

class SystemVolumePlugin(private val device: Device) : Plugin() {

    override val pluginInfo: PermissionPluginInfo = SystemVolumePluginInfo

    private val _sinks = MutableStateFlow<List<Sink>>(emptyList())
    val sinks: StateFlow<List<Sink>> = _sinks.asStateFlow()

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if ("sinkList" in np) {
            try {
                val sinkArray = checkNotNull(np.getJsonArray("sinkList"))
                val newList = mutableListOf<Sink>()
                for (i in sinkArray.indices) {
                    val sinkObj = sinkArray[i].jsonObject
                    newList.add(Sink(sinkObj))
                }
                _sinks.value = newList
            } catch (e: SerializationException) {
                LoggerTagged.e(e) { "Exception" }
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
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST).update {
            put("volume", volume)
            put("name", name)
        }
        device.sendPacket(np)
    }

    internal suspend fun sendMute(name: String, mute: Boolean) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST).update {
            put("muted", mute)
            put("name", name)
        }
        device.sendPacket(np)
    }

    internal suspend fun sendEnable(name: String) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST).update {
            put("enabled", true)
            put("name", name)
        }
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_SYSTEMVOLUME = "kdeconnect.systemvolume"
        const val PACKET_TYPE_SYSTEMVOLUME_REQUEST = "kdeconnect.systemvolume.request"
    }
}
