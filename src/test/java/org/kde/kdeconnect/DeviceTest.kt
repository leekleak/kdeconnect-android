/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.DeviceInfo.Companion.fromIdentityPacketAndCert
import org.kde.kdeconnect.DeviceInfo.Companion.isValidDeviceId
import org.kde.kdeconnect.DeviceInfo.Companion.isValidIdentityPacket
import org.kde.kdeconnect.DeviceType.Companion.fromString
import org.kde.kdeconnect.backends.lan.LanLink
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.DevicesRoomDatabase
import org.kde.kdeconnect.helpers.security.EcHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.PluginFactory
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.security.cert.CertificateException
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class DeviceTest {
    private val context: Context = mockk(relaxed = true)
    private lateinit var deviceSettings: DeviceSettings
    private lateinit var db: DevicesRoomDatabase
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private lateinit var sslHelper: SslHelper

    // Creating a paired device before each test case
    @Before
    fun setUp() {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(realContext, DevicesRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deviceSettings = DeviceSettings(db.deviceDao())
        sslHelper = SslHelper(settingsDataStore, deviceSettings)
        val deviceId = "testDevice"
        val name = "Test Device"
        val encodedCertificate = """
            MIIDVzCCAj+gAwIBAgIBCjANBgkqhkiG9w0BAQUFADBVMS8wLQYDVQQDDCZfZGExNzlhOTFfZjA2
            NF80NzhlX2JlOGNfMTkzNWQ3NTQ0ZDU0XzEMMAoGA1UECgwDS0RFMRQwEgYDVQQLDAtLZGUgY29u
            bmVjdDAeFw0xNTA2MDMxMzE0MzhaFw0yNTA2MDMxMzE0MzhaMFUxLzAtBgNVBAMMJl9kYTE3OWE5
            MV9mMDY0XzQ3OGVfYmU4Y18xOTM1ZDc1NDRkNTRfMQwwCgYDVQQKDANLREUxFDASBgNVBAsMC0tk
            ZSBjb25uZWN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzH9GxS1lctpwYdSGAoPH
            ws+MnVaL0PVDCuzrpxzXc+bChR87xofhQIesLPLZEcmUJ1MlEJ6jx4W+gVhvY2tUN7SoiKKbnq8s
            WjI5ovs5yML3C1zPbOSJAdK613FcdkK+UGd/9dQk54gIozinC58iyTAChVVpB3pAF38EPxwKkuo2
            qTzwk24d6PRxz1skkzwEphUQQzGboyHsAlJHN1MzM2/yFGB4l8iUua2d3ETyfy/xFEh/SwtGtXE5
            KLz4cpb0fxjeYQZVruBKxzE07kgDO3zOhmP3LJ/KSPHWYImd1DWmpY9iDvoXr6+V7FAnRloaEIyg
            7WwdlSCpo3TXVuIjLwIDAQABozIwMDAdBgNVHQ4EFgQUwmbHo8YbiR463GRKSLL3eIKyvDkwDwYD
            VR0TAQH/BAUwAwIBADANBgkqhkiG9w0BAQUFAAOCAQEAydijH3rbnvpBDB/30w2PCGMT7O0N/XYM
            wBtUidqa4NFumJrNrccx5Ehp4UP66BfP61HW8h2U/EekYfOsZyyWd4KnsDD6ycR8h/WvpK3BC2cn
            I299wbqCEZmk5ZFFaEIDHdLAdgMCuxJkAzy9mMrWEa05Soxi2/ZXdrU9nXo5dzuPGYlirVPDHl7r
            /urBxD6HVX3ObQJRJ7r/nAWyUVdX3/biJaDRsydftOpGU6Gi5c1JK4MWIz8Bsjh6mEjCsVatbPPl
            yygGiJbDZfAvN2XoaVEBii2GDDCWfaFwPVPYlNTvjkUkMP8YThlMsiJ8Q4693XoLOL94GpNlCfUg
            7n+KOQ==
            """.trimIndent()

        val certificateBytes = Base64.Mime.decode(encodedCertificate)
        runBlocking {
            deviceSettings.addTrustedDevice(
                DeviceInfo(
                    id = deviceId,
                    name = name,
                    type = DeviceType.PHONE,
                    protocolVersion = DeviceHelper.PROTOCOL_VERSION,
                    certificate = certificateBytes,
                )
            )
        }

        mockkObject(EcHelper)
        every { EcHelper.ensureKeyPair() } returns Unit

        mockkStatic(ContextCompat::class)
        every { ContextCompat.getSystemService(context, NotificationManager::class.java) } returns mockk(relaxed = true)

        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns Dispatchers.Unconfined

        mockkObject(PluginFactory)
        every { PluginFactory.instantiatePluginForDevice(any(), any()) } returns null

        startKoin {
            modules(
                module {
                    single { deviceSettings }
                    single { sslHelper }
                    single { mockk<DeviceHelper>(relaxed = true) }
                    factory { (deviceInfo: DeviceInfo ) ->
                        Device(get(), get(), { device -> DevicePairingCallback(device, context) }, deviceInfo)
                    }
                }
            )
        }

        sslHelper.certificate = SslHelper.parseCertificate(certificateBytes)
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkObject(PluginFactory)
        unmockkAll()
    }

    @Test
    @Throws(CertificateException::class)
    fun testDeviceInfoToIdentityPacket() {
        val deviceId = "testDevice"
        val deviceInfo = runBlocking {
            deviceSettings.getDeviceInfo(deviceId)!!.copy(
                protocolVersion = DeviceHelper.PROTOCOL_VERSION,
                incomingCapabilities = hashSetOf("kdeconnect.plugin1State", "kdeconnect.plugin2State"),
                outgoingCapabilities = hashSetOf("kdeconnect.plugin1State.request", "kdeconnect.plugin2State.request"),
            )
        }

        val networkPacket = deviceInfo.toIdentityPacket()
        Assert.assertEquals(deviceInfo.id, networkPacket.getString("deviceId"))
        Assert.assertEquals(deviceInfo.name, networkPacket.getString("deviceName"))
        Assert.assertEquals(deviceInfo.protocolVersion.toLong(), networkPacket.getInt("protocolVersion").toLong())
        Assert.assertEquals(deviceInfo.type.toString(), networkPacket.getString("deviceType"))
        Assert.assertEquals(deviceInfo.incomingCapabilities, networkPacket.getStringSet("incomingCapabilities"))
        Assert.assertEquals(deviceInfo.outgoingCapabilities, networkPacket.getStringSet("outgoingCapabilities"))
    }

    @Test
    fun testIsValidDeviceId() {
        Assert.assertTrue(isValidDeviceId("27456E3C_fE5C_4208_96A7_c0CAEEC5E5A0"))
        Assert.assertTrue(isValidDeviceId("27456e3c_fe5c_4208_96a7_c0caeec5e5a0"))
        Assert.assertTrue(isValidDeviceId("27456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertFalse(isValidDeviceId("7456e3cfe5c420896a7c0caeec5e5a0"))
        Assert.assertTrue(isValidDeviceId("_27456e3c_fe5c_4208_96a7_c0caeec5e5a0_"))
        Assert.assertTrue(isValidDeviceId("z7456e3c_fe5c_4208_96a7_c0caeec5e5a0"))
        Assert.assertFalse(isValidDeviceId(""))
        Assert.assertFalse(isValidDeviceId("______"))
        Assert.assertFalse(isValidDeviceId("____"))
        Assert.assertFalse(isValidDeviceId("potato"))
        Assert.assertFalse(isValidDeviceId("12345"))
    }

    @Test
    fun testIsValidIdentityPacket() {
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY)
        Assert.assertFalse(isValidIdentityPacket(np))

        val validName = "MyDevice"
        val validId = "27456e3c_fe5c_4208_96a7_c0caeec5e5a0"
        np["deviceName"] = validName
        np["deviceId"] = validId
        Assert.assertTrue(isValidIdentityPacket(np))

        np["deviceName"] = "    "
        Assert.assertFalse(isValidIdentityPacket(np))
        np["deviceName"] = "<><><><><><><><><>" // Only invalid characters
        Assert.assertFalse(isValidIdentityPacket(np))

        np["deviceName"] = validName
        np["deviceId"] = "    "
        Assert.assertFalse(isValidIdentityPacket(np))
    }

    @Test
    fun testDeviceType() {
        Assert.assertEquals(DeviceType.PHONE, fromString(DeviceType.PHONE.toString()))
        Assert.assertEquals(DeviceType.TABLET, fromString(DeviceType.TABLET.toString()))
        Assert.assertEquals(DeviceType.DESKTOP, fromString(DeviceType.DESKTOP.toString()))
        Assert.assertEquals(DeviceType.LAPTOP, fromString(DeviceType.LAPTOP.toString()))
        Assert.assertEquals(DeviceType.TV, fromString(DeviceType.TV.toString()))
        Assert.assertEquals(DeviceType.DESKTOP, fromString("invalid"))
    }

    // Basic paired device testing
    @Test
    fun testDevice() = runBlocking {
        val deviceInfo = deviceSettings.getDeviceInfo("testDevice")!!
        val device = Device(deviceSettings, sslHelper, { device -> DevicePairingCallback(device, context) }, deviceInfo)

        Assert.assertEquals(device.deviceId, "testDevice")
        Assert.assertEquals(device.deviceType, DeviceType.PHONE)
        Assert.assertEquals(device.name, "Test Device")
        Assert.assertTrue(device.isPaired)
        Assert.assertNotNull(device.deviceInfo.certificate)
    }

    @Test
    @Throws(CertificateException::class)
    fun testPairingDone() {
        val fakeNetworkPacket = NetworkPacket(NetworkPacket.PACKET_TYPE_IDENTITY)
        val deviceId = "unpairedTestDevice"
        fakeNetworkPacket["deviceId"] = deviceId
        fakeNetworkPacket["deviceName"] = "Unpaired Test Device"
        fakeNetworkPacket["protocolVersion"] = DeviceHelper.PROTOCOL_VERSION
        fakeNetworkPacket["deviceType"] = DeviceType.PHONE.toString()
        val certificateString = """
            MIIDVzCCAj+gAwIBAgIBCjANBgkqhkiG9w0BAQUFADBVMS8wLQYDVQQDDCZfZGExNzlhOTFfZjA2
            NF80NzhlX2JlOGNfMTkzNWQ3NTQ0ZDU0XzEMMAoGA1UECgwDS0RFMRQwEgYDVQQLDAtLZGUgY29u
            bmVjdDAeFw0xNTA2MDMxMzE0MzhaFw0yNTA2MDMxMzE0MzhaMFUxLzAtBgNVBAMMJl9kYTE3OWE5
            MV9mMDY0XzQ3OGVfYmU4Y18xOTM1ZDc1NDRkNTRfMQwwCgYDVQQKDANLREUxFDASBgNVBAsMC0tk
            ZSBjb25uZWN0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzH9GxS1lctpwYdSGAoPH
            ws+MnVaL0PVDCuzrpxzXc+bChR87xofhQIesLPLZEcmUJ1MlEJ6jx4W+gVhvY2tUN7SoiKKbnq8s
            WjI5ovs5yML3C1zPbOSJAdK613FcdkK+UGd/9dQk54gIozinC58iyTAChVVpB3pAF38EPxwKkuo2
            qTzwk24d6PRxz1skkzwEphUQQzGboyHsAlJHN1MzM2/yFGB4l8iUua2d3ETyfy/xFEh/SwtGtXE5
            KLz4cpb0fxjeYQZVruBKxzE07kgDO3zOhmP3LJ/KSPHWYImd1DWmpY9iDvoXr6+V7FAnRloaEIyg
            7WwdlSCpo3TXVuIjLwIDAQABozIwMDAdBgNVHQ4EFgQUwmbHo8YbiR463GRKSLL3eIKyvDkwDwYD
            VR0TAQH/BAUwAwIBADANBgkqhkiG9w0BAQUFAAOCAQEAydijH3rbnvpBDB/30w2PCGMT7O0N/XYM
            wBtUidqa4NFumJrNrccx5Ehp4UP66BfP61HW8h2U/EekYfOsZyyWd4KnsDD6ycR8h/WvpK3BC2cn
            I299wbqCEZmk5ZFFaEIDHdLAdgMCuxJkAzy9mMrWEa05Soxi2/ZXdrU9nXo5dzuPGYlirVPDHl7r
            /urBxD6HVX3ObQJRJ7r/nAWyUVdX3/biJaDRsydftOpGU6Gi5c1JK4MWIz8Bsjh6mEjCsVatbPPl
            yygGiJbDZfAvN2XoaVEBii2GDDCWfaFwPVPYlNTvjkUkMP8YThlMsiJ8Q4693XoLOL94GpNlCfUg
            7n+KOQ==
            """.trimIndent()
        val certificateBytes = Base64.Mime.decode(certificateString)
        val certificate = SslHelper.parseCertificate(certificateBytes)
        val deviceInfo = fromIdentityPacketAndCert(fakeNetworkPacket, certificate)

        val linkProvider = mockk<LanLinkProvider>()
        every { linkProvider.name } returns "LanLinkProvider"
        val link = mockk<LanLink>()
        every { link.linkProvider } returns linkProvider
        every { link.deviceId } returns deviceId
        every { link.deviceInfo } returns deviceInfo
        every { link.addPacketReceiver(any()) } returns Unit
        val device = Device(deviceSettings, sslHelper, { device -> DevicePairingCallback(device, context) }, deviceInfo)
        device.addLink(link)

        Assert.assertNotNull(device)
        Assert.assertEquals(device.deviceId, deviceId)
        Assert.assertEquals(device.name, "Unpaired Test Device")
        Assert.assertEquals(device.deviceType, DeviceType.PHONE)
        Assert.assertNotNull(device.deviceInfo.certificate)

        device.pairingHandler.pairingDone()

        Assert.assertTrue(device.isPaired)

        Assert.assertTrue(runBlocking { deviceSettings.isTrustedDevice(device.deviceId) })

        val deviceInfoInSettings = runBlocking { deviceSettings.getDeviceInfo(device.deviceId) }
        Assert.assertNotNull(deviceInfoInSettings)
        Assert.assertEquals(deviceInfoInSettings?.name, "Unpaired Test Device")
        Assert.assertEquals(deviceInfoInSettings?.type, DeviceType.PHONE)

        runBlocking { deviceSettings.removeTrustedDevice(device.deviceId) }
    }

    @Test
    @Throws(CertificateException::class)
    fun testUnpair() = runBlocking {
        val deviceInfo = deviceSettings.getDeviceInfo("testDevice")!!
        val device = Device(deviceSettings, sslHelper, { device -> DevicePairingCallback(device, context) }, deviceInfo)

        device.unpair()

        Assert.assertEquals(PairState.NotPaired, device.state.value.pairState)

        var isTrusted = true
        repeat(10) {
            isTrusted = deviceSettings.isTrustedDevice(device.deviceId)
            if (!isTrusted) return@repeat
            delay(100.milliseconds)
        }
        Assert.assertFalse("Device should be removed from trusted list", isTrusted)
    }
}
