/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.R

internal class UploadNotification(private val device: Device, private val context: Context, private val jobId: Long) {
    private val notificationManager: NotificationManager? = ContextCompat.getSystemService(context, NotificationManager::class.java)
    private var builder: NotificationCompat.Builder = NotificationCompat.Builder(context, NotificationHelper.Channels.FILETRANSFER_UPLOAD)
        .setSmallIcon(R.drawable.ic_arrow_upward_black_24dp)
        .setAutoCancel(true)
        .setOngoing(true)
        .setProgress(100, 0, true)
    private val notificationId: Int = System.currentTimeMillis().toInt()

    init {
        addCancelAction()
    }

    fun addCancelAction() {
        val cancelIntent = Intent(context, ShareBroadcastReceiver::class.java)
        cancelIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        cancelIntent.action = SharePlugin.ACTION_CANCEL_SHARE
        cancelIntent.putExtra(SharePlugin.CANCEL_SHARE_BACKGROUND_JOB_ID_EXTRA, jobId)
        cancelIntent.putExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA, device.deviceId)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(
            R.drawable.ic_reject_pairing_24dp,
            context.getString(R.string.cancel),
            cancelPendingIntent
        )
    }

    fun setTitle(title: String?) {
        builder.setContentTitle(title)
        builder.setTicker(title)
    }

    fun setProgress(progress: Int, progressMessage: String?) {
        builder.setProgress(100, progress, false)
        builder.setContentText(progressMessage)
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(progressMessage))
    }

    fun setFinished(message: String?) {
        builder = NotificationCompat.Builder(
            context,
            NotificationHelper.Channels.FILETRANSFER_UPLOAD
        )
        builder.setContentTitle(message)
            .setTicker(message)
            .setSmallIcon(R.drawable.check_circle)
            .setAutoCancel(true)
            .setOngoing(false)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean("share_notification_preference", true)) {
            builder.setDefaults(Notification.DEFAULT_ALL)
        }
    }

    fun setFailed(message: String?) {
        setFinished(message)
        builder.setSmallIcon(R.drawable.ic_error_outline_48dp)
            .setChannelId(NotificationHelper.Channels.FILETRANSFER_ERROR)
    }

    fun cancel() {
        notificationManager?.cancel(notificationId)
    }

    fun show() {
        notificationManager?.notify(notificationId, builder.build())
    }
}

