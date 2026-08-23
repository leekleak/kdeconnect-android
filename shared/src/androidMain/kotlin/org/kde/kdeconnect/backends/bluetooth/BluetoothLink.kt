/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.bluetooth

import android.bluetooth.BluetoothDevice
import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.helpers.LoggerTagged
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class BluetoothLink(
    private val connection: ConnectionMultiplexer,
    val input: BufferedSource,
    val output: BufferedSink,
    val remoteAddress: BluetoothDevice,
    override val deviceInfo: DeviceInfo,
    override val linkProvider: BluetoothLinkProvider
) : BaseLink(linkProvider) {
    private var continueAccepting = true
    private val receivingThread = Thread(object : Runnable {
        override fun run() {
            try {
                while (continueAccepting) {
                    val message = input.readUtf8Line()
                    if (message == null) {
                        runBlocking { disconnect() }
                        return
                    }
                    if (!continueAccepting) break
                    runBlocking {
                        processMessage(message)
                    }
                }
            } catch (e: IOException) {
                LoggerTagged.e(e) { "Connection to " + remoteAddress.address + " likely broken." }
                runBlocking { disconnect() }
            }
        }

        private suspend fun processMessage(message: String) {
            val np = try {
                NetworkPacket.unserialize(message)
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Unable to parse message." }
                return
            }
            np.payloadTransferInfo?.let { transferInfo ->
                try {
                    val uuidString = transferInfo["uuid"]?.jsonPrimitive?.content
                    if (uuidString != null) {
                        val transferUuid = UUID.fromString(uuidString)
                        val payloadInputStream = connection.getChannelSource(transferUuid)
                        np.payload = NetworkPacket.Payload(payloadInputStream, np.payloadSize)
                    }
                } catch (e: Exception) {
                    LoggerTagged.e(e) { "Unable to get payload" }
                }
            }
            packetReceived(np)
        }
    })

    fun startListening() {
        receivingThread.start()
    }

    override val name: String = "BluetoothLink"

    override suspend fun disconnect() {
        continueAccepting = false
        try {
            connection.close()
        } catch (_: IOException) {
        }
        linkProvider.disconnectedLink(this, remoteAddress)
    }

    @Throws(IOException::class)
    private fun sendMessage(np: NetworkPacket) {
        output.writeUtf8(np.serialize() + "\n")
        output.flush()
    }

    @WorkerThread
    @Throws(IOException::class)
    override suspend fun sendPacket(np: NetworkPacket, callback: Device.SendPacketStatusCallback): Boolean {
        // sendPayloadFromSameThread is ignored, we always send from the same thread!

        return try {
            var transferUuid: UUID? = null
            if (np.hasPayload()) {
                transferUuid = connection.newChannel()
                val payloadTransferInfo = JsonObject(mapOf("uuid" to JsonPrimitive(transferUuid.toString())))
                np.payloadTransferInfo = payloadTransferInfo
            }
            sendMessage(np)
            if (transferUuid != null) {
                try {
                    connection.getChannelSink(transferUuid).buffer().use { payloadSink ->
                        val bufferLength = 1024L
                        var progress: Long = 0
                        val source = np.payload!!.source!!
                        val buffer = Buffer()
                        @OptIn(ExperimentalAtomicApi::class)
                        while (!np.isCanceled.load()) {
                            val bytesRead = source.read(buffer, bufferLength)
                            if (bytesRead == -1L) break
                            payloadSink.write(buffer, bytesRead)
                            payloadSink.emitCompleteSegments()
                            progress += bytesRead
                            if (np.payloadSize > 0) {
                                callback.onPayloadProgressChanged((100 * progress / np.payloadSize).toInt())
                            }
                        }
                        payloadSink.flush()
                    }
                } catch (e: Exception) {
                    callback.onFailure(e)
                    return false
                }
            }
            @OptIn(ExperimentalAtomicApi::class)
            if (!np.isCanceled.load()) {
                callback.onSuccess()
            }
            true
        } catch (e: Exception) {
            callback.onFailure(e)
            false
        }
    }
}
