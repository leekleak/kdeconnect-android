/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect.async.BackgroundJobHandler
import org.kde.kdeconnect.async.BackgroundJobHandler.Companion.newFixedThreadPoolBackgroundJobHandler
import org.kde.kdeconnect.helpers.FilesHelper.uriToNetworkPacket
import org.kde.kdeconnect.helpers.IntentHelper.startActivityFromBackgroundOrCreateNotification
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL

/**
 * A Plugin for sharing and receiving files and uris.
 * 
 * 
 * All of the associated I/O work is scheduled on background
 * threads by [BackgroundJobHandler].
 * 
 */
class SharePlugin(context: Context, device: Device) : Plugin(context, device) {
    private val backgroundJobHandler: BackgroundJobHandler = newFixedThreadPoolBackgroundJobHandler(5)
    private val handler: Handler = Handler(Looper.getMainLooper())

    private var receiveFileJob: CompositeReceiveFileJob? = null
    private var uploadFileJob: CompositeUploadFileJob? = null
    private val receiveFileJobCallback: Callback = Callback()

    private val mSharedPrefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    override val pluginInfo: PluginInfo = SharePluginInfo

    override fun onCreate(): Boolean {
        createOrUpdateDynamicShortcut(null)
        // Deliver URLs previously shared to this device now that it's connected
        deliverPreviouslySentIntents()
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
        super.onDestroy()
    }

