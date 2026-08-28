/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.lan

import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.Sink
import okio.Source
import okio.sink
import okio.source
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.NetworkPacket.Companion.unserialize
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.SendPacketStatusCallback
import org.kde.kdeconnect.helpers.LineTooLongException
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.ProgressSink
import org.kde.kdeconnect.helpers.readLineBounded
import org.kde.kdeconnect.helpers.security.SslHelper
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.channels.NotYetConnectedException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.text.Charsets.UTF_8

class LanLink(
    override var deviceInfo: DeviceInfo,
    linkProvider: BaseLinkProvider,
    socket: SSLSocket,
    private val sslHelper: SslHelper,
) : BaseLink(linkProvider) {
    enum class ConnectionStarted {
        Locally, Remotely
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var socket: SSLSocket? = null
    override val name: String = "LanLink"

    override suspend fun disconnect() {
        LoggerTagged.i { "socket:" + socket.hashCode() }
        try {
            withContext(Dispatchers.IO) {
                socket?.close()
            }
            scope.cancel()
        } catch (e: IOException) {
            LoggerTagged.e(e) { "Error" }
        }
    }

    //Returns the old socket
    @WorkerThread
    @Throws(IOException::class)
    fun reset(newSocket: SSLSocket, deviceInfo: DeviceInfo): SSLSocket? {
        this.deviceInfo = deviceInfo

        val oldSocket = socket
        socket = newSocket

        oldSocket?.close()

        //Create a thread to take care of incoming data for the new socket
        scope.launch {
            try {
                val stream = newSocket.getInputStream().buffered()
                while (true) {
                    val packet = try {
                        readLineBounded(stream, MAX_PACKET_SIZE)
                    } catch (_: LineTooLongException) {
                        continue
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    if (packet.isEmpty()) {
                        continue
                    }
                    val np = unserialize(packet)
                    receivedNetworkPacket(np)
                }
            } catch (e: Exception) {
                LoggerTagged.i { "Socket closed: " + newSocket.hashCode() + ". Reason: " + e.message }
                try {
                    Thread.sleep(300)
                } catch (_: InterruptedException) {
                } // Wait a bit because we might receive a new socket meanwhile

                val thereIsaANewSocket = (newSocket !== socket)
                if (!thereIsaANewSocket) {
                    LoggerTagged.i { "Socket closed and there's no new socket, disconnecting device" }
                    linkProvider.onConnectionLost(this@LanLink)
                }
            }
        }

        return oldSocket
    }

    init {
        reset(socket, deviceInfo)
    }

    @WorkerThread
    override suspend fun sendPacket(
        np: NetworkPacket,
        callback: SendPacketStatusCallback,
    ): Boolean {
        if (socket == null) {
            LoggerTagged.e { "Not yet connected" }
            callback.onFailure(NotYetConnectedException())
            return false
        }

        try {
            //Prepare socket for the payload

            val server: ServerSocket? = if (np.hasPayload()) {
                val newServer = LanLinkProvider.openServerSocketOnFreePort(LanLinkProvider.PAYLOAD_TRANSFER_MIN_PORT)
                val payloadTransferInfo = JsonObject(mapOf("port" to JsonPrimitive(newServer.localPort)))
                np.payloadTransferInfo = payloadTransferInfo
                newServer
            } else {
                null
            }

            //Send body of the network packet
            withContext(Dispatchers.IO) {
                try {
                    val writer = socket?.getOutputStream()
                    writer?.write(np.serialize().toByteArray(UTF_8))
                    writer?.flush()
                } catch (e: Exception) {
                    disconnect() //main socket is broken, disconnect
                    if (server != null) {
                        try {
                            server.close()
                        } catch (_: Exception) {
                        }
                    }
                    throw e
                }
            }

            //Send payload
            @OptIn(ExperimentalAtomicApi::class)
            if (server != null && !np.isCanceled.load()) {
                withContext(Dispatchers.IO) {
                    sendPayload(np, callback, server)
                }
            }

            @OptIn(ExperimentalAtomicApi::class)
            if (!np.isCanceled.load()) {
                callback.onSuccess()
            }
            return true
        } catch (e: Exception) {
            callback.onFailure(e)
            return false
        } finally {
            //Make sure we close the payload stream, if any
            if (np.hasPayload()) {
                np.payload?.close()
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    @Throws(IOException::class)
    private suspend fun sendPayload(
        np: NetworkPacket,
        callback: SendPacketStatusCallback,
        server: ServerSocket
    ) = withContext(Dispatchers.IO) {
        var payloadSocket: Socket? = null
        var outputStream: Sink? = null
        val inputStream: Source?
        try {
            //Wait a maximum of 10 seconds for the other end to establish a connection with our socket, close it afterwards
            server.soTimeout = 10 * 1000


            payloadSocket = server.accept()

            //Convert to SSL if needed
            payloadSocket =
                sslHelper.convertToSslSocket(payloadSocket!!, deviceInfo,
                    isDeviceTrusted = true,
                    clientMode = false,
                )

            val rawOutputStream = payloadSocket.getOutputStream().sink()
            val progressSink = ProgressSink(
                delegate = rawOutputStream,
                totalPayloadSize = np.payloadSize,
                isCancelled = { np.isCanceled.load() },
                setProgress = { callback.onPayloadProgressChanged(it) }
            )
            outputStream = progressSink
            inputStream = np.payload!!.source

            LoggerTagged.i { "Beginning to send payload for " + np.type }
            val buffer = Buffer()
            var bytesRead = -1L
            while ((inputStream!!.read(buffer, 4096).also { bytesRead = it }) != -1L) {
                outputStream.write(buffer, bytesRead)
            }
            outputStream.flush()
            LoggerTagged.i { "Finished sending payload (${progressSink.getWritten()} bytes written)" }
        } catch (e: SocketTimeoutException) {
            LoggerTagged.e(e) {
                "Socket for payload in packet " + np.type + " timed out. The other end didn't fetch the payload."
            }
        } catch (e: CertificateException) {
            // The exception can be due to several causes. "Connection closed by peer" seems to be a common one.
            // If we could distinguish different cases we could react differently for some of them, but I haven't found how.
            LoggerTagged.e(e) { "Payload SSLSocket failed" }
        } catch (e: SSLHandshakeException) {
            LoggerTagged.e(e) { "Payload SSLSocket failed" }
        } catch (e: Exception) {
            if (e.message == "Cancelled") {
                LoggerTagged.i { "Payload sending for ${np.type} was cancelled" }
            } else {
                throw e
            }
        } finally {
            try {
                server.close()
            } catch (_: Exception) {
            }
            try {
                payloadSocket?.close()
            } catch (_: Exception) {
            }
            np.payload?.close()
            try {
                outputStream?.close()
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun receivedNetworkPacket(np: NetworkPacket) {
        if (np.hasPayloadTransferInfo()) {
            var payloadSocket = Socket()
            try {
                val tcpPort = np.payloadTransferInfo?.get("port")?.jsonPrimitive?.int ?: return
                val deviceAddress = socket?.remoteSocketAddress as InetSocketAddress
                withContext(Dispatchers.IO) {
                    payloadSocket.connect(InetSocketAddress(deviceAddress.address, tcpPort))
                }
                payloadSocket =
                    sslHelper.convertToSslSocket(payloadSocket, deviceInfo,
                        isDeviceTrusted = true,
                        clientMode = true,
                    )
                withContext(Dispatchers.IO) {
                    np.payload = NetworkPacket.Payload(payloadSocket.getInputStream().source(), np.payloadSize) {
                        payloadSocket.close()
                    }
                }
            } catch (e: Exception) {
                try {
                    withContext(Dispatchers.IO) {
                        payloadSocket.close()
                    }
                } catch (_: Exception) {
                }
                LoggerTagged.e(e) { "Exception connecting to payload remote socket" }
            }
        }

        packetReceived(np)
    }

    companion object {
        const val MAX_PACKET_SIZE: Int = 32 * 1024 * 1024
    }
}
