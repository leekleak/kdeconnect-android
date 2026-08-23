/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.put
import org.junit.Assert
import org.junit.Test
import org.kde.kdeconnect.NetworkPacket.Companion.unserialize
import java.security.cert.Certificate
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class NetworkPacketTest {

    @Test
    fun testNetworkPacket() {
        var np = NetworkPacket("com.test").update {
            put("hello", "hola")
        }

        Assert.assertEquals(np.getString("hello", "bye"), "hola")

        np = NetworkPacket("com.test").update {
            put("hello", "")
        }
        Assert.assertEquals(np.getString("hello", "bye"), "")

        Assert.assertEquals(np.getString("hi", "bye"), "bye")

        np = NetworkPacket("com.test").update {
            put("foo", "bar")
        }
        val serialized = np.serialize()
        var np2 = unserialize(serialized)

        Assert.assertEquals(np.getLong("id"), np2.getLong("id"))
        Assert.assertEquals(np.getString("type"), np2.getString("type"))
        Assert.assertEquals(np.getJsonArray("body"), np2.getJsonArray("body"))

        val json = "{\"type\":\"test\",\"body\":{\"testing\":true}}"
        np2 = unserialize(json)
        Assert.assertTrue(np2.getBoolean("testing") == true)
        Assert.assertNull(np2.getBoolean("not_testing"))
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    fun testCancellation() {
        val np = NetworkPacket("com.test")
        Assert.assertFalse(np.isCanceled.load())
        np.cancel()
        Assert.assertTrue(np.isCanceled.load())
    }

    @Test
    fun testIdentity() {
        val cert = mockk<Certificate>()
        every { cert.encoded } returns ByteArray(0)
        val deviceInfo = DeviceInfo("myid", ByteArray(0), "myname", DeviceType.TV, 12, setOf("ASDFG"), setOf("QWERTY"))

        val np = deviceInfo.toIdentityPacket()

        Assert.assertEquals(np.getInt("protocolVersion"), 12)

        val parsed = DeviceInfo.fromIdentityPacketAndCert(np, cert)

        Assert.assertEquals(parsed.name, deviceInfo.name)
        Assert.assertEquals(parsed.id, deviceInfo.id)
        Assert.assertEquals(parsed.type, deviceInfo.type)
        Assert.assertEquals(parsed.protocolVersion.toLong(), deviceInfo.protocolVersion.toLong())
        Assert.assertEquals(parsed.incomingCapabilities, deviceInfo.incomingCapabilities)
        Assert.assertEquals(parsed.outgoingCapabilities, deviceInfo.outgoingCapabilities)
    }
}
