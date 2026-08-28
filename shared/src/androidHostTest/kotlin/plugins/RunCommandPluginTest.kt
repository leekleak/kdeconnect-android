package org.kde.kdeconnect.plugins

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin

@RunWith(AndroidJUnit4::class)
class RunCommandPluginTest {
    private lateinit var runCommandPlugin: RunCommandPlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private lateinit var settingsDataStore: RunCommandSettingsDataStore
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        device = mockk {
            every { deviceId } returns "some_id"
            coEvery { sendPacket(any()) } answers {
                val sentPacket = arg<NetworkPacket>(0)
                packet = sentPacket
                true
            }
        }
        settingsDataStore = mockk(relaxed = true)
        runCommandPlugin = RunCommandPlugin(context, device, settingsDataStore)
    }

    @After
    fun cleanup() {
        packet = null // Ensuring we clean up any captured packets
    }

    // LOCAL -> REMOTE

    @Test
    fun testRunCommand() = runBlocking {
        val commandKey = "testCommandKey"
        runCommandPlugin.runCommand(commandKey)

        val sentPacket = checkNotNull(packet)
        assertEquals("kdeconnect.runcommand.request", sentPacket.type)
        assertEquals(commandKey, sentPacket.getString("key"))
    }

    @Test
    fun testRequestCommandList() = runBlocking {
        runCommandPlugin.onCreate() // Simulate plugin creation that requests command list

        coVerify(timeout = 1000) { device.sendPacket(any()) }
        val sentPacket = checkNotNull(packet)
        assertEquals("kdeconnect.runcommand.request", sentPacket.type)
        assertTrue(sentPacket.has("requestCommandList"))
        assertTrue(sentPacket.getBoolean("requestCommandList") == true)
    }

    // REMOTE -> LOCAL

    @Test
    fun testReceiveCommandList() = runBlocking {
        val commandListPacket = NetworkPacket("kdeconnect.runcommand").update {
            put("commandList", buildJsonObject {
                put("command1", buildJsonObject {
                    put("name", "Command 1")
                    put("command", "cmd1")
                })
                put("command2", buildJsonObject {
                    put("name", "Command 2")
                    put("command", "cmd2")
                })
            })
        }

        assertTrue(runCommandPlugin.onPacketReceived(commandListPacket))

        val commandList = runCommandPlugin.commandList.value
        assertEquals(2, commandList.size)

        val command1 = commandList[0]
        assertEquals("command1", command1.key)
        assertEquals("Command 1", command1.name)

        val command2 = commandList[1]
        assertEquals("command2", command2.key)
        assertEquals("Command 2", command2.name)
    }

    @Test
    fun testReceiveCommandsUpdate() = runBlocking {
        // First, simulate receiving a basic command list
        val initialCommandPacket = NetworkPacket("kdeconnect.runcommand").update {
            put("commandList", buildJsonObject {
                put("command1", buildJsonObject {
                    put("name", "Command 1")
                    put("command", "cmd1")
                })
            })
        }
        assertTrue(runCommandPlugin.onPacketReceived(initialCommandPacket))

        // Then, send a new packet with an updated command1 and a new command2
        val updatedCommandPacket = NetworkPacket("kdeconnect.runcommand").update {
            put("commandList", buildJsonObject {
                put("command1", buildJsonObject {
                    put("name", "Updated Command 1")
                    put("command", "cmd1")
                })
                put("command2", buildJsonObject {
                    put("name", "Command 2")
                    put("command", "cmd2")
                })
            })
        }
        assertTrue(runCommandPlugin.onPacketReceived(updatedCommandPacket))

        // Afterward we check the list has been updated appropriately
        val commandList = runCommandPlugin.commandList.value
        assertEquals(2, commandList.size)

        val command2 = commandList[0]
        assertEquals("command2", command2.key)
        assertEquals("Command 2", command2.name)

        val updatedCommand1 = commandList[1]
        assertEquals("command1", updatedCommand1.key)
        assertEquals("Updated Command 1", updatedCommand1.name)
    }

    @Test
    fun testCanAddCommandFlag() = runBlocking {
        fun JsonObjectBuilder.addBasicCommandList() {
            put("commandList", buildJsonObject {
                put("command1", buildJsonObject {
                    put("name", "Command 1")
                    put("key", "command1")
                })
                put("command2", buildJsonObject {
                    put("name", "Command 2")
                    put("key", "command2")
                })
            })
        }

        val canAddCommandPacket = NetworkPacket("kdeconnect.runcommand").update {
            put("canAddCommand", true)
            addBasicCommandList()
        }

        assertTrue(runCommandPlugin.onPacketReceived(canAddCommandPacket))
        assertTrue(runCommandPlugin.canAddCommand())

        val cannotAddCommandPacket = NetworkPacket("kdeconnect.runcommand").update {
            put("canAddCommand", false)
            addBasicCommandList()
        }

        assertTrue(runCommandPlugin.onPacketReceived(cannotAddCommandPacket))
        assertFalse(runCommandPlugin.canAddCommand())
    }
}
