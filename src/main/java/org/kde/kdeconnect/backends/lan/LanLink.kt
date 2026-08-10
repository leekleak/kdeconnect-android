/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.lan

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.NetworkPacket.Companion.unserialize
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.helpers.LineTooLongException
import org.kde.kdeconnect.helpers.readLineBounded
import org.kde.kdeconnect.helpers.security.SslHelper
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.channels.NotYetConnectedException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.Volatile
import kotlin.text.Charsets.UTF_8

class LanLink @WorkerThread constructor(
    context: Context,
    override var deviceInfo: DeviceInfo,
    linkProvider: BaseLinkProvider,
    socket: SSLSocket,
    private val sslHelper: SslHelper,
) : BaseLink(context, linkProvider) {
    enum class ConnectionStarted {
        Locally, Remotely
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var socket: SSLSocket? = null
    override val name: String = "LanLink"

    override suspend fun disconnect() {
        Log.i("LanLink/Disconnect", "socket:" + socket.hashCode())
        try {
            withContext(Dispatchers.IO) {
                socket?.close()
            }
            scope.cancel()
        } catch (e: IOException) {
            Log.e("LanLink", "Error", e)
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
                Log.i(
                    "LanLink",
                    "Socket closed: " + newSocket.hashCode() + ". Reason: " + e.message
                )
                try {
                    Thread.sleep(300)
                } catch (_: InterruptedException) {
                } // Wait a bit because we might receive a new socket meanwhile

                val thereIsaANewSocket = (newSocket !== socket)
                if (!thereIsaANewSocket) {
                    Log.i(
                        "LanLink",
                        "Socket closed and there's no new socket, disconnecting device"
                    )
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
        callback: Device.SendPacketStatusCallback,
    ): Boolean {
        if (socket == null) {
            Log.e("KDE/sendPacket", "Not yet connected")
            callback.onFailure(NotYetConnectedException())
            return false
        }

        Log.e("SendPacket", "Stop 1")

        try {
            //Prepare socket for the payload

            val server: ServerSocket? = if (np.hasPayload()) {
                val newServer = LanLinkProvider.openServerSocketOnFreePort(LanLinkProvider.PAYLOAD_TRANSFER_MIN_PORT)
                val payloadTransferInfo = JSONObject()
                payloadTransferInfo.put("port", newServer.localPort)
                np.payloadTransferInfo = payloadTransferInfo
                newServer
            } else {
                null
            }
            Log.e("SendPacket", "Stop 2")

            //Log.e("LanLink/sendPacket", np.getType());

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
            Log.e("SendPacket", "Stop 3")

            //Send payload
            if (server != null) {
                try {
                    sendPayload(np, callback, server)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.e(
                        "LanLink/sendPacket",
                        "Async sendPayload failed for packet of type " + np.type + ". The Plugin was NOT notified."
                    )
                }
            }

            Log.e("SendPacket", "Stop 4")
            if (!np.isCanceled) {
                callback.onSuccess()
            }
            return true
        } catch (e: Exception) {
            callback.onFailure(e)
            return false
        } finally {
            Log.e("SendPacket", "Stop 5")
            //Make sure we close the payload stream, if any
            if (np.hasPayload()) {
                np.payload?.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun sendPayload(
        np: NetworkPacket,
        callback: Device.SendPacketStatusCallback,
        server: ServerSocket
    ) {
        var payloadSocket: Socket? = null
        var outputStream: OutputStream? = null
        val inputStream: InputStream?
        try {
            if (!np.isCanceled) {
                //Wait a maximum of 10 seconds for the other end to establish a connection with our socket, close it afterwards
                server.soTimeout = 10 * 1000

                payloadSocket = server.accept()

                //Convert to SSL if needed
                payloadSocket =
                    sslHelper.convertToSslSocket(context, payloadSocket!!, deviceInfo,
                        isDeviceTrusted = true,
                        clientMode = false,
                    )

                outputStream = payloadSocket.getOutputStream()
                inputStream = np.payload!!.inputStream

                Log.i("KDE/LanLink", "Beginning to send payload for " + np.type)
                val buffer = ByteArray(4096)
                var bytesRead: Int = -1
                val size = np.payloadSize
                var progress: Long = 0
                var timeSinceLastUpdate: Long = -1
                while (!np.isCanceled && (inputStream!!.read(buffer).also { bytesRead = it }) != -1) {
                    progress += bytesRead.toLong()
                    outputStream.write(buffer, 0, bytesRead)
                    if (size > 0) {
                        if (timeSinceLastUpdate + 500 < System.currentTimeMillis()) { //Report progress every half a second
                            val percent = ((100 * progress) / size)
                            callback.onPayloadProgressChanged(percent.toInt())
                            timeSinceLastUpdate = System.currentTimeMillis()
                        }
                    }
                }
                outputStream.flush()
                Log.i("KDE/LanLink", "Finished sending payload ($progress bytes written)")
            }
        } catch (_: SocketTimeoutException) {
            Log.e(
                "LanLink",
                "Socket for payload in packet " + np.type + " timed out. The other end didn't fetch the payload."
            )
        } catch (e: CertificateException) {
            // The exception can be due to several causes. "Connection closed by peer" seems to be a common one.
            // If we could distinguish different cases we could react differently for some of them, but I haven't found how.
            Log.e("sendPacket", "Payload SSLSocket failed")
            e.printStackTrace()
        } catch (e: SSLHandshakeException) {
            Log.e("sendPacket", "Payload SSLSocket failed")
            e.printStackTrace()
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
                val tcpPort = np.payloadTransferInfo.getInt("port")
                val deviceAddress = socket?.remoteSocketAddress as InetSocketAddress
                withContext(Dispatchers.IO) {
                    payloadSocket.connect(InetSocketAddress(deviceAddress.address, tcpPort))
                }
                payloadSocket =
                    sslHelper.convertToSslSocket(context, payloadSocket, deviceInfo,
                        isDeviceTrusted = true,
                        clientMode = true,
                    )
                np.payload = NetworkPacket.Payload(payloadSocket, np.payloadSize)
            } catch (e: Exception) {
                try {
                    withContext(Dispatchers.IO) {
                        payloadSocket.close()
                    }
                } catch (_: Exception) {
                }
                Log.e("KDE/LanLink", "Exception connecting to payload remote socket", e)
            }
        }

        Log.e("Receiving", np.type)
        packetReceived(np)
    }

    companion object {
        const val MAX_PACKET_SIZE: Int = 32 * 1024 * 1024
    }
}