    private fun createOrUpdateDynamicShortcut(shortcutToUpdate: ShortcutInfoCompat?) {
        val isNewShortcut = shortcutToUpdate == null
        val icon = IconCompat.createWithResource(
            context, device.deviceType.toShortcutDrawableId()
        )
        val shortcutIntent: Intent = if (isNewShortcut) {
            val intent = Intent(context, MainActivity::class.java)
            intent.action = Intent.ACTION_VIEW
            intent.putExtra(MainActivity.EXTRA_DEVICE_ID, device.deviceId)
            intent
        } else shortcutToUpdate.intent
        val shortcut = ShortcutInfoCompat.Builder(context, device.deviceId)
            .setIntent(shortcutIntent)
            .setIcon(icon)
            .setShortLabel(
                if (isNewShortcut)
                    device.name
                else
                    context.getString(
                        R.string.unreachable_device_dynamic_shortcut,
                        shortcutToUpdate.shortLabel
                    )
            )
            .setCategories(
                (if (isNewShortcut) mutableSetOf("org.kde.kdeconnect.category.SHARE_TARGET") else
                    shortcutToUpdate.categories!!)
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

    private fun deliverPreviouslySentIntents() {
        val currentUrlSet = mSharedPrefs.getStringSet(KEY_UNREACHABLE_URL_LIST + device.deviceId, null) ?: return
        for (url in currentUrlSet) {
            val intent: Intent
            try {
                intent = Intent.parseUri(url, 0)
                intent.putExtra(Intent.EXTRA_TEXT, url)
            } catch (_: URISyntaxException) {
                Log.e("SharePlugin", "Malformed URI")
                continue
            }
            share(intent)
        }
        mSharedPrefs.edit {
            putStringSet(KEY_UNREACHABLE_URL_LIST + device.deviceId, null)
        }
    }

    override fun getUiButtons(): MutableList<PluginUiButton> {
        return mutableListOf(
            PluginUiButton(
                context.getString(R.string.send_files),
                R.drawable.share_plugin_action_24dp,
                ButtonCategory.SEND
            ) { parentActivity: Activity ->
                val intent = Intent(parentActivity, SendFileActivity::class.java)
                intent.putExtra("deviceId", device.deviceId)
                parentActivity.startActivity(intent)
            })
    }

    @WorkerThread
    override fun onPacketReceived(np: NetworkPacket): Boolean {
        try {
            if (np.type == PACKET_TYPE_SHARE_REQUEST_UPDATE) {
                receiveFileJob?.let {
                    if (it.isRunning) {
                        it.updateTotals(
                            np.getInt(KEY_NUMBER_OF_FILES), np.getLong(
                                KEY_TOTAL_PAYLOAD_SIZE
                            )
                        )
                    } else {
                        Log.d("SharePlugin", "Received update packet but CompositeUploadJob is not running")
                    }
                } ?: Log.d("SharePlugin", "Received update packet but CompositeUploadJob is null")

                return true
            }

            if (np.has("filename")) {
                receiveFile(np)
            } else if (np.has("text")) {
                Log.i("SharePlugin", "hasText")
                receiveText(np)
            } else if (np.has("url")) {
                receiveUrl(np)
            } else {
                Log.e("SharePlugin", "Error: Nothing attached!")
            }
        } catch (e: Exception) {
            Log.e("SharePlugin", "Exception")
            e.printStackTrace()
        }

        return true
    }

    private fun receiveUrl(np: NetworkPacket) {
        val url = np.getString("url")

        Log.i("SharePlugin", "hasUrl: $url")

        val browserIntent = Intent(Intent.ACTION_VIEW, url.toUri())
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        startActivityFromBackgroundOrCreateNotification(context, browserIntent, url)
    }

    private fun receiveText(np: NetworkPacket) {
        val text = np.getString("text")
        val cm = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        cm?.let {
            it.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.clipboard_toast), text))
            handler.post {
                Toast.makeText(context, R.string.shareplugin_text_saved, Toast.LENGTH_LONG).show()
            }
        }
    }

    @WorkerThread
    private fun receiveFile(np: NetworkPacket) {

        val hasNumberOfFiles = np.has(KEY_NUMBER_OF_FILES)
        val isOpen = np.getBoolean("open", false)

        val job = if (hasNumberOfFiles && !isOpen && receiveFileJob != null) {
            receiveFileJob!!
        } else {
            CompositeReceiveFileJob(device, receiveFileJobCallback)
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
            backgroundJobHandler.runJob(job)
        }
    }

    fun sendUriList(uriList: List<Uri>) {
        val job = uploadFileJob ?: CompositeUploadFileJob(device, this.receiveFileJobCallback)

        //Read all the data early, as we only have permissions to do it while the activity is alive
        for (uri in uriList) {
            val np = uriToNetworkPacket(context, uri, PACKET_TYPE_SHARE_REQUEST)

            if (np != null) {
                job.addNetworkPacket(np)
            }
        }

        if (job !== uploadFileJob) {
            uploadFileJob = job
            backgroundJobHandler.runJob(uploadFileJob!!)
        }
    }

    fun share(intent: Intent) {
        val streams = streamsFromIntent(intent)
        if (streams.isNotEmpty()) {
            sendUriList(streams)
            return
        }
        var text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!text.isNullOrEmpty()) {
            Log.i("SharePlugin", "Intent contains text to share")

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
            Log.e("SharePlugin", "There's nothing we know how to share")
        }
    }

    private fun streamsFromIntent(intent: Intent): List<Uri> {
        Log.i("SharePlugin", "Intent contains streams to share")
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
            Log.w("SharePlugin", "All streams were null")
        }
        return uriList
    }

    private inner class Callback : BackgroundJob.Callback<Void?> {
        override fun onResult(job: BackgroundJob<*, *>, result: Void?) {
            if (job === receiveFileJob) {
                receiveFileJob = null
            } else if (job === uploadFileJob) {
                uploadFileJob = null
            }
        }

        override fun onError(job: BackgroundJob<*, *>, error: Throwable) {
            if (job === receiveFileJob) {
                receiveFileJob = null
            } else if (job === uploadFileJob) {
                uploadFileJob = null
            }
        }
    }

    fun cancelJob(jobId: Long) {
        if (backgroundJobHandler.isRunning(jobId)) {
            val job = backgroundJobHandler.getJob(jobId) ?: return

            job.cancel()

            if (job === receiveFileJob) {
                receiveFileJob = null
            } else if (job === uploadFileJob) {
                uploadFileJob = null
            }
        }
    }

    override fun onDeviceUnpaired(context: Context, deviceId: String) {
        Log.i("KDE/SharePlugin", "onDeviceUnpaired deviceId = $deviceId")
        mSharedPrefs.edit { remove(KEY_UNREACHABLE_URL_LIST + deviceId) }
    }

    companion object {
        const val ACTION_CANCEL_SHARE: String = "org.kde.kdeconnect.plugins.share.CancelShare"
        const val CANCEL_SHARE_DEVICE_ID_EXTRA: String = "deviceId"
        const val CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA: String = "backgroundJobId"

        private const val PACKET_TYPE_SHARE_REQUEST = "kdeconnect.share.request"
        const val PACKET_TYPE_SHARE_REQUEST_UPDATE: String = "kdeconnect.share.request.update"

        const val KEY_NUMBER_OF_FILES: String = "numberOfFiles"
        const val KEY_TOTAL_PAYLOAD_SIZE: String = "totalPayloadSize"

        const val KEY_UNREACHABLE_URL_LIST: String = "key_unreachable_url_list"
    }
}
