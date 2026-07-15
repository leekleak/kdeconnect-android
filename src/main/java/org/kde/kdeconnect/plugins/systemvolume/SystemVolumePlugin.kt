/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONException
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin.Companion.PACKET_TYPE_SYSTEMVOLUME
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin.Companion.PACKET_TYPE_SYSTEMVOLUME_REQUEST
import org.kde.kdeconnect_tp.R
import java.util.concurrent.ConcurrentHashMap

object SystemVolumePluginInfo : PluginInfo(
    instantiableClass = SystemVolumePlugin::class.java,
    displayNameRes = R.string.pref_plugin_systemvolume,
    descriptionRes = R.string.pref_plugin_systemvolume_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_SYSTEMVOLUME),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_SYSTEMVOLUME_REQUEST),
)

class SystemVolumePlugin(context: Context, device: Device) : Plugin(context, device) {

    override val pluginInfo: PluginInfo = SystemVolumePluginInfo
    interface SinkListener {
        fun sinksChanged()
    }

    private val sinkMap: ConcurrentHashMap<String, Sink> = ConcurrentHashMap()
    private val listeners: MutableList<SinkListener> = mutableListOf()

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if ("sinkList" in np) {
            sinkMap.clear()

            try {
                val sinkArray = checkNotNull(np.getJSONArray("sinkList"))
                for (i in 0..< sinkArray.length()) {
                    val sinkObj = sinkArray.getJSONObject(i)
                    val sink = Sink(sinkObj)
                    sinkMap[sink.name] = sink
                }
            } catch (e: JSONException) {
                Log.e("KDEConnect", "Exception", e)
            }

            synchronized(listeners) {
                for (l in listeners) {
                    l.sinksChanged()
                }
            }
        } else {
            val name = np.getString("name")
            val sink = sinkMap[name]
            if (sink != null) {
                if ("volume" in np) {
                    sink.setVolume(np.getInt("volume"))
                }
                if ("muted" in np) {
                    sink.setMute(np.getBoolean("muted"))
                }
                if ("enabled" in np) {
                    sink.isDefault = np.getBoolean("enabled")
                }
            }
        }
        return true
    }

    internal fun sendVolume(name: String, volume: Int) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["volume"] = volume
        np["name"] = name
        device.sendPacket(np)
    }

    internal fun sendMute(name: String, mute: Boolean) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["muted"] = mute
        np["name"] = name
        device.sendPacket(np)
    }

    internal fun sendEnable(name: String) {
        val np = NetworkPacket(PACKET_TYPE_SYSTEMVOLUME_REQUEST)
        np["enabled"] = true
        np["name"] = name
        device.sendPacket(np)
    }

    val sinks: MutableCollection<Sink>
        get() = sinkMap.values

    internal fun addSinkListener(listener: SinkListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    internal fun removeSinkListener(listener: SinkListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    companion object {
        const val PACKET_TYPE_SYSTEMVOLUME = "kdeconnect.systemvolume"
        const val PACKET_TYPE_SYSTEMVOLUME_REQUEST = "kdeconnect.systemvolume.request"
    }
}
