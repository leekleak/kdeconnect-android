package org.kde.kdeconnect.helpers

import android.content.Context
import android.text.TextUtils
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.get
import java.util.ArrayList
import java.util.Comparator

object CustomDevicesHelper : KoinComponent {
    val dataStore: SettingsDataStore by inject()
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
    fun getCustomDeviceList(context: Context): ArrayList<DeviceHost> {
        val deviceListPrefs = dataStore.getCustomDeviceListBlocking()
        val list = deserializeIpList(deviceListPrefs)
        list.sortWith(Comparator.comparing { it.toString() })
        return list
    }

    @JvmStatic
    fun saveCustomDeviceList(context: Context, customDeviceList: List<DeviceHost>) {
        val serialized = TextUtils.join(IP_DELIM, customDeviceList)
        ThreadHelper.execute {
            kotlinx.coroutines.runBlocking {
                dataStore.setCustomDeviceList(serialized)
            }
        }
    }
}
