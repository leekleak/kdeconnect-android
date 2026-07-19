/*
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.sftp

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.helpers.getLocalIpAddress
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.Companion.PACKET_TYPE_SFTP
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.Companion.PACKET_TYPE_SFTP_REQUEST
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.StorageInfo
import org.kde.kdeconnect.ui.DeviceSettingsAlertDialogFragment
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.StartActivityAlertDialogFragment
import org.kde.kdeconnect_tp.BuildConfig
import org.kde.kdeconnect_tp.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SftpPlugin(
    context: Context,
    device: org.kde.kdeconnect.Device,
    private val dataStore: SftpSettingsDataStore
) : Plugin(context, device) {
    override val pluginInfo: SftpPluginInfo = SftpPluginInfo

    private var job: Job? = null

    override fun onCreate(): Boolean {
        job = CoroutineScope(Dispatchers.Main).launch {
            dataStore.storageInfoListJson.collect {
                if (!server.isStarted) return@collect

                server.stop()

                val np = NetworkPacket(PACKET_TYPE_SFTP_REQUEST).apply {
                    this["startBrowsing"] = true
                }
                onPacketReceived(np)
            }
        }
        return true
    }

    override fun onDestroy() {
        server.stop()
        job?.cancel()
        job = null
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (!np.getBoolean("startBrowsing")) return false

        if (!pluginInfo.checkRequiredPermissions(context)) {
            pluginInfo.showPermissionExplanation(context, device.deviceId)
            val noPermissionsPacket = NetworkPacket(PACKET_TYPE_SFTP).apply {
                this["errorMessage"] = context.getString(R.string.sftp_missing_permission_error)
            }
            device.sendPacket(noPermissionsPacket)
            return true
        }

        if (!server.isInitialized || server.isClosed) {
            server.initialize(context, device)
        }

        val paths = mutableListOf<String>()
        val pathNames = mutableListOf<String>()

        if (SimpleSftpServer.SUPPORTS_NATIVEFS) {
            val volumes = context.getSystemService(
                StorageManager::class.java
            ).storageVolumes
            for (sv in volumes) {
                pathNames.add(sv.getDescription(context))
                paths.add(sv.directory!!.path)
            }
        } else {
            val storageInfoList = pluginInfo.getStorageInfoList()
            storageInfoList.sortBy { it.uri }
            if (storageInfoList.isEmpty()) {
                device.sendPacket(NetworkPacket(PACKET_TYPE_SFTP).apply {
                    this["errorMessage"] = context.getString(R.string.sftp_no_storage_locations_configured)
                })
                return true
            }
            getPathsAndNamesForStorageInfoList(paths, pathNames, storageInfoList)
            storageInfoList.removeChildren()
            server.setSafRoots(storageInfoList)
        }

        if (!server.start()) {
            return false
        }

        device.sendPacket(NetworkPacket(PACKET_TYPE_SFTP).apply {
            this["ip"] = getLocalIpAddress()!!.hostAddress
            this["port"] = server.port
            this["user"] = SimpleSftpServer.USER
            this["password"] = server.regeneratePassword()
            // Kept for compatibility, in case "multiPaths" is not possible or the other end does not support it
            this["path"] = if (paths.size == 1) paths[0] else "/"
            if (paths.isNotEmpty()) {
                this["multiPaths"] = paths
                this["pathNames"] = pathNames
            }
        })

        return true
    }

    private fun getPathsAndNamesForStorageInfoList(
        paths: MutableList<String>,
        pathNames: MutableList<String>,
        storageInfoList: List<StorageInfo>
    ) {
        var prevInfo: StorageInfo? = null
        val pathBuilder = StringBuilder()

        for (curInfo in storageInfoList) {
            pathBuilder.setLength(0)
            pathBuilder.append("/")

            if (prevInfo != null && curInfo.uri.toString().startsWith(prevInfo.uri.toString())) {
                pathBuilder.append(prevInfo.displayName)
                pathBuilder.append("/")
                if (curInfo.uri.path != null && prevInfo.uri.path != null) {
                    pathBuilder.append(curInfo.uri.path!!.substring(prevInfo.uri.path!!.length))
                } else {
                    throw RuntimeException("curInfo.uri.getPath() or parentInfo.uri.getPath() returned null")
                }
            } else {
                pathBuilder.append(curInfo.displayName)

                if (prevInfo == null || !curInfo.uri.toString()
                        .startsWith(prevInfo.uri.toString())
                ) {
                    prevInfo = curInfo
                }
            }

            paths.add(pathBuilder.toString())
            pathNames.add(curInfo.displayName)
        }
    }

    private fun MutableList<StorageInfo>.removeChildren() {
        fun StorageInfo.isParentOf(other: StorageInfo): Boolean =
            other.uri.toString().startsWith(this.uri.toString())

        var currentParent: StorageInfo? = null

        retainAll { curInfo ->
            when {
                currentParent == null -> {
                    currentParent = curInfo
                    true
                }

                currentParent!!.isParentOf(curInfo) -> {
                    false
                }

                else -> {
                    currentParent = curInfo
                    true
                }
            }
        }
    }

    data class StorageInfo(@JvmField var displayName: String, @JvmField val uri: Uri) {
        val isFileUri: Boolean = uri.scheme == ContentResolver.SCHEME_FILE
        val isContentUri: Boolean = uri.scheme == ContentResolver.SCHEME_CONTENT

        @Throws(JSONException::class)
        fun toJSON(): JSONObject {
            return JSONObject().apply {
                put(KEY_DISPLAY_NAME, displayName)
                put(KEY_URI, uri.toString())
            }
        }

        companion object {
            private const val KEY_DISPLAY_NAME = "DisplayName"
            private const val KEY_URI = "Uri"

            @JvmStatic
            @Throws(JSONException::class)
            fun fromJSON(jsonObject: JSONObject): StorageInfo { // TODO: Use Result after migrate callee to Kotlin
                val displayName = jsonObject.getString(KEY_DISPLAY_NAME)
                val uri = jsonObject.getString(KEY_URI).toUri()

                return StorageInfo(displayName, uri)
            }
        }
    }

    companion object {
        const val PACKET_TYPE_SFTP = "kdeconnect.sftp"
        const val PACKET_TYPE_SFTP_REQUEST = "kdeconnect.sftp.request"
        private val server = SimpleSftpServer()
    }
}

object SftpPluginInfo : PluginInfo(
    instantiableClass = SftpPlugin::class.java,
    displayNameRes = R.string.pref_plugin_sftp,
    descriptionRes = R.string.pref_plugin_sftp_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_SFTP_REQUEST),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_SFTP),
), KoinComponent {
    private val dataStore: SftpSettingsDataStore by inject()
    override fun checkRequiredPermissions(context: Context): Boolean {
        return if (SimpleSftpServer.SUPPORTS_NATIVEFS) {
            Environment.isExternalStorageManager()
        } else {
            getStorageInfoList().isNotEmpty()
        }
    }

    override fun getPermissionExplanationDialog(context: Context): DialogFragment
        = if (SimpleSftpServer.SUPPORTS_NATIVEFS) {
            StartActivityAlertDialogFragment.Builder()
                .setTitle(getDisplayName(context))
                .setMessage(R.string.sftp_manage_storage_permission_explanation)
                .setPositiveButton(R.string.open_settings)
                .setNegativeButton(R.string.cancel)
                .setIntentAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setIntentUrl("package:" + BuildConfig.APPLICATION_ID)
                .setStartForResult(true)
                .setRequestCode(MainActivity.RESULT_NEEDS_RELOAD)
                .create()
        } else {
            DeviceSettingsAlertDialogFragment.Builder()
                .setTitle(getDisplayName(context))
                .setMessage(R.string.sftp_saf_permission_explanation)
                .setPositiveButton(R.string.ok)
                .setNegativeButton(R.string.cancel)
                .setDeviceId(null)
                .setPluginKey(pluginKey)
                .create()
        }

    fun getStorageInfoList(): MutableList<StorageInfo> {
        val storageInfoList = mutableListOf<StorageInfo>()

        val jsonString = dataStore.getStorageInfoListJsonBlocking()

        try {
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                storageInfoList.add(StorageInfo.fromJSON(jsonArray.getJSONObject(i)))
            }
        } catch (e: JSONException) {
            Log.e("SFTPSettings", "Couldn't load storage info", e)
        }

        return storageInfoList
    }
}
