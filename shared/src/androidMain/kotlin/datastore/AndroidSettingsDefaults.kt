package org.kde.kdeconnect.datastore

import android.content.Context
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings

class AndroidSettingsDefaults(private val context: Context) : SettingsDefaults {
    override fun getDefaultDeviceName(): String {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: "Android Device"
    }

    override fun getDefaultFileDestination(): String {
        return DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOWNLOADS}"
        ).toString()
    }
}
