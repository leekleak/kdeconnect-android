/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 * SPDX-FileCopyrightText: 2021 Ilmaz Gumerov <ilmaz1309@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect_tp.BuildConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardListener(private val context: Context) {
    interface ClipboardObserver {
        fun clipboardChanged(content: String)
    }

    private val observers: HashSet<ClipboardObserver> = HashSet()

    var currentContent: String? = null
        private set
    var updateTimestamp: Long = 0
        private set

    private lateinit var cm: ClipboardManager

    init {
        CoroutineScope(Dispatchers.IO).launch {
            cm = ContextCompat.getSystemService(context, ClipboardManager::class.java)!!
            cm.addPrimaryClipChangedListener { this@ClipboardListener.onClipboardChanged() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ClipboardPlugin.canSyncAutomatically(context)) {
                try {
                    val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                    // Listen only ClipboardService errors after now
                    val logcatFilter = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.VANILLA_ICE_CREAM) { "E ClipboardService" } else { "ClipboardService:E" }
                    val process = Runtime.getRuntime().exec(arrayOf<String>("logcat", "-T", timeStamp, logcatFilter, "*:S"))
                    val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                    bufferedReader.forEachLine { line ->
                        if (line.contains(BuildConfig.APPLICATION_ID)) {
                            context.startActivity(ClipboardFloatingActivity.getIntent(context, false))
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun registerObserver(observer: ClipboardObserver) {
        observers.add(observer)
    }

    fun removeObserver(observer: ClipboardObserver) {
        observers.remove(observer)
    }

    fun onClipboardChanged() {
        try {
            val item = cm.primaryClip!!.getItemAt(0)
            val content = item.coerceToText(context).toString()

            if (content == currentContent) {
                return
            }
            updateTimestamp = System.currentTimeMillis()
            currentContent = content

            for (observer in observers) {
                observer.clipboardChanged(content)
            }
        } catch (_: Exception) {
            //Probably clipboard was not text
        }
    }

    fun setText(text: String?) {
        if (this::cm.isInitialized) {
            updateTimestamp = System.currentTimeMillis()
            currentContent = text
            cm.setPrimaryClip(ClipData(ClipDescription("KDE Connect Clipboard Sync", arrayOf(MIMETYPE_TEXT_PLAIN)), ClipData.Item(text) ))
        }
    }

    companion object {
        private var instanceRef: WeakReference<ClipboardListener>? = null
        val instance: ClipboardListener?
            get() = instanceRef?.get()

        fun instance(context: Context): ClipboardListener {
            // FIXME: The _instance we return won't be completely initialized yet since initialization happens on a new thread (why?)
            return instance ?: ClipboardListener(context).also { instanceRef = WeakReference(it) }
        }
    }
}
