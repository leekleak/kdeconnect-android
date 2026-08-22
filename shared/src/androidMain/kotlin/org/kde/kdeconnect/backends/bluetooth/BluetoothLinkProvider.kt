/*
 * SPDX-FileCopyrightText: 2016 Saikrishna Arcot <saiarcot895@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Network
import android.os.Parcelable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.fromIdentityPacketAndCert
import org.kde.kdeconnect.isValidIdentityPacket
import org.kde.kdeconnect.toIdentityPacket
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.extensions.getParcelableArrayCompat
import org.kde.kdeconnect.extensions.getParcelableCompat
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import org.kde.kdeconnect.helpers.security.SslHelper
import org.jetbrains.compose.resources.DrawableResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.bluetooth
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.security.cert.CertificateException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8

class BluetoothLinkProvider(
    private val context: Context,
    val dataStore: SettingsDataStore,
    private val deviceHelper: DeviceHelper,
    private val sslHelper: SslHelper
) : BaseLinkProvider() {
    private val visibleDevices: ConcurrentHashMap<String, BluetoothLink> = ConcurrentHashMap()
    private val sockets: ConcurrentHashMap<BluetoothDevice, BluetoothSocket> = ConcurrentHashMap()
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverRunnable: ServerRunnable? = null
    private var clientRunnable: ClientRunnable? = null

    @Throws(CertificateException::class)
    private suspend fun addLink(identityPacket: NetworkPacket, link: BluetoothLink) {
        val deviceId = identityPacket.getString("deviceId")
        LoggerTagged.i { "addLink to $deviceId" }
        val oldLink = visibleDevices[deviceId]
        if (oldLink == link) {
            LoggerTagged.e { "oldLink == link. This should not happen!" }
            return
        }
        visibleDevices[deviceId] = link
        onConnectionReceived(link)
        link.startListening()
        link.packetReceived(identityPacket)
        if (oldLink != null) {
            LoggerTagged.i { "Removing old connection to same device" }
            oldLink.disconnect()
        }
    }

    init {
        if (bluetoothAdapter == null) {
            LoggerTagged.e { "No bluetooth adapter found." }
        }
    }

    override suspend fun onStart() {
        if (!dataStore.bluetoothEnabled.first()) {
            return
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return
        }
        LoggerTagged.i { "onStart called" }

        //This handles the case when I'm the existing device in the network and receive a hello package
        clientRunnable = ClientRunnable()
        execute(clientRunnable!!)

        // I'm on a new network, let's be polite and introduce myself
        serverRunnable = ServerRunnable()
        execute(serverRunnable!!)
    }

    override suspend fun onNetworkChange(network: Network?) {
        LoggerTagged.i { "onNetworkChange called" }
        onStop()
        onStart()
    }

    override fun onStop() {
        if (bluetoothAdapter == null || clientRunnable == null || serverRunnable == null) {
            return
        }
        LoggerTagged.i { "onStop called" }
        clientRunnable!!.stopProcessing()
        serverRunnable!!.stopProcessing()
    }

    override val name: String = "BluetoothLinkProvider"
    override val icon: DrawableResource = Res.drawable.bluetooth

    override val priority: Int = 10

    suspend fun disconnectedLink(link: BluetoothLink, remoteAddress: BluetoothDevice?) {
        LoggerTagged.i { "disconnectedLink called" }
        sockets.remove(remoteAddress)
        visibleDevices.remove(link.deviceId)
        onConnectionLost(link)
    }

    private inner class ServerRunnable : Runnable {
        private var continueProcessing = true
        private var serverSocket: BluetoothServerSocket? = null
        fun stopProcessing() {
            continueProcessing = false
            try {
                serverSocket?.close()
            } catch (e: IOException) {
                LoggerTagged.e(e) { "Exception" }
            }
        }

        override fun run() {
            serverSocket = try {
                bluetoothAdapter!!.listenUsingRfcommWithServiceRecord("KDE Connect", SERVICE_UUID)
            } catch (e: IOException) {
                LoggerTagged.e(e) { "Exception" }
                return
            } catch (e: SecurityException) {
                LoggerTagged.e(e) { "Security Exception for CONNECT" }

                runBlocking {
                    dataStore.setBluetoothEnabled(false)
                }

                return
            }
            try {
                while (continueProcessing) {
                    val socket = serverSocket!!.accept()
                    runBlocking {
                        connect(socket)
                    }
                }
            } catch (e: Exception) {
                LoggerTagged.d(e) { "Bluetooth Server error" }
            }
        }

        @Throws(Exception::class)
        private suspend fun connect(socket: BluetoothSocket) {
            if (sockets.containsKey(socket.remoteDevice)) {
                LoggerTagged.i { "Received duplicate connection from " + socket.remoteDevice.address }
                socket.close()
                return
            } else {
                sockets[socket.remoteDevice] = socket
            }
            LoggerTagged.i { "Received connection from " + socket.remoteDevice.address }

            //Delay to let bluetooth initialize stuff correctly
            try {
                Thread.sleep(500)
            } catch (e: Exception) {
                sockets.remove(socket.remoteDevice)
                throw e
            }
            try {
                ConnectionMultiplexer(socket).use { connection ->
                    val outputStream = connection.defaultOutputStream
                    val inputStream = connection.defaultInputStream

                    val myDeviceInfo = deviceHelper.getDeviceInfo()
                    val np = myDeviceInfo.toIdentityPacket()
                    val myCertificate = Base64.Mime.encode(sslHelper.certificate.encoded, 0)
                    val pemEncodedCertificate = "-----BEGIN CERTIFICATE-----\n$myCertificate\n-----END CERTIFICATE-----\n"

                    np["certificate"] = pemEncodedCertificate

                    val message = np.serialize().toByteArray(UTF_8)
                    outputStream.write(message)
                    outputStream.flush()
                    LoggerTagged.i { "Sent identity packet" }

                    // Listen for the response
                    val sb = StringBuilder()
                    val reader: Reader = InputStreamReader(inputStream, UTF_8)
                    var charsRead = 0
                    val buf = CharArray(512)
                    while (sb.lastIndexOf("\n") == -1 && reader.read(buf).also { charsRead = it } != -1) {
                        sb.appendRange(buf, 0, charsRead)
                    }
                    val response = sb.toString()
                    val identityPacket = NetworkPacket.unserialize(response)
                    if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
                        LoggerTagged.w { "Invalid identity packet received." }
                        return
                    }
                    LoggerTagged.i { "Received identity packet" }
                    val pemEncodedCertificateString = identityPacket.getString("certificate")
                    val base64CertificateString = pemEncodedCertificateString
                            .replace("-----BEGIN CERTIFICATE-----\n", "")
                            .replace("-----END CERTIFICATE-----\n", "")
                    val pemEncodedCertificateBytes = Base64.Mime.decode(base64CertificateString, 0)
                    val certificate = sslHelper.parseCertificate(pemEncodedCertificateBytes)
                    val deviceInfo = DeviceInfo.fromIdentityPacketAndCert(identityPacket, certificate)
                    LoggerTagged.i { "About to create link" }
                    val link = BluetoothLink(connection,
                            inputStream, outputStream, socket.remoteDevice,
                            deviceInfo, this@BluetoothLinkProvider)
                    LoggerTagged.i { "About to addLink" }
                    addLink(identityPacket, link)
                    LoggerTagged.i { "Link Added" }
                }
            } catch (e: Exception) {
                sockets.remove(socket.remoteDevice)
                LoggerTagged.i(e) { "Exception thrown, removing socket" }
                throw e
            }
        }
    }

    object ClientRunnableSingleton {
        val connectionThreads: MutableMap<BluetoothDevice?, Thread> = HashMap()
    }

    private inner class ClientRunnable : BroadcastReceiver(), Runnable {
        private var continueProcessing = true
        fun stopProcessing() {
            continueProcessing = false
        }

        override fun run() {
            try {
                LoggerTagged.i { "run called" }
                val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
                context.registerReceiver(this, filter)
                LoggerTagged.i { "receiver registered" }
                if (continueProcessing) {
                    LoggerTagged.i { "before connectToDevices" }
                    discoverDeviceServices()
                    LoggerTagged.i { "after connectToDevices" }
                    try {
                        Thread.sleep(15000)
                    } catch (_: InterruptedException) {
                    }
                }
                LoggerTagged.i { "unregisteringReceiver" }
                context.unregisterReceiver(this)
            } catch (se: SecurityException) {
                LoggerTagged.w(se) { "BluetoothLinkProvider" }
            } catch (ia: IllegalArgumentException) {
                LoggerTagged.w(ia) { "BluetoothLinkProvider" } // Happens sometimes in unregisterReceiver
            }
        }

        /**
         * Tell Android to use ServiceDiscoveryProtocol to update the
         * list of available UUIDs associated with Bluetooth devices
         * that are bluetooth-paired-but-not-yet-kde-paired
         */

        private fun discoverDeviceServices() {
            LoggerTagged.i { "connectToDevices called" }
            val pairedDevices = bluetoothAdapter!!.bondedDevices
            if (pairedDevices == null) {
                LoggerTagged.i { "Paired Devices is NULL" }
                return
            }
            LoggerTagged.i { "Bluetooth adapter paired devices: " + pairedDevices.size }

            // Loop through Bluetooth paired devices
            for (device in pairedDevices) {
                // If a socket exists for this, then it has been paired in KDE
                if (sockets.containsKey(device)) {
                    continue
                }
                LoggerTagged.i { "Calling fetchUuidsWithSdp for device: $device" }
                device.fetchUuidsWithSdp()
                val deviceUuids = device.uuids
                if (deviceUuids != null) {
                    for (thisUuid in deviceUuids) {
                        LoggerTagged.i { "device $device uuid: $thisUuid" }
                    }
                }
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_UUID == action) {
                LoggerTagged.i { "Action matches" }
                val device = intent.getParcelableCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val activeUuids = intent.getParcelableArrayCompat<Parcelable>(BluetoothDevice.EXTRA_UUID)
                if (sockets.containsKey(device)) {
                    LoggerTagged.i { "sockets contains device" }
                    return
                }
                if (activeUuids == null) {
                    LoggerTagged.i { "activeUuids is null" }
                    return
                }
                for (uuid in activeUuids) {
                    if (uuid.toString() == SERVICE_UUID.toString() || uuid.toString() == BYTE_REVERSED_SERVICE_UUID.toString()) {
                        LoggerTagged.i { "calling connectToDevice for device: " + device!!.address }
                        connectToDevice(device)
                        return
                    }
                }
            }
        }

        private fun connectToDevice(device: BluetoothDevice?) {
            synchronized(ClientRunnableSingleton.connectionThreads) {
                if (!ClientRunnableSingleton.connectionThreads.containsKey(device) || !ClientRunnableSingleton.connectionThreads[device]!!.isAlive) {
                    val connectionThread = Thread(ClientConnect(device))
                    connectionThread.start()
                    ClientRunnableSingleton.connectionThreads[device] = connectionThread
                }
            }
        }
    }

    private inner class ClientConnect(private val device: BluetoothDevice?) : Runnable {
        override fun run() {
            runBlocking {
                connectToDevice()
            }
        }

        private suspend fun connectToDevice() {
            val socket: BluetoothSocket
            try {
                LoggerTagged.i { "Cancelling Discovery" }
                bluetoothAdapter!!.cancelDiscovery()
                LoggerTagged.i { "Creating RFCommSocket to Service Record" }
                socket = device!!.createRfcommSocketToServiceRecord(SERVICE_UUID)
                LoggerTagged.i { "Connecting to ServiceRecord Socket" }
                socket.connect()
                sockets[device] = socket
            } catch (e: IOException) {
                LoggerTagged.e(e) { "Could not connect to KDE Connect service on " + device!!.address }
                return
            } catch (e: SecurityException) {
                LoggerTagged.e(e) { "Security Exception connecting to " + device!!.address }
                return
            }
            LoggerTagged.i { "Connected to " + device.address }
            try {
                //Delay to let bluetooth initialize stuff correctly
                Thread.sleep(500)
                val connection = ConnectionMultiplexer(socket)
                val outputStream = connection.defaultOutputStream
                val inputStream = connection.defaultInputStream
                LoggerTagged.i { "Device: " + device.address + " Before inputStream.read()" }
                var character = 0
                val sb = StringBuilder()
                while (sb.lastIndexOf("\n") == -1 && inputStream.read().also { character = it } != -1) {
                    sb.append(character.toChar())
                }
                LoggerTagged.i { "Device: " + device.address + " Before sb.toString()" }
                val message = sb.toString()
                LoggerTagged.i { "Device: " + device.address + " Before unserialize (message: '" + message + "')" }
                val identityPacket = NetworkPacket.unserialize(message)
                LoggerTagged.i { "Device: " + device.address + " After unserialize" }

                if (!DeviceInfo.isValidIdentityPacket(identityPacket)) {
                    LoggerTagged.w { "Invalid identity packet received." }
                    connection.close()
                    return
                }

                LoggerTagged.i { "Received identity packet" }
                val myId = deviceHelper.getDeviceId()
                if (identityPacket.getString("deviceId") == myId) {
                    // Probably won't happen, but just to be safe
                    connection.close()
                    return
                }
                if (visibleDevices.containsKey(identityPacket.getString("deviceId"))) {
                    return
                }
                LoggerTagged.i { "identity packet received, creating link" }
                val pemEncodedCertificateString = identityPacket.getString("certificate")
                val base64CertificateString = pemEncodedCertificateString
                        .replace("-----BEGIN CERTIFICATE-----\n", "")
                        .replace("-----END CERTIFICATE-----\n", "")

                val pemEncodedCertificateBytes = Base64.Mime.decode(base64CertificateString, 0)
                val certificate = sslHelper.parseCertificate(pemEncodedCertificateBytes)
                val deviceInfo = DeviceInfo.fromIdentityPacketAndCert(identityPacket, certificate)
                val link = BluetoothLink(connection, inputStream, outputStream,
                        socket.remoteDevice, deviceInfo, this@BluetoothLinkProvider)

                val myDeviceInfo = deviceHelper.getDeviceInfo()
                val np2 = myDeviceInfo.toIdentityPacket()
                val myCertificate = Base64.Mime.encode(sslHelper.certificate.encoded, 0)
                val pemEncodedCertificate = "-----BEGIN CERTIFICATE-----\n$myCertificate\n-----END CERTIFICATE-----\n"

                np2["certificate"] = pemEncodedCertificate
                LoggerTagged.i { "about to send packet np2" }
                link.sendPacket(np2, object : Device.SendPacketStatusCallback() {
                    override fun onSuccess() {
                        try {
                            runBlocking {
                                addLink(identityPacket, link)
                            }
                        } catch (e: CertificateException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onFailure(e: Throwable) {}
                })
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Connection lost/disconnected on " + device.address }
                sockets.remove(device, socket)
            }
        }
    }

    companion object {
        private val SERVICE_UUID = UUID.fromString("185f3df4-3268-4e3f-9fca-d4d5059915bd")
        private val BYTE_REVERSED_SERVICE_UUID = UUID(java.lang.Long.reverseBytes(SERVICE_UUID.leastSignificantBits), java.lang.Long.reverseBytes(SERVICE_UUID.mostSignificantBits))
    }
}
