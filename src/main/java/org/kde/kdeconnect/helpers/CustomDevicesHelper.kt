package org.kde.kdeconnect.helpers

import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.ArrayList
import java.util.Comparator

object CustomDevicesHelper : KoinComponent {
    val dataStore: ConnectionsSettingsDataStore by inject()
    private const val IP_DELIM = ","

    @JvmStatic
    fun deserializeIpList(serialized: String): ArrayList<DeviceHost> {
        val ipList = ArrayList<DeviceHost>()
        if (!serialized.isEmpty()) {
            for (ip in serialized.split(IP_DELIM)) {
                val deviceHost = DeviceHost.toDeviceHostOrNull(ip)
                if (deviceHost != null) {
                    ipList.add(deviceHost)
                }
            }
        }
        return ipList
    }

    @JvmStatic
    fun getCustomDeviceList(): ArrayList<DeviceHost> {
        val deviceListPrefs = dataStore.getCustomDeviceListBlocking()
        val list = deserializeIpList(deviceListPrefs)
        list.sortWith(Comparator.comparing { it.toString() })
        return list
    }
}
