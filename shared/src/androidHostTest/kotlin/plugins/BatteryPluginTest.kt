package org.kde.kdeconnect.plugins

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo

@RunWith(AndroidJUnit4::class)
class BatteryPluginTest {
    private lateinit var batteryPlugin: BatteryPlugin
    private lateinit var context: Context
    private lateinit var device: Device
    private var packet: NetworkPacket? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        device = mockk(relaxed = true) {
            every { deviceId } returns "some_id"
            val packetSlot = slot<NetworkPacket>()
            coEvery { sendPacket(capture(packetSlot)) } answers {
                packet = packetSlot.captured
                true
            }
        }
        batteryPlugin = BatteryPlugin(context, device)
    }

    @After
    fun cleanup() {
        packet = null // remove old capture packet
    }

    // LOCAL -> REMOTE

    @Test
    fun testSendBatteryLow() {
        val batLowIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_LOW
        }

        batteryPlugin.receiver.onReceive(context, batLowIntent)

        Assert.assertNull(packet)

        // Now send a battery changed event
        val batChangedIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 20)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }

        batteryPlugin.receiver.onReceive(context, batChangedIntent)

        // Check battery info updated accordingly
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p1 = checkNotNull(packet)

        Assert.assertEquals(20, p1.getInt("currentCharge"))
        Assert.assertEquals(false, p1.getBoolean("isCharging"))
        Assert.assertEquals(1, p1.getInt("thresholdEvent"))
    }

    @Test
    fun testSendBatteryOkAfterLow() {
        // First simulate battery low
        val batLowIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_LOW
        }
        batteryPlugin.receiver.onReceive(context, batLowIntent)

        Assert.assertNull(packet)

        val batChangedIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 20)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }

        batteryPlugin.receiver.onReceive(context, batChangedIntent)

        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p1 = checkNotNull(packet)

        Assert.assertEquals(20, p1.getInt("currentCharge"))
        Assert.assertEquals(false, p1.getBoolean("isCharging"))
        Assert.assertEquals(1, p1.getInt("thresholdEvent"))


        packet = null

        // Now simulate battery is okay
        val okIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_OKAY
        }
        batteryPlugin.receiver.onReceive(context, okIntent)

        Assert.assertNull(packet)

        val batChangedIntent2 = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 25)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }

        batteryPlugin.receiver.onReceive(context, batChangedIntent2)

        // Check if the isLowBattery flag is reset
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p2 = checkNotNull(packet)

        Assert.assertEquals(25, p2.getInt("currentCharge"))
        Assert.assertEquals(false, p2.getBoolean("isCharging"))
        Assert.assertEquals(0, p2.getInt("thresholdEvent"))
    }

    @Test
    fun testBatteryCharging() {
        val batChangedintent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 50)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        batteryPlugin.receiver.onReceive(context, batChangedintent)

        // Check battery info updated accordingly
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p1 = checkNotNull(packet)

        Assert.assertEquals(50, p1.getInt("currentCharge"))
        Assert.assertEquals(true, p1.getBoolean("isCharging"))
        Assert.assertEquals(0, p1.getInt("thresholdEvent"))
    }

    @Test
    fun testBatteryChargingToLow() {
        val chargingIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 25)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        batteryPlugin.receiver.onReceive(context, chargingIntent)

        // Initial state should reflect charging
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p1 = checkNotNull(packet)

        Assert.assertEquals(25, p1.getInt("currentCharge"))
        Assert.assertEquals(true, p1.getBoolean("isCharging"))
        Assert.assertEquals(0, p1.getInt("thresholdEvent"))

        packet = null

        // Now simulate battery low condition
        val batLowIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_LOW
        }

        batteryPlugin.receiver.onReceive(context, batLowIntent)

        Assert.assertNull(packet)

        val chargingIntent2 = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 20)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
        }
        batteryPlugin.receiver.onReceive(context, chargingIntent2)

        // Check battery info updated accordingly
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p2 = checkNotNull(packet)

        Assert.assertEquals(20, p2.getInt("currentCharge"))
        Assert.assertEquals(false, p2.getBoolean("isCharging"))
        Assert.assertEquals(1, p2.getInt("thresholdEvent"))
    }

    @Test
    fun testBatteryStatusChangeFromChargingToNotCharging() {
        val chargingIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 60)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC)
        }
        batteryPlugin.receiver.onReceive(context, chargingIntent)

        // Initial state should reflect charging
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p1 = checkNotNull(packet)

        Assert.assertEquals(60, p1.getInt("currentCharge"))
        Assert.assertEquals(true, p1.getBoolean("isCharging"))

        // Now, simulate battery status change to not charging
        val notChargingIntent = Intent().apply {
            action = Intent.ACTION_BATTERY_CHANGED
            putExtra(BatteryManager.EXTRA_LEVEL, 60)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0) // Not charging
        }
        batteryPlugin.receiver.onReceive(context, notChargingIntent)

        // Check battery info updated accordingly
        coVerify(timeout = 2000) { device.sendPacket(any()) }
        val p2 = checkNotNull(packet)

        Assert.assertEquals(60, p2.getInt("currentCharge"))
        Assert.assertEquals(false, p2.getBoolean("isCharging"))
        Assert.assertEquals(0, p2.getInt("thresholdEvent"))
    }

    // REMOTE -> LOCAL

    @Test
    fun checkPacketType() = runBlocking {
        Assert.assertFalse(batteryPlugin.onPacketReceived(NetworkPacket("invalid type")))

        Assert.assertTrue(batteryPlugin.onPacketReceived(NetworkPacket("kdeconnect.battery")))
    }

    @Test
    fun processIncomingBatteryInfoPacket() {
        val packet = NetworkPacket("kdeconnect.battery").update {
            put("currentCharge", 75)
            put("isCharging", true)
            put("thresholdEvent", 0)
        }

        val batteryInfoSlot = slot<DeviceBatteryInfo>()
        every { device.updateBatteryInfo(capture(batteryInfoSlot)) } returns Unit

        runBlocking { batteryPlugin.onPacketReceived(packet) }

        val batteryInfo = batteryInfoSlot.captured
        Assert.assertEquals(75, batteryInfo.currentCharge)
        Assert.assertEquals(true, batteryInfo.isCharging)
        Assert.assertEquals(0, batteryInfo.thresholdEvent)
    }
}
