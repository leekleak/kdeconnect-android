/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.backends.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.LoggerTagged

class MdnsDiscovery(
    context: Context,
    private val lanLinkProvider: LanLinkProvider,
    private val deviceHelper: DeviceHelper
) {
    private val mNsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: RegistrationListener? = null
    private var discoveryListener: DiscoveryListener? = null
    private val multicastLock: MulticastLock
    private val mNsdResolveQueue: NsdResolveQueue = NsdResolveQueue(mNsdManager)

    init {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("kdeConnectMdnsMulticastLock")
    }

    fun startDiscovering() {
        if (discoveryListener == null) {
            multicastLock.acquire()
            discoveryListener = createDiscoveryListener()
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
    }

    fun stopDiscovering() {
        try {
            if (discoveryListener != null) {
                mNsdManager.stopServiceDiscovery(discoveryListener)
                multicastLock.release()
            }
        } catch (_: IllegalArgumentException) {
            // Ignore "listener not registered" exception
        }
        discoveryListener = null
    }

    suspend fun startAnnouncing() {
        if (registrationListener == null) {
            val serviceInfo: NsdServiceInfo?
            try {
                serviceInfo = createNsdServiceInfo()
            } catch (e: IllegalAccessException) {
                LoggerTagged.w { "Couldn't start announcing via MDNS: " + e.message }
                return
            }
            registrationListener = createRegistrationListener()
            mNsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    fun stopAnnouncing() {
        try {
            if (registrationListener != null) {
                mNsdManager.unregisterService(registrationListener)
            }
        } catch (_: IllegalArgumentException) {
            // Ignore "listener not registered" exception
        }
        registrationListener = null
    }

    fun createRegistrationListener() = object : RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            // If Android changed the service name to avoid conflicts, here we can read it.
            LoggerTagged.i { "Registered ${serviceInfo.serviceName}" }
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            LoggerTagged.e { "Registration failed with: $errorCode" }
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
            LoggerTagged.d { "Service unregistered: $serviceInfo" }
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            LoggerTagged.e { "Unregister of $serviceInfo failed with: $errorCode" }
        }
    }

    @Throws(IllegalAccessException::class)
    suspend fun createNsdServiceInfo(): NsdServiceInfo = withContext(Dispatchers.IO) {
        val serviceInfo = NsdServiceInfo()

        val deviceId = deviceHelper.getDeviceId()
        // Without resolving the DNS, the service name is the only info we have so it must be sufficient to identify a device.
        // Also, it must be unique, otherwise it will be automatically renamed. For these reasons we use the deviceId.
        serviceInfo.serviceName = deviceId
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port = lanLinkProvider.tcpPort

        // The following fields aren't really used for anything, since we can't include enough info
        // for it to be useful (namely: we can't include the device certificate).
        // Each field (key + value) needs to be < 255 bytes. All the fields combined need to be < 1300 bytes.
        val deviceName = deviceHelper.getDeviceName()
        val deviceType = deviceHelper.deviceType.toString()
        val protocolVersion = DeviceHelper.PROTOCOL_VERSION.toString()
        serviceInfo.setAttribute("id", deviceId)
        serviceInfo.setAttribute("name", deviceName)
        serviceInfo.setAttribute("type", deviceType)
        serviceInfo.setAttribute("protocol", protocolVersion)

        LoggerTagged.i { "My MDNS info: $serviceInfo" }

        return@withContext serviceInfo
    }

    fun createDiscoveryListener() = object : DiscoveryListener {
        val myId: String = deviceHelper.getDeviceId()

        override fun onDiscoveryStarted(serviceType: String?) {
            LoggerTagged.i { "Service discovery started: $serviceType" }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            LoggerTagged.d { "Service discovered: $serviceInfo" }

            val deviceId = serviceInfo.serviceName

            if (myId == deviceId) {
                LoggerTagged.d { "Discovered myself, ignoring." }
                return
            }

            if (lanLinkProvider.visibleDevices.containsKey(deviceId)) {
                LoggerTagged.i { "MDNS discovered $deviceId to which I'm already connected to. Ignoring." }
                return
            }

            // We use a queue because only one service can be resolved at
            // a time, otherwise we get error 3 (already active) in onResolveFailed.
            mNsdResolveQueue.resolveOrEnqueue(serviceInfo, createResolveListener())
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            LoggerTagged.w { "Service lost: $serviceInfo" }
            // We can't see this device via mdns. This probably means it's not reachable anymore
            // but we do nothing here since we have other ways to do detect unreachable devices
            // that hopefully will also trigger.
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            LoggerTagged.i { "MDNS discovery stopped: $serviceType" }
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            LoggerTagged.e { "MDNS discovery start failed: $errorCode" }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            LoggerTagged.e { "MDNS discovery stop failed: $errorCode" }
        }
    }

    /**
     * Returns a new listener instance since NsdManager wants a different listener each time you call resolveService
     */
    fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            LoggerTagged.w { "MDNS error $errorCode resolving service: $serviceInfo" }
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            LoggerTagged.i { "MDNS successfully resolved $serviceInfo" }

            // Let the LanLinkProvider handle the connection
            val remoteAddress = serviceInfo.host
            // TODO: In protocol version 8 we should be able to call "identityPacketReceived"
            //       here, since we already have all the info we need to start a connection
            //       and the remaining identity info will be exchanged later.
            runBlocking {
                lanLinkProvider.sendUdpIdentityPacket(mutableListOf(remoteAddress), null)
            }
        }
    }

    companion object {

        const val SERVICE_TYPE: String = "_kdeconnect._udp"
    }
}
