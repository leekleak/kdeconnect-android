/*
 * SPDX-FileCopyrightText: 2024 TPJ Schikhof <kde@schikhof.eu>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.security.SslHelper
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64

@RunWith(AndroidJUnit4::class)
class SSLHelperTest {
    private lateinit var deviceSettings: DeviceSettings
    private lateinit var db: DevicesRoomDatabase
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private lateinit var sslHelper: SslHelper
    private val certificateBase64 = """
        MIIBkzCCATmgAwIBAgIBATAKBggqhkjOPQQDBDBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAzXzRl
        Y2NfOTI2MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANLREUw
        HhcNMjMwOTE1MjIwMDAwWhcNMzQwOTE1MjIwMDAwWjBTMS0wKwYDVQQDDCRlZTA2MWE3NV9lNDAz
        XzRlY2NfOTI2MV81ZmZlMjcyMmY2OTgxFDASBgNVBAsMC0tERSBDb25uZWN0MQwwCgYDVQQKDANL
        REUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAASqOIKTm5j6x8DKgYSkItLmjCgIXP0gkOW6bmVv
        loDGsYnvqYLMFGe7YW8g8lT/qPBTEfDOM4UpQ8X6jidE+XrnMAoGCCqGSM49BAMEA0gAMEUCIEpk
        6VNpbt3tfbWDf0TmoJftRq3wAs3Dke7d5vMZlivyAiEA/ZXtSRqPjs/2RN9SynKhSUA9/z0PNq6L
        YoAaC6TdomM=
        """.trimIndent().replace("\n", "\r\n") // the mime encoder adds \r\n line endings
    private val certificateHash = "fc:1f:b3:d3:d3:3b:23:42:e4:5c:74:b1:a6:13:dc:df:e5:e1:f0:29:d6:68:24:9f:50:49:52:a9:a8:04:1e:31:"
    private val deviceId = "testDevice"
    private val certificateKey = "certificate"

    @Before
    fun setup() {
        val realContext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(realContext, DevicesRoomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        deviceSettings = DeviceSettings(db.deviceDao())
        sslHelper = SslHelper(settingsDataStore, deviceSettings)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCertificateStored() {
        val cert = Base64.Mime.decode(certificateBase64)
        runBlocking {
            deviceSettings.addTrustedDevice(
                DeviceInfo(deviceId, cert, "name", DeviceType.PHONE, 0)
            )
        }
        Assert.assertTrue(runBlocking { deviceSettings.getDeviceInfo(deviceId)?.certificate.contentEquals(cert) })
        runBlocking { deviceSettings.removeTrustedDevice(deviceId) }
        Assert.assertFalse(runBlocking { deviceSettings.getDeviceInfo(deviceId)?.certificate.contentEquals(cert) })
    }

    @Test
    fun getAnyCertificate() {
        Assert.assertNull( runBlocking { deviceSettings.getDeviceInfo(deviceId)?.certificate } )
        runBlocking {
            deviceSettings.addTrustedDevice(
                DeviceInfo(deviceId, Base64.Mime.decode(certificateBase64), "name", DeviceType.PHONE, 0)
            )
        }
        Assert.assertNotNull(runBlocking { deviceSettings.getDeviceInfo(deviceId)?.certificate })
    }

    @Test
    fun getExpectedCertificate() {
        runBlocking {
            deviceSettings.addTrustedDevice(
                DeviceInfo(deviceId, Base64.Mime.decode(certificateBase64), "name", DeviceType.PHONE, 0)
            )
        }
        val cert: ByteArray = runBlocking { deviceSettings.getDeviceInfo(deviceId)!!.certificate }
        Assert.assertEquals(certificateBase64, Base64.Mime.encode(cert))
    }

    @Test
    fun getCertificateHash() {
        runBlocking {
            deviceSettings.addTrustedDevice(
                DeviceInfo(deviceId, Base64.Mime.decode(certificateBase64), "name", DeviceType.PHONE, 0)
            )
        }
        val certificateBytes: ByteArray? = runBlocking { deviceSettings.getDeviceInfo(deviceId)?.certificate }
        val certificate = sslHelper.parseCertificate(certificateBytes!!)
        val hash = sslHelper.getCertificateHash(certificate)
        Assert.assertEquals(certificateHash, hash)
    }

    @Test
    fun parseCertificate() {
        val bytes = Base64.Mime.decode(certificateBase64)
        val cert = sslHelper.parseCertificate(bytes)
        val hash = sslHelper.getCertificateHash(cert)
        Assert.assertEquals(certificateHash, hash)
    }

    @Test
    fun getCommonName() {
        runBlocking {
            deviceSettings.addTrustedDevice(
                DeviceInfo(deviceId, Base64.Mime.decode(certificateBase64), "name", DeviceType.PHONE, 0)
            )
        }
        val cert: Certificate = runBlocking { sslHelper.parseCertificate(deviceSettings.getDeviceInfo(deviceId)?.certificate!!) }
        val commonName = sslHelper.getCommonNameFromCertificate(cert as X509Certificate)
        Assert.assertEquals("ee061a75_e403_4ecc_9261_5ffe2722f698", commonName)
    }

}
