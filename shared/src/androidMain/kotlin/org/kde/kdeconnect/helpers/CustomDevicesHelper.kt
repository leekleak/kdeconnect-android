package org.kde.kdeconnect.helpers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import java.util.ArrayList
import java.util.Comparator

class CustomDevicesHelper(private val dataStore: ConnectionsSettingsDataStore) {
    private val IP_DELIM = ","

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

    suspend fun getCustomDeviceList(): ArrayList<DeviceHost> = withContext(Dispatchers.IO) {
        val deviceListPrefs = dataStore.customDeviceList.first()
        val list = deserializeIpList(deviceListPrefs)
        list.sortWith(Comparator.comparing { it.toString() })
        return@withContext list
    }
}
