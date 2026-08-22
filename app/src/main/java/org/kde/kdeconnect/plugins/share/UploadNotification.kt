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
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect_tp.R

internal class UploadNotification(private val device: Device, private val context: Context, private val jobId: Int) {
    private val notificationManager: NotificationManager? = ContextCompat.getSystemService(context, NotificationManager::class.java)
    private var builder: NotificationCompat.Builder = NotificationCompat.Builder(context, NotificationHelper.Channels.FILETRANSFER_UPLOAD)
        .setSmallIcon(R.drawable.arrow_upward)
        .setAutoCancel(true)
        .setOngoing(true)
        .setProgress(100, 0, true)
    private val notificationId: Int = jobId

    init {
        addCancelAction()
    }

    fun addCancelAction() {
        val cancelIntent = Intent(context, ShareBroadcastReceiver::class.java)
        cancelIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        cancelIntent.action = SharePlugin.ACTION_CANCEL_SHARE
        cancelIntent.putExtra(SharePlugin.CANCEL_SHARE_DATA_TRANSFER_JOB_ID_EXTRA, jobId)
        cancelIntent.putExtra(SharePlugin.CANCEL_SHARE_DEVICE_ID_EXTRA, device.deviceId)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(
            R.drawable.cancel,
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
    }

    fun setFailed(message: String?) {
        setFinished(message)
        builder.setSmallIcon(R.drawable.error)
            .setChannelId(NotificationHelper.Channels.FILETRANSFER_ERROR)
    }

    fun cancel() {
        notificationManager?.cancel(notificationId)
    }

    fun getNotification(): Notification {
        return builder.build()
    }

    fun getNotificationId(): Int {
        return notificationId
    }

    fun show() {
        notificationManager?.notify(notificationId, builder.build())
    }
}

