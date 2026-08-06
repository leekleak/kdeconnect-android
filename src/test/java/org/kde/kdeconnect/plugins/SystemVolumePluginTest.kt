package org.kde.kdeconnect.plugins

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin

@RunWith(AndroidJUnit4::class)
class SystemVolumePluginTest {
    private lateinit var systemVolumePlugin: SystemVolumePlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        device = mockk {
            every { deviceId } returns "some_id"
            coEvery { sendPacket(any()) } answers {
                val sentPacket = arg<NetworkPacket>(0)
                packet = sentPacket
            }
        }
        systemVolumePlugin = SystemVolumePlugin(context, device)
    }

    @After
    fun cleanup() {
        packet = null // Ensuring we clean up any captured packets
    }

    // LOCAL -> REMOTE

    @Test
    fun testSendVolume() = runBlocking {
        systemVolumePlugin.sendVolume("Sink 1", 85)
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertEquals(85, sentPacket.getInt("volume"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    @Test
    fun testSendMute() = runBlocking {
        systemVolumePlugin.sendMute("Sink 1", true)
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertTrue(sentPacket.getBoolean("muted"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    @Test
    fun testSendEnable() = runBlocking {
        systemVolumePlugin.sendEnable("Sink 1")
        val sentPacket = checkNotNull(packet)

        assertEquals("kdeconnect.systemvolume.request", sentPacket.type)
        assertEquals(true, sentPacket.getBoolean("enabled"))
        assertEquals("Sink 1", sentPacket.getString("name"))
    }

    // REMOTE -> LOCAL

    @Test
    fun testReceiveSinkList() = runBlocking {
        // Simulate receiving a packet with sink list
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Sink 1")
                    put("volume", 50)
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                })
                put(JSONObject().apply {
                    put("name", "Sink 2")
                    put("volume", 70)
                    put("muted", true)
                    put("description", "")
                    put("maxVolume", 100)
                })
            })
        }

        assertTrue(systemVolumePlugin.onPacketReceived(sinkPacket))

        val sinks = systemVolumePlugin.sinks.value
        assertEquals(2, sinks.size)

        val sink1 = sinks.first { it.name == "Sink 1" }
        assertEquals(50, sink1.volume)
        assertTrue(!sink1.isMuted)

        val sink2 = sinks.first { it.name == "Sink 2" }
        assertEquals(70, sink2.volume)
        assertTrue(sink2.isMuted)
    }

    @Test
    fun testReceiveSinkUpdate() = runBlocking {
        // First, add a sink to ensure proper updates
        val sinkPacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("sinkList", JSONArray().apply {
                put(JSONObject().apply {
                    put("name", "Sink 1")
                    put("volume", 30)
                    put("muted", false)
                    put("description", "")
                    put("maxVolume", 100)
                })
            })
        }

        systemVolumePlugin.onPacketReceived(sinkPacket)

        // Update the sink's volume and mute status
        val updatePacket = NetworkPacket("kdeconnect.systemvolume").apply {
            set("name", "Sink 1")
            set("volume", 40)
            set("muted", true)
        }

        assertTrue(systemVolumePlugin.onPacketReceived(updatePacket))

        val sinks = systemVolumePlugin.sinks.value
        val updatedSink = sinks.first { it.name == "Sink 1" }

        assertEquals(40, updatedSink.volume)
        assertEquals(true, updatedSink.isMuted)
        assertTrue(updatedSink.isMuted)
    }
}
