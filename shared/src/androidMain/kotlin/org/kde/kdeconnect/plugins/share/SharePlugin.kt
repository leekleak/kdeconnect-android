/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.BuildConfig
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.toShortcutIconRes
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.DataTransferJob
import org.kde.kdeconnect.async.DataTransferJobRegistry
import org.kde.kdeconnect.async.DataTransferJobService
import org.kde.kdeconnect.async.JobCallback
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.clipboard_toast
import org.kde.kdeconnect.generated.resources.shareplugin_text_saved
import org.kde.kdeconnect.generated.resources.unreachable_device_dynamic_shortcut
import org.kde.kdeconnect.helpers.FilesHelper.uriToNetworkPacket
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * A Plugin for sharing and receiving files and uris.
 */
class SharePlugin(
    context: Context,
    device: Device,
    private val settingsDataStore: SettingsDataStore
) : Plugin(context, device) {
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val concurrencyLimit = Semaphore(permits = 5)
    private val activeJobs = ConcurrentHashMap<Int, Job>()

    private var receiveFileJob: CompositeReceiveFileJob? = null
    private var uploadFileJob: CompositeUploadFileJob? = null
    private val jobCallback: JobCallback = Callback()

    override val pluginInfo: PluginInfo = SharePluginInfo

    override fun onCreate(): Boolean {
        createOrUpdateDynamicShortcut(null)
        return true
    }

    override fun onDestroy() {
        for (shortcut in ShortcutManagerCompat.getDynamicShortcuts(context)) {
            if (shortcut.id != device.deviceId) continue
            if (!device.isReachable && shortcut.isPinned) {
                // Create an updated shortcut with the same ID
                createOrUpdateDynamicShortcut(shortcut)
                break
            } else {
                ShortcutManagerCompat.removeLongLivedShortcuts(
                    context,
                    listOf(shortcut.id)
                )
            }
        }
        pluginScope.cancel()
        super.onDestroy()
    }

    private fun createOrUpdateDynamicShortcut(shortcutToUpdate: ShortcutInfoCompat?) {
        val isNewShortcut = shortcutToUpdate == null
        val icon = IconCompat.createWithResource(
            context, device.deviceType.toShortcutIconRes()
        )
        val shortcutIntent: Intent = if (isNewShortcut) {
            val intent = Intent().setClassName(context.packageName, BuildConfig.MAIN_ACTIVITY_NAME)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID, device.deviceId)
            intent
        } else shortcutToUpdate.intent
        val shortcut = ShortcutInfoCompat.Builder(context, device.deviceId)
            .setIntent(shortcutIntent)
            .setIcon(icon)
            .setShortLabel(
                if (isNewShortcut)
                    device.name
                else
                    runBlocking { getString(
                        Res.string.unreachable_device_dynamic_shortcut,
                        shortcutToUpdate.shortLabel
                    )
                }
            )
            .setCategories(
                (if (isNewShortcut) mutableSetOf("org.kde.kdeconnect.category.SHARE_TARGET") else
                    shortcutToUpdate.categories ?: emptySet())
            )
            .setLocusId(
                if (isNewShortcut)
                    LocusIdCompat(device.deviceId)
                else
                    shortcutToUpdate.locusId
            )
            .build()
        if (isNewShortcut) {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } else {
            ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
        }
    }

    @WorkerThread
    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        try {
            if (np.type == PACKET_TYPE_SHARE_REQUEST_UPDATE) {
                receiveFileJob?.let {
                    if (it.isRunning.get()) {
                        it.updateTotals(
                            np.getInt(KEY_NUMBER_OF_FILES), np.getLong(
                                KEY_TOTAL_PAYLOAD_SIZE
                            )
                        )
                    } else {
                        LoggerTagged.d { "Received update packet but CompositeUploadJob is not running" }
                    }
                } ?: LoggerTagged.d { "Received update packet but CompositeUploadJob is null" }

                return true
            }

            if (np.has("filename")) {
                receiveFile(np)
            } else if (np.has("text")) {
                LoggerTagged.i { "hasText" }
                receiveText(np)
            } else if (np.has("url")) {
                receiveUrl(np)
            } else {
                LoggerTagged.e { "Error: Nothing attached!" }
            }
        } catch (e: Exception) {
            LoggerTagged.e(e) { "Exception" }
        }

        return true
    }

    private fun receiveUrl(np: NetworkPacket) {
        val url = np.getString("url")

        LoggerTagged.i { "hasUrl: $url" }

        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private suspend fun receiveText(np: NetworkPacket) {
        val text = np.getString("text")
        val cm = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        cm?.let {
            it.setPrimaryClip(ClipData.newPlainText(getString(Res.string.clipboard_toast), text))
            Toast.makeText(context, getString(Res.string.shareplugin_text_saved), Toast.LENGTH_LONG).show()
        }
    }

    @WorkerThread
    private fun receiveFile(np: NetworkPacket) {

        val hasNumberOfFiles = np.has(KEY_NUMBER_OF_FILES)
        val isOpen = np.getBoolean("open", false)

        val job = if (hasNumberOfFiles && !isOpen && receiveFileJob != null) {
            receiveFileJob!!
        } else {
            CompositeReceiveFileJob(
                DataTransferJobRegistry.generateJobId(),
                device,
                context,
                settingsDataStore,
                jobCallback
            )
        }

        if (!hasNumberOfFiles) {
            np[KEY_NUMBER_OF_FILES] = 1
            np[KEY_TOTAL_PAYLOAD_SIZE] = np.payloadSize
        }

        job.addNetworkPacket(np)

        if (job !== receiveFileJob) {
            if (hasNumberOfFiles && !isOpen) {
                receiveFileJob = job
            }
            runBackgroundJob(job)
        }
    }

    private fun runBackgroundJob(job: DataTransferJob) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val componentName = ComponentName(context, DataTransferJobService::class.java)

            DataTransferJobRegistry.register(job)

            val jobInfo = JobInfo.Builder(job.id, componentName)
                .setUserInitiated(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(PersistableBundle().apply {
                    putInt(DataTransferJobService.EXTRA_DATA_TRANSFER_JOB_ID, job.id)
                })
                .build()

            jobScheduler.schedule(jobInfo)
        } else {
            val coroutineJob = pluginScope.launch {
                concurrencyLimit.withPermit {
                    try {
                        job.run()
                    } catch (e: Exception) {
                        LoggerTagged.e(e) { "Failed to run background job" }
                    } finally {
                        activeJobs.remove(job.id)
                    }
                }
            }
            activeJobs[job.id] = coroutineJob
        }
    }

    fun sendUriList(uriList: List<Uri>) {
        val job = uploadFileJob ?: CompositeUploadFileJob(
            DataTransferJobRegistry.generateJobId(),
            device,
            context,
            jobCallback
        )

        //Read all the data early, as we only have permissions to do it while the activity is alive
        for (uri in uriList) {
            val np = uriToNetworkPacket(context, uri, PACKET_TYPE_SHARE_REQUEST)

            if (np != null) {
                job.addNetworkPacket(np)
            }
        }

        if (job !== uploadFileJob) {
            uploadFileJob = job
            runBackgroundJob(uploadFileJob!!)
        }
    }

    suspend fun share(intent: Intent) {
        val streams = streamsFromIntent(intent)
        if (streams.isNotEmpty()) {
            sendUriList(streams)
            return
        }
        var text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrEmpty()) {
            LoggerTagged.i { "Intent contains text to share" }

            //Hack: Detect shared youtube videos, so we can open them in the browser instead of as text
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            if (subject != null && subject.endsWith("YouTube")) {
                val index = text.indexOf(": http://youtu.be/")
                if (index > 0) {
                    text = text.substring(index + 2) //Skip ": "
                }
            }

            var isUrl: Boolean
            try {
                URL(text)
                isUrl = true
            } catch (_: MalformedURLException) {
                isUrl = false
            }
            val np = NetworkPacket(PACKET_TYPE_SHARE_REQUEST)
            np[if (isUrl) "url" else "text"] = text
            device.sendPacket(np)
        } else {
            LoggerTagged.e { "There's nothing we know how to share" }
        }
    }

    private fun streamsFromIntent(intent: Intent): List<Uri> {
        LoggerTagged.i { "Intent contains streams to share" }
        val uriList = if (Intent.ACTION_SEND_MULTIPLE == intent.action) {
            val list = IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            )
            list ?: emptyList()
        } else {
            listOfNotNull(
                intent.extras?.let {
                    BundleCompat.getParcelable(it, Intent.EXTRA_STREAM, Uri::class.java)
                }
            )
        }
        if (uriList.isEmpty()) {
            LoggerTagged.w { "All streams were null" }
        }
        return uriList
    }

    private inner class Callback : JobCallback {
        override fun onResult(jobId: Int) {
            if (receiveFileJob?.id == jobId) {
                receiveFileJob = null
            } else if (uploadFileJob?.id == jobId) {
                uploadFileJob = null
            }
            DataTransferJobRegistry.unregister(jobId)
        }

        override fun onError(jobId: Int, error: Throwable) {
            if (receiveFileJob?.id == jobId) {
                receiveFileJob = null
            } else if (uploadFileJob?.id == jobId) {
                uploadFileJob = null
            }
            DataTransferJobRegistry.unregister(jobId)
        }
    }

    fun cancelJob(jobId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(jobId)
        }

        DataTransferJobRegistry.get(jobId)?.cancel()
        activeJobs[jobId]?.cancel()
        activeJobs.remove(jobId)

        if (receiveFileJob?.id == jobId) {
            receiveFileJob = null
        }
        if (uploadFileJob?.id == jobId) {
            uploadFileJob = null
        }
        DataTransferJobRegistry.unregister(jobId)
    }

    companion object {
        const val ACTION_CANCEL_SHARE: String = "org.kde.kdeconnect.plugins.share.CancelShare"
        const val CANCEL_SHARE_DEVICE_ID_EXTRA: String = "deviceId"
        const val CANCEL_SHARE_DATA_TRANSFER_JOB_ID_EXTRA: String = "dataTransferJobId"

        private const val PACKET_TYPE_SHARE_REQUEST = "kdeconnect.share.request"
        const val PACKET_TYPE_SHARE_REQUEST_UPDATE: String = "kdeconnect.share.request.update"

        const val KEY_NUMBER_OF_FILES: String = "numberOfFiles"
        const val KEY_TOTAL_PAYLOAD_SIZE: String = "totalPayloadSize"
    }
}
