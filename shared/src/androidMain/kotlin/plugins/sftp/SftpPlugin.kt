/*
 * SPDX-FileCopyrightText: 2014 Samoilenko Yuri <kinnalru@gmail.com>
 * SPDX-FileCopyrightText: 2024 ShellWen Chen <me@shellwen.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.sftp

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.open_settings
import org.kde.kdeconnect.generated.resources.pref_plugin_sftp
import org.kde.kdeconnect.generated.resources.pref_plugin_sftp_desc
import org.kde.kdeconnect.generated.resources.sftp_manage_storage_permission_explanation
import org.kde.kdeconnect.generated.resources.sftp_missing_permission_error
import org.kde.kdeconnect.generated.resources.sftp_no_storage_locations_configured
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.helpers.getLocalIpAddress
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.Companion.PACKET_TYPE_SFTP
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.Companion.PACKET_TYPE_SFTP_REQUEST
import org.kde.kdeconnect.plugins.sftp.SftpPlugin.StorageInfo
import org.kde.kdeconnect.toJsonArray
import org.kde.kdeconnect.ui.PermissionRequest
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SftpPlugin(
    context: Context,
    device: Device,
    private val dataStore: SftpSettingsDataStore,
    private val permissionRequestHelper: PermissionRequestHelper
) : Plugin(context, device) {
    override val pluginInfo: SftpPluginInfo = SftpPluginInfo

    private var job: Job? = null

    override fun onCreate(): Boolean {
        job = CoroutineScope(Dispatchers.Main).launch {
            dataStore.storageInfoListJson.collect {
                if (!server.isStarted) return@collect

                server.stop()

                val np = NetworkPacket(PACKET_TYPE_SFTP_REQUEST).update {
                    put("startBrowsing", true)
                }
                onPacketReceived(np)
            }
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
        job?.cancel()
        job = null
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.getBoolean("startBrowsing") != true) return false

        if (!pluginInfo.checkRequiredPermissions(context)) {
            pluginInfo.showPermissionExplanation(context, permissionRequestHelper)
            val errorMessage = getString(Res.string.sftp_missing_permission_error)
            val noPermissionsPacket = NetworkPacket(PACKET_TYPE_SFTP).update {
                put("errorMessage", errorMessage)
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
                val errorMessage = getString(Res.string.sftp_no_storage_locations_configured)
                device.sendPacket(NetworkPacket(PACKET_TYPE_SFTP).update {
                    put("errorMessage", errorMessage)
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

        device.sendPacket(NetworkPacket(PACKET_TYPE_SFTP).update {
            put("ip", getLocalIpAddress()!!.hostAddress)
            put("port", server.port)
            put("user", SimpleSftpServer.USER)
            put("password", server.regeneratePassword())
            // Kept for compatibility, in case "multiPaths" is not possible or the other end does not support it
            put("path", if (paths.size == 1) paths[0] else "/")
            if (paths.isNotEmpty()) {
                put("multiPaths", paths.toJsonArray())
                put("pathNames", pathNames.toJsonArray())
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

        fun toJson(): JsonObject {
            return buildJsonObject {
                put(KEY_DISPLAY_NAME, displayName)
                put(KEY_URI, uri.toString())
            }
        }

        companion object {
            private const val KEY_DISPLAY_NAME = "DisplayName"
            private const val KEY_URI = "Uri"

            fun fromJson(jsonObject: JsonObject): StorageInfo {
                val displayName = jsonObject[KEY_DISPLAY_NAME]?.jsonPrimitive?.content ?: ""
                val uri = jsonObject[KEY_URI]?.jsonPrimitive?.content?.toUri()!!

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
    pluginKey = "SftpPlugin",
    instantiableClass = SftpPlugin::class.java,
    displayNameRes = Res.string.pref_plugin_sftp,
    descriptionRes = Res.string.pref_plugin_sftp_desc,
    supportedPacketTypes = arrayOf(PACKET_TYPE_SFTP_REQUEST),
    outgoingPacketTypes = arrayOf(PACKET_TYPE_SFTP),
    lazy = false
), KoinComponent {
    private val dataStore: SftpSettingsDataStore by inject()

    override suspend fun checkRequiredPermissions(context: Context): Boolean {
        return if (SimpleSftpServer.SUPPORTS_NATIVEFS) {
            Environment.isExternalStorageManager()
        } else {
            getStorageInfoList().isNotEmpty()
        }
    }

    override suspend fun getPermissionRequests(): List<PermissionRequest> {
        return buildList {
            if (SimpleSftpServer.SUPPORTS_NATIVEFS) {
                add(
                    PermissionRequest(
                        title = getString(displayNameRes),
                        description = getString(Res.string.sftp_manage_storage_permission_explanation),
                        intentAction = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        positiveButton = getString(Res.string.open_settings)
                    )
                )
            }
        }
    }

    suspend fun getStorageInfoList(): MutableList<StorageInfo> {
        val storageInfoList = mutableListOf<StorageInfo>()

        val jsonString = dataStore.storageInfoListJson.first()

        try {
            val jsonArray = Json.parseToJsonElement(jsonString) as JsonArray

            for (i in jsonArray.indices) {
                storageInfoList.add(StorageInfo.fromJson(jsonArray[i].jsonObject))
            }
        } catch (e: SerializationException) {
            LoggerTagged.e(e) { "Couldn't load storage info" }
        }

        return storageInfoList
    }
}
