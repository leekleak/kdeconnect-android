package org.kde.kdeconnect.helpers

import android.content.Context
import android.preference.PreferenceManager
import android.text.TextUtils
import org.kde.kdeconnect.DeviceHost
import java.util.ArrayList
import java.util.Comparator
import androidx.core.content.edit

object CustomDevicesHelper {
    const val KEY_CUSTOM_DEVLIST_PREFERENCE = "device_list_preference"
    private const val IP_DELIM = ","

    private fun deserializeIpList(serialized: String): ArrayList<DeviceHost> {
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
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val deviceListPrefs = sharedPreferences.getString(KEY_CUSTOM_DEVLIST_PREFERENCE, "") ?: ""
        val list = deserializeIpList(deviceListPrefs)
        list.sortWith(Comparator.comparing { it.toString() })
        return list
    }

    @JvmStatic
    fun saveCustomDeviceList(context: Context, customDeviceList: List<DeviceHost>) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val serialized = TextUtils.join(IP_DELIM, customDeviceList)
        sharedPreferences.edit { putString(KEY_CUSTOM_DEVLIST_PREFERENCE, serialized) }
    }
}
