/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.lan

import android.content.Context
import android.net.Network
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONException
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceInfo.Companion.fromIdentityPacketAndCert
import org.kde.kdeconnect.DeviceInfo.Companion.isValidIdentityPacket
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.NetworkPacket.Companion.unserialize
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.lan.LanLink.ConnectionStarted
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.helpers.isPrivateAddress
import org.kde.kdeconnect.helpers.readLineBounded
import org.kde.kdeconnect.helpers.security.SslHelper
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import javax.net.SocketFactory
import javax.net.ssl.HandshakeCompletedEvent
import javax.net.ssl.SSLSocket
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * This LanLinkProvider creates [LanLink]s to other devices on the same
 * WiFi network. The first packet sent over a socket must be an
 * [DeviceInfo.toIdentityPacket].
 * 
 * @see .identityPacketReceived
 */
class LanLinkProvider(
    private val context: Context,
    private val deviceHelper: DeviceHelper,
    private val trustedNetworkHelper: TrustedNetworkHelper,
    private val deviceSettings: DeviceSettings,
    private val customDevicesHelper: CustomDevicesHelper,
    private val sslHelper: SslHelper
) : BaseLinkProvider() {

    val visibleDevices: HashMap<String?, LanLink?> = HashMap() // Links by device id

    val lastConnectionTimeByDeviceId: ConcurrentHashMap<String, Long> =
        ConcurrentHashMap<String, Long>()
    val lastConnectionTimeByIp: ConcurrentHashMap<InetAddress, Long> =
        ConcurrentHashMap<InetAddress, Long>()

    private var tcpServer: ServerSocket? = null
    private var udpServer: DatagramSocket? = null

    private val mdnsDiscovery: MdnsDiscovery = MdnsDiscovery(context, this, deviceHelper)

    private var lastBroadcast: Long = 0
    private var listening = false

    private var scope: CoroutineScope? = null

    override suspend fun onConnectionLost(link: BaseLink) {
        val deviceId = link.deviceId
        visibleDevices.remove(deviceId)
        super.onConnectionLost(link)
    }

    suspend fun unserializeReceivedIdentityPacket(message: String): Pair<NetworkPacket, Boolean?>? = withContext(Dispatchers.IO) {
        val identityPacket: NetworkPacket?
        try {
            identityPacket = unserialize(message)
        } catch (e: JSONException) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received: " + e.message)
            return@withContext null
        }

        if (!isValidIdentityPacket(identityPacket)) {
            Log.w("KDE/LanLinkProvider", "Invalid identity packet received.")
            return@withContext null
        }

        val deviceId = identityPacket.getString("deviceId")
        val myId = deviceHelper.getDeviceId()
        if (deviceId == myId) {
            //Ignore my own broadcast
            return@withContext null
        }

        if (rateLimitByDeviceId(deviceId)) {
            Log.i(
                "LanLinkProvider",
                "Discarding second packet from the same device $deviceId received too quickly"
            )
            return@withContext null
        }

        val deviceTrusted = deviceSettings.isTrustedDevice(deviceId)
        if (!deviceTrusted && !trustedNetworkHelper.getIsTrustedNetwork()) {
            Log.i(
                "KDE/LanLinkProvider",
                "Ignoring identity packet because the device is not trusted and I'm not on a trusted network."
            )
            return@withContext null
        }

        return@withContext Pair<NetworkPacket, Boolean?>(identityPacket, deviceTrusted)
    }

    //They received my UDP broadcast and are connecting to me. The first thing they send should be their identity packet.
    @WorkerThread
    @Throws(IOException::class, CertificateException::class)
    private suspend fun tcpPacketReceived(socket: Socket) = withContext(Dispatchers.IO) {
        val address = socket.inetAddress

        if (!isPrivateAddress(address)) {
            Log.i("LanLinkProvider", "Discarding TCP packet from a non-local IP")
            return@withContext
        }

        if (rateLimitByIp(address)) {
            Log.i(
                "LanLinkProvider",
                "Discarding second TCP packet from the same ip $address received too quickly"
            )
            return@withContext
        }

        val message: String?
        try {
            // We don't use a BufferedInputStream on purpose, since BufferedReader reads ahead and would require
            // us to keep a single BufferedInputStream instance and pass it around to make sure we don't lose data.
            // This means we are readying byte by byte directly from the OS, which is slow, but only for the handshake.
            message = readLineBounded(socket.getInputStream(), MAX_IDENTITY_PACKET_SIZE)
            //Log.e("TcpListener", "Received TCP packet: " + message);
        } catch (e: Exception) {
            Log.e("KDE/LanLinkProvider", "Exception while receiving TCP packet", e)
            return@withContext
        }

        val pair = unserializeReceivedIdentityPacket(message) ?: return@withContext
        val identityPacket = pair.first
        val deviceTrusted: Boolean = pair.second!!

        Log.i(
            "KDE/LanLinkProvider",
            "identity packet received from a TCP connection from " + identityPacket.getString("deviceName")
        )

        val targetDeviceId = identityPacket.getStringOrNull("targetDeviceId")
        val targetProtocolVersion = identityPacket.getIntOrNull("targetProtocolVersion")
        if (targetDeviceId != null && targetDeviceId != deviceHelper.getDeviceId()) {
            Log.e(
                "KDE/LanLinkProvider",
                "Received a connection request for a device that isn't me: $targetDeviceId"
            )
            return@withContext
        }
        if (targetProtocolVersion != null && targetProtocolVersion != DeviceHelper.PROTOCOL_VERSION) {
            Log.e(
                "KDE/LanLinkProvider",
                "Received a connection request for a protocol version that isn't mine: $targetProtocolVersion"
            )
            return@withContext
        }

        identityPacketReceived(identityPacket, socket, ConnectionStarted.Locally, deviceTrusted)
    }

    fun rateLimitByIp(address: InetAddress): Boolean {
        val now = System.currentTimeMillis()
        val last = lastConnectionTimeByIp[address]
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true
        }
        lastConnectionTimeByIp[address] = now
        if (lastConnectionTimeByIp.size > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByIp.entries.removeIf { e: MutableMap.MutableEntry<InetAddress, Long>? -> e!!.value + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now }
        }
        return false
    }

    fun rateLimitByDeviceId(deviceId: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastConnectionTimeByDeviceId[deviceId]
        if (last != null && (last + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE > now)) {
            return true
        }
        lastConnectionTimeByDeviceId[deviceId] = now
        if (lastConnectionTimeByDeviceId.size > MAX_RATE_LIMIT_ENTRIES) {
            lastConnectionTimeByDeviceId.entries.removeIf { e: MutableMap.MutableEntry<String, Long>? -> e!!.value + MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE < now }
        }
        return false
    }

    //I've received their broadcast and should connect to their TCP socket and send my identity.
    @WorkerThread
    private suspend fun udpPacketReceived(packet: DatagramPacket) = withContext(Dispatchers.IO) {
        val address = packet.address

        if (!isPrivateAddress(address)) {
            Log.i("LanLinkProvider", "Discarding UDP packet from a non-local IP")
            return@withContext
        }

        if (rateLimitByIp(address)) {
            Log.i(
                "LanLinkProvider",
                "Discarding second UDP packet from the same ip $address received too quickly"
            )
            return@withContext
        }

        val message = String(packet.data, UTF_8)

        val pair = unserializeReceivedIdentityPacket(message) ?: return@withContext
        val identityPacket = pair.first
        val deviceTrusted: Boolean = pair.second!!

        Log.i(
            "KDE/LanLinkProvider",
            "Broadcast identity packet received from " + identityPacket.getString("deviceName")
        )

        val tcpPort = identityPacket.getInt("tcpPort", MIN_PORT)
        if (tcpPort !in MIN_PORT..MAX_PORT) {
            Log.e("LanLinkProvider", "TCP port outside of kdeconnect's range")
            return@withContext
        }

        var socket: Socket? = null
        try {
            socket = SocketFactory.getDefault().createSocket(address, tcpPort)
            configureSocket(socket)

            val myDeviceInfo = deviceHelper.getDeviceInfo()
            val myIdentity = myDeviceInfo.toIdentityPacket()
            myIdentity["targetDeviceId"] = identityPacket.getString("deviceId")
            myIdentity["targetProtocolVersion"] = identityPacket.getString("protocolVersion")

            val out = socket.getOutputStream()
            out.write(myIdentity.serialize().toByteArray())
            out.flush()

            identityPacketReceived(
                identityPacket,
                socket,
                ConnectionStarted.Remotely,
                deviceTrusted
            )
        } catch (e: IOException) {
            Log.e("LanLinkProvider", "Exception receiving incoming UDP connection", e)
            if (socket != null) {
                try {
                    socket.close()
                } catch (_: IOException) {
                }
            }
        } catch (e: CertificateException) {
            Log.e("LanLinkProvider", "Exception receiving incoming UDP connection", e)
            if (socket != null) {
                try {
                    socket.close()
                } catch (_: IOException) {
                }
            }
        } catch (e: JSONException) {
            Log.e("LanLinkProvider", "Exception receiving incoming UDP connection", e)
            if (socket != null) {
                try {
                    socket.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun configureSocket(socket: Socket) {
        try {
            socket.keepAlive = true
        } catch (e: SocketException) {
            Log.e("LanLink", "Exception", e)
        }
    }

    /**
     * Called when a new 'identity' packet is received. Those are passed here by
     * [.tcpPacketReceived] and [.udpPacketReceived].
     * Should be called on a new thread since it blocks until the handshake is completed.
     * 
     * @param identityPacket    identity of a remote device
     * @param socket            a new Socket, which should be used to receive packets from the remote device
     * @param connectionStarted which side started this connection
     * @param deviceTrusted     whether the packet comes from a trusted device
     */
    @WorkerThread
    @Throws(IOException::class, CertificateException::class)
    private fun identityPacketReceived(
        identityPacket: NetworkPacket,
        socket: Socket,
        connectionStarted: ConnectionStarted?,
        deviceTrusted: Boolean
    ) {
        val deviceId = identityPacket.getString("deviceId")
        val protocolVersion = identityPacket.getInt("protocolVersion")

        if (deviceTrusted && isProtocolDowngrade(deviceId, protocolVersion)) {
            Log.w(
                "KDE/LanLinkProvider",
                "Refusing to connect to a device using an older protocol version:$protocolVersion"
            )
            return
        }

         if (deviceTrusted && !runBlocking { deviceSettings.isCertificateStored(deviceId) }) {
            Log.e(
                "KDE/LanLinkProvider",
                "Device trusted but no cert stored. This should not happen."
            )
            return
        }

        Log.i(
            "KDE/LanLinkProvider",
            "Starting SSL handshake with $deviceId trusted:$deviceTrusted"
        )

        // If I'm the TCP server I will be the SSL client and vice-versa.
        val clientMode = (connectionStarted == ConnectionStarted.Locally)
        val sslSocket =
            sslHelper.convertToSslSocket(context, socket, deviceId, deviceTrusted, clientMode, deviceSettings)
        sslSocket.addHandshakeCompletedListener { event: HandshakeCompletedEvent? ->
            // Start a new thread because some Android versions don't allow calling sslSocket.getOutputStream() from the callback
            scope?.launch {
                val mode = if (clientMode) "client" else "server"
                try {
                    val secureIdentityPacket: NetworkPacket?
                    if (protocolVersion >= 8) {
                        val myDeviceInfo = deviceHelper.getDeviceInfo()
                        val myIdentity = myDeviceInfo.toIdentityPacket()

                        val writer = sslSocket.getOutputStream()
                        writer.write(myIdentity.serialize().toByteArray(UTF_8))
                        writer.flush()
                        val line =
                            readLineBounded(sslSocket.getInputStream(), MAX_IDENTITY_PACKET_SIZE)
                        // Do not trust the identity packet we received unencrypted
                        secureIdentityPacket = unserialize(line)
                        if (!isValidIdentityPacket(secureIdentityPacket)) {
                            Log.e("KDE/LanLinkProvider", "Identity packet isn't valid")
                            sslSocket.close()
                            return@launch
                        }
                        val newProtocolVersion = secureIdentityPacket.getInt("protocolVersion")
                        if (newProtocolVersion != protocolVersion) {
                            Log.e(
                                "KDE/LanLinkProvider",
                                "Protocol version changed half-way through the handshake: $protocolVersion -> $newProtocolVersion"
                            )
                            sslSocket.close()
                            return@launch
                        }
                        val newDeviceId = secureIdentityPacket.getString("deviceId")
                        if (newDeviceId != deviceId) {
                            Log.e(
                                "KDE/LanLinkProvider",
                                "Device ID changed half-way through the handshake: $deviceId -> $newDeviceId"
                            )
                            sslSocket.close()
                            return@launch
                        }
                    } else {
                        secureIdentityPacket = identityPacket
                    }
                    val certificate = event!!.peerCertificates[0]
                    val deviceInfo = fromIdentityPacketAndCert(secureIdentityPacket, certificate)
                    Log.i(
                        "KDE/LanLinkProvider",
                        "Handshake as " + mode + " successful with " + deviceInfo.name + " secured with " + event.cipherSuite
                    )
                    addOrUpdateLink(sslSocket, deviceInfo)
                } catch (e: JSONException) {
                    Log.e(
                        "KDE/LanLinkProvider",
                        "Remote device doesn't correctly implement protocol version 8",
                        e
                    )
                    try {
                        sslSocket.close()
                    } catch (_: IOException) {
                    }
                } catch (e: IOException) {
                    Log.e(
                        "KDE/LanLinkProvider",
                        "Handshake as $mode failed with $deviceId",
                        e
                    )
                    try {
                        sslSocket.close()
                    } catch (_: IOException) {
                    }
                }
            }
        }

        //Handshake is blocking, so do it on another thread and free this thread to keep receiving new connection
        Log.d("LanLinkProvider", "Starting handshake")
        sslSocket.startHandshake()
        Log.d("LanLinkProvider", "Handshake done")
    }

    private fun isProtocolDowngrade(deviceId: String, protocolVersion: Int): Boolean {
        val lastKnownProtocolVersion =
            runBlocking { deviceSettings.getDeviceInfo(deviceId)?.protocolVersion ?: 0 }
        return lastKnownProtocolVersion > protocolVersion
    }

    /**
     * Add or update a link in the [.visibleDevices] map.
     * 
     * @param socket           a new Socket, which should be used to send and receive packets from the remote device
     * @param deviceInfo       remote device info
     * @throws IOException if an exception is thrown by [LanLink.reset]
     */
    @WorkerThread
    @Throws(IOException::class)
    private suspend fun addOrUpdateLink(socket: SSLSocket, deviceInfo: DeviceInfo) {
        var link = visibleDevices[deviceInfo.id]
        if (link != null) {
            if (!link.deviceInfo.certificate.contentEquals(deviceInfo.certificate)) {
                Log.e(
                    "LanLinkProvider",
                    "LanLink was asked to replace a socket but the certificate doesn't match, aborting"
                )
                return
            }
            // Update existing link
            Log.d("KDE/LanLinkProvider", "Reusing same link for device " + deviceInfo.id)
            link.reset(socket, deviceInfo)
            onDeviceInfoUpdated(deviceInfo)
        } else {
            // Create a new link
            Log.d("KDE/LanLinkProvider", "Creating a new link for device " + deviceInfo.id)
            link = LanLink(context, deviceInfo, this, socket, sslHelper, deviceSettings)
            visibleDevices[deviceInfo.id] = link
            onConnectionReceived(link)
        }
    }

    private fun setupUdpListener() {
        try {
            udpServer = DatagramSocket(null)
            udpServer!!.reuseAddress = true
            udpServer!!.broadcast = true
            udpServer!!.bind(InetSocketAddress(UDP_PORT))
        } catch (e: SocketException) {
            // We ignore this exception and continue without being able to receive broadcasts instead of crashing the app.
            Log.e(
                "LanLinkProvider",
                "Error binding udp server. We can send udp broadcasts but not receive them",
                e
            )
            if (udpServer != null) {
                try {
                    udpServer!!.close()
                } catch (_: Exception) {
                }
                udpServer = null
            }
            return
        }
        scope?.launch {
            Log.i("UdpListener", "Starting UDP listener")
            while (listening) {
                try {
                    val packet = DatagramPacket(ByteArray(MAX_UDP_PACKET_SIZE), MAX_UDP_PACKET_SIZE)
                    udpServer!!.receive(packet)
                    scope?.launch {
                        try {
                            udpPacketReceived(packet)
                        } catch (e: Exception) {
                            Log.e(
                                "LanLinkProvider",
                                "Unhandled exception receiving incoming UDP connection",
                                e
                            )
                        }
                    }
                } catch (e: IOException) {
                    Log.e("LanLinkProvider", "UdpReceive exception", e)
                    onNetworkChange(null) // Trigger a UDP broadcast to try to get them to connect to us instead
                }
            }
            Log.w("UdpListener", "Stopping UDP listener")
        }
    }

    private fun setupTcpListener() {
        try {
            tcpServer = openServerSocketOnFreePort(MIN_PORT)
        } catch (e: IOException) {
            Log.e("LanLinkProvider", "Error creating tcp server", e)
            throw RuntimeException(e)
        }
        scope?.launch {
            while (listening) {
                try {
                    val socket = tcpServer!!.accept()
                    configureSocket(socket)
                    scope?.launch {
                        try {
                            tcpPacketReceived(socket)
                        } catch (e: IOException) {
                            try {
                                socket.close()
                            } catch (_: IOException) {
                            }
                            Log.e(
                                "LanLinkProvider",
                                "Exception receiving incoming TCP connection",
                                e
                            )
                        } catch (e: CertificateException) {
                            try {
                                socket.close()
                            } catch (_: IOException) {
                            }
                            Log.e(
                                "LanLinkProvider",
                                "Exception receiving incoming TCP connection",
                                e
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LanLinkProvider", "TcpReceive exception", e)
                }
            }
            Log.w("TcpListener", "Stopping TCP listener")
        }
    }

    private fun broadcastUdpIdentityPacket(network: Network?) {
        scope?.launch {
            val hostList: MutableList<DeviceHost> = customDevicesHelper.getCustomDeviceList()
            if (trustedNetworkHelper.getIsTrustedNetwork()) {
                hostList.add(DeviceHost.BROADCAST) //Default: broadcast.
            } else {
                Log.i("LanLinkProvider", "Current network isn't trusted, not broadcasting")
            }

            val ipList = ArrayList<InetAddress>()
            for (host in hostList) {
                try {
                    ipList.add(InetAddress.getByName(host.toString()))
                } catch (e: UnknownHostException) {
                    e.printStackTrace()
                }
            }

            if (ipList.isEmpty()) {
                return@launch
            }
            sendUdpIdentityPacket(ipList, network)
        }
    }

    @WorkerThread
    suspend fun sendUdpIdentityPacket(ipList: MutableList<InetAddress>, network: Network?) = withContext(Dispatchers.IO) {
        if (tcpServer == null || !tcpServer!!.isBound) {
            Log.i("LanLinkProvider", "Won't broadcast UDP packet if TCP socket is not ready yet")
            return@withContext
        }

        // TODO: In protocol version 8 this packet doesn't need to contain identity info
        //       since it will be exchanged after the socket is encrypted.
        val myDeviceInfo = deviceHelper.getDeviceInfo()
        val identity = myDeviceInfo.toIdentityPacket()
        identity["tcpPort"] = tcpServer!!.localPort

        val bytes: ByteArray
        try {
            bytes = identity.serialize().toByteArray(UTF_8)
        } catch (e: JSONException) {
            Log.e("KDE/LanLinkProvider", "Failed to serialize identity packet", e)
            return@withContext
        }

        val socket: DatagramSocket?
        try {
            socket = DatagramSocket()
            if (network != null) {
                try {
                    network.bindSocket(socket)
                } catch (e: IOException) {
                    Log.w("LanLinkProvider", "Couldn't bind socket to the network")
                    e.printStackTrace()
                }
            }
            socket.reuseAddress = true
            socket.broadcast = true
        } catch (e: SocketException) {
            Log.e("KDE/LanLinkProvider", "Failed to create DatagramSocket", e)
            return@withContext
        }

        for (ip in ipList) {
            try {
                socket.send(DatagramPacket(bytes, bytes.size, ip, MIN_PORT))
                //Log.i("KDE/LanLinkProvider","Udp identity packet sent to address "+client);
            } catch (e: IOException) {
                Log.e(
                    "KDE/LanLinkProvider",
                    "Sending udp identity packet failed. Invalid address? ($ip)",
                    e
                )
            }
        }

        socket.close()
    }

    override suspend fun onStart() {
        if (!listening) {
            listening = true

            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

            setupUdpListener()
            setupTcpListener()

            mdnsDiscovery.startDiscovering()
            if (trustedNetworkHelper.getIsTrustedNetwork()) {
                mdnsDiscovery.startAnnouncing()
            }

            broadcastUdpIdentityPacket(null)
        }
    }

    override suspend fun onNetworkChange(network: Network?) {
        if (System.currentTimeMillis() < lastBroadcast + DELAY_BETWEEN_BROADCASTS) {
            Log.i("LanLinkProvider", "onNetworkChange: relax cowboy")
            return
        }
        lastBroadcast = System.currentTimeMillis()

        if (udpServer == null) {
            setupUdpListener()
        }

        broadcastUdpIdentityPacket(network)
        synchronized(mdnsDiscovery) {
            mdnsDiscovery.stopDiscovering()
            mdnsDiscovery.startDiscovering()
        }
    }

    override fun onStop() {
        //Log.i("KDE/LanLinkProvider", "onStop");
        listening = false

        scope?.cancel()
        scope = null

        synchronized(mdnsDiscovery) {
            mdnsDiscovery.stopAnnouncing()
            mdnsDiscovery.stopDiscovering()
        }
        try {
            tcpServer!!.close()
        } catch (e: Exception) {
            Log.e("LanLink", "Exception", e)
        }
        try {
            udpServer!!.close()
        } catch (e: Exception) {
            Log.e("LanLink", "Exception", e)
        }
    }

    override val name: String = "LanLinkProvider"

    override val priority: Int = 20

    val tcpPort: Int
        get() = tcpServer!!.localPort

    companion object {
        const val UDP_PORT: Int = 1716
        const val MIN_PORT: Int = 1716
        const val MAX_PORT: Int = 1764
        const val PAYLOAD_TRANSFER_MIN_PORT: Int = 1739

        const val MAX_IDENTITY_PACKET_SIZE: Int = 1024 * 512
        const val MAX_UDP_PACKET_SIZE: Int = 1024 * 512

        const val MILLIS_DELAY_BETWEEN_CONNECTIONS_TO_SAME_DEVICE: Long = 1000L

        const val MAX_RATE_LIMIT_ENTRIES: Int = 255
        private const val DELAY_BETWEEN_BROADCASTS: Long = 200

        @Throws(IOException::class)
        fun openServerSocketOnFreePort(minPort: Int): ServerSocket {
            var tcpPort = minPort
            while (tcpPort <= MAX_PORT) {
                try {
                    val candidateServer = ServerSocket(tcpPort)
                    Log.i("KDE/LanLink", "Using port $tcpPort")
                    return candidateServer
                } catch (e: IOException) {
                    tcpPort++
                    if (tcpPort == MAX_PORT) {
                        Log.e("KDE/LanLink", "No ports available")
                        throw e //Propagate exception
                    }
                }
            }
            throw RuntimeException("This should not be reachable")
        }
    }
}
