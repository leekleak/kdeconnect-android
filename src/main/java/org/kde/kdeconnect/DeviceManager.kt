package org.kde.kdeconnect

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.plugins.Plugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class DeviceManager(
    private val context: Context,
) : KoinComponent {

    val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun interface DeviceListChangedCallback {
        fun onDeviceListChanged()
    }

    val devices: SnapshotStateMap<String, Device> = SnapshotStateMap()

    private val deviceListChangedCallbacks = ConcurrentHashMap<String, DeviceListChangedCallback>()

    init {
        jobScope.launch {
            snapshotFlow { devices }.collect {
                onDeviceListChanged()
            }
        }
    }

    fun addDeviceListChangedCallback(key: String, callback: DeviceListChangedCallback) {
        deviceListChangedCallbacks[key] = callback
    }

    fun removeDeviceListChangedCallback(key: String) {
        deviceListChangedCallbacks.remove(key)
    }

    private fun onDeviceListChanged() {
        Log.i("DeviceManager", "Device list changed, notifying ${deviceListChangedCallbacks.size} observers.")
        deviceListChangedCallbacks.values.forEach(DeviceListChangedCallback::onDeviceListChanged)
    }

    fun getDevice(id: String?): Device? {
        if (id == null) {
            return null
        }
        return devices[id]
    }

    fun <T : Plugin> getDevicePlugin(deviceId: String?, pluginClass: Class<T>): T? {
        val device = getDevice(deviceId)
        return device?.getPlugin(pluginClass)
    }

    fun loadRememberedDevicesFromSettings() {
        val trustedDevices = TrustedDevices.getAllTrustedDevices(context)
        trustedDevices.asSequence()
            .onEach { Log.d("DeviceManager", "Loading device $it") }
            .forEach {
                try {
                    val device: Device = get { parametersOf(it, null) }
                    val now = Date()
                    val x509Cert = device.certificate as X509Certificate
                    if (now < x509Cert.notBefore) {
                        throw CertificateException("Certificate not effective yet: " + x509Cert.notBefore)
                    } else if (now > x509Cert.notAfter) {
                        throw CertificateException("Certificate already expired: " + x509Cert.notAfter)
                    }
                    devices[it] = device
                } catch (e: CertificateException) {
                    Log.w(
                        "DeviceManager",
                        "Couldn't load the certificate for a remembered device. Removing from trusted list.", e
                    )
                    TrustedDevices.removeTrustedDevice(context, it)
                }
            }
    }

    val connectionListener: ConnectionReceiver = object : ConnectionReceiver {
        @WorkerThread
        override fun onConnectionReceived(link: BaseLink) {
            var device: Device? = devices[link.deviceId]
            if (device != null) {
                device.addLink(link)
            } else {
                device = get { parametersOf(link.deviceId, link) }
                devices[link.deviceId] = device
            }
            onDeviceListChanged()
        }

        @WorkerThread
        override fun onConnectionLost(link: BaseLink) {
            val device = devices[link.deviceId]
            Log.i("DeviceManager/onConnectionLost", "removeLink, deviceId: ${link.deviceId}")
            if (device != null) {
                device.removeLink(link)
                if (!device.isReachable) {
                    devices.remove(device.deviceId)
                }
            } else {
                Log.d("DeviceManager/onConnectionLost", "Removing connection to unknown device")
            }
            onDeviceListChanged()
        }

        @WorkerThread
        override fun onDeviceInfoUpdated(deviceInfo: DeviceInfo) {
            val device = devices[deviceInfo.id]
            if (device == null) {
                Log.e("DeviceManager", "onDeviceInfoUpdated for an unknown device")
                return
            }
            val hasChanges = device.updateDeviceInfo(deviceInfo)
            if (hasChanges) {
                onDeviceListChanged()
            }
        }
    }
}
