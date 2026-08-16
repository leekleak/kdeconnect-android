package org.kde.kdeconnect
 
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.Plugin
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date

class DeviceManager(
    private val deviceSettings: DeviceSettings,
    private val deviceFactory: (String, BaseLink?) -> Device
) {
    private val _devices: MutableStateFlow<Map<String, Device>> = MutableStateFlow(emptyMap())
    val devices: StateFlow<Map<String, Device>> = _devices.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val allDeviceStatesMap: Flow<Map<String, DeviceState>> =
        devices.flatMapLatest { deviceMap ->
            if (deviceMap.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(deviceMap.values.map { it.state }) { states ->
                    states.associateBy { it.deviceInfo.id }
                }
            }
        }

    init {
        loadRememberedDevicesFromSettings()
    }

    fun getDevice(id: String?): Device? {
        if (id == null) {
            return null
        }
        return devices.value[id]
    }

    fun <T : Plugin> getDevicePlugin(deviceId: String?, pluginClass: Class<T>): T? {
        val device = getDevice(deviceId)
        return device?.getPlugin(pluginClass)
    }

    private fun loadRememberedDevicesFromSettings() {
        runBlocking { deviceSettings.getAllTrustedDevices() }
            .onEach { LoggerTagged.d { "Loading device $it" } }
            .forEach { trustedDevice ->
                try {
                    val device: Device = deviceFactory(trustedDevice, null)
                    val now = Date()
                    val x509Cert = device.certificate as X509Certificate
                    if (now < x509Cert.notBefore) {
                        throw CertificateException("Certificate not effective yet: " + x509Cert.notBefore)
                    } else if (now > x509Cert.notAfter) {
                        throw CertificateException("Certificate already expired: " + x509Cert.notAfter)
                    }
                    _devices.update { it + (trustedDevice to device) }
                } catch (e: CertificateException) {
                    LoggerTagged.w(e) {
                        "Couldn't load the certificate for a remembered device. Removing from trusted list."
                    }
                    runBlocking { deviceSettings.removeTrustedDevice(trustedDevice) }
                }
            }
    }

    val connectionListener: ConnectionReceiver = object : ConnectionReceiver {
        override fun onConnectionReceived(link: BaseLink) {
            var device: Device? = devices.value[link.deviceId]
            if (device != null) {
                device.addLink(link)
            } else {
                device = deviceFactory(link.deviceId, link)
                _devices.update { it + (link.deviceId to device) }
            }
        }

        override fun onConnectionLost(link: BaseLink) {
            LoggerTagged.i { "Connection lost, removing link deviceId: ${link.deviceId}" }
            val device = devices.value[link.deviceId] ?: return
            device.removeLink(link)
            device.cancel()
        }

        override fun onDeviceInfoUpdated(deviceInfo: DeviceInfo) {
            val device = devices.value[deviceInfo.id]
            if (device == null) {
                LoggerTagged.e { "onDeviceInfoUpdated for an unknown device" }
                return
            }
            device.updateDeviceInfo(deviceInfo)
        }
    }
}
