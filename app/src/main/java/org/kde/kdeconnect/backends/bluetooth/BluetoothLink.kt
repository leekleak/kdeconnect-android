/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.WorkerThread
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.helpers.LoggerTagged
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.Reader
import java.util.UUID
import kotlin.text.Charsets.UTF_8

class BluetoothLink(
    context: Context,
    private val connection: ConnectionMultiplexer,
    val input: InputStream,
    val output: OutputStream,
    val remoteAddress: BluetoothDevice,
    override val deviceInfo: DeviceInfo,
    override val linkProvider: BluetoothLinkProvider
) : BaseLink(context, linkProvider) {
    private var continueAccepting = true
    private val receivingThread = Thread(object : Runnable {
        override fun run() {
            val sb = StringBuilder()
            try {
                val reader: Reader = InputStreamReader(input, UTF_8)
                val buf = CharArray(512)
                while (continueAccepting) {
                    while (sb.indexOf("\n") == -1 && continueAccepting) {
                        var charsRead: Int
                        if (reader.read(buf).also { charsRead = it } > 0) {
                            sb.appendRange(buf, 0, charsRead)
                        }
                        if (charsRead < 0) {
                            runBlocking { disconnect() }
                            return
                        }
                    }
                    if (!continueAccepting) break
                    val endIndex = sb.indexOf("\n")
                    if (endIndex != -1) {
                        val message = sb.substring(0, endIndex + 1)
                        sb.delete(0, endIndex + 1)
                        runBlocking {
                            processMessage(message)
                        }
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
            } catch (e: JSONException) {
                LoggerTagged.e(e) { "Unable to parse message." }
                return
            }
            if (np.hasPayloadTransferInfo()) {
                try {
                    val transferUuid = UUID.fromString(np.payloadTransferInfo.getString("uuid"))
                    val payloadInputStream = connection.getChannelInputStream(transferUuid)
                    np.payload = NetworkPacket.Payload(payloadInputStream, np.payloadSize)
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

    @Throws(JSONException::class, IOException::class)
    private fun sendMessage(np: NetworkPacket) {
        val message = np.serialize().toByteArray(UTF_8)
        output.write(message)
    }

    @WorkerThread
    @Throws(IOException::class)
    override suspend fun sendPacket(np: NetworkPacket, callback: Device.SendPacketStatusCallback): Boolean {
        // sendPayloadFromSameThread is ignored, we always send from the same thread!

        return try {
            var transferUuid: UUID? = null
            if (np.hasPayload()) {
                transferUuid = connection.newChannel()
                val payloadTransferInfo = JSONObject()
                payloadTransferInfo.put("uuid", transferUuid.toString())
                np.payloadTransferInfo = payloadTransferInfo
            }
            sendMessage(np)
            if (transferUuid != null) {
                try {
                    connection.getChannelOutputStream(transferUuid).use { payloadStream ->
                        val bufferLength = 1024
                        val buffer = ByteArray(bufferLength)
                        var bytesRead: Int
                        var progress: Long = 0
                        val stream = np.payload!!.inputStream!!
                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            progress += bytesRead.toLong()
                            payloadStream.write(buffer, 0, bytesRead)
                            if (np.payloadSize > 0) {
                                callback.onPayloadProgressChanged((100 * progress / np.payloadSize).toInt())
                            }
                        }
                        payloadStream.flush()
                    }
                } catch (e: Exception) {
                    callback.onFailure(e)
                    return false
                }
            }
            callback.onSuccess()
            true
        } catch (e: Exception) {
            callback.onFailure(e)
            false
        }
    }
}
