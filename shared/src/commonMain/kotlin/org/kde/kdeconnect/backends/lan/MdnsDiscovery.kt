package org.kde.kdeconnect.backends.lan

import com.appstractive.dnssd.DiscoveryEvent
import com.appstractive.dnssd.discoverServices
import com.appstractive.dnssd.publishService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.PROTOCOL_VERSION

class MdnsDiscovery(
    private val deviceHelper: DeviceHelper,
    private val tcpPortProvider: () -> Int,
    private val serviceType: String = SERVICE_TYPE_LAN,
    private val onDiscoveryStarted: () -> Unit = {},
    private val onDiscoveryStopped: () -> Unit = {},
    private val onDeviceDiscovered: (String, String, Int) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var discoveryJob: Job? = null
    private var announcementJob: Job? = null

    fun startDiscovering() {
        if (discoveryJob != null) return
        onDiscoveryStarted()
        discoveryJob = scope.launch {
            try {
                discoverServices(serviceType).collect { event ->
                    when (event) {
                        is DiscoveryEvent.Discovered -> {
                            LoggerTagged.d { "Discovered service ($serviceType): ${event.service.name}" }
                            event.resolve()
                        }
                        is DiscoveryEvent.Resolved -> {
                            val deviceId = event.service.name
                            val host = event.service.addresses.firstOrNull() ?: ""
                            val port = event.service.port
                            LoggerTagged.d { "Resolved service ($serviceType): $deviceId at $host:$port" }
                            if (host.isNotEmpty() && port > 0) {
                                onDeviceDiscovered(deviceId, host, port)
                            }
                        }
                        is DiscoveryEvent.Removed -> {
                            LoggerTagged.d { "Removed service ($serviceType): ${event.service.name}" }
                        }
                    }
                }
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Error in MDNS discovery for $serviceType" }
            }
        }
    }

    fun stopDiscovering() {
        discoveryJob?.cancel()
        discoveryJob = null
        onDiscoveryStopped()
    }

    suspend fun startAnnouncing() {
        if (announcementJob != null) return
        val deviceInfo = deviceHelper.getDeviceInfo()
        val deviceId = deviceInfo.id
        val deviceName = deviceInfo.name
        val deviceType = deviceInfo.type.toString()
        val port = tcpPortProvider()

        announcementJob = scope.launch {
            try {
                publishService(
                    type = serviceType,
                    name = deviceId,
                ) {
                    this.port = port
                    txt = mapOf(
                        "id" to deviceId,
                        "name" to deviceName,
                        "type" to deviceType,
                        "protocol" to PROTOCOL_VERSION.toString()
                    )
                }
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Error in MDNS announcement for $serviceType" }
            }
        }
    }

    fun stopAnnouncing() {
        announcementJob?.cancel()
        announcementJob = null
    }

    companion object {
        const val SERVICE_TYPE_LAN = "_kdeconnect._udp"
        const val SERVICE_TYPE_HTTP = "_kdeconnect-http._tcp"
    }
}
