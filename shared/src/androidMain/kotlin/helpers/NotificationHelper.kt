/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.notification_channel_default
import org.kde.kdeconnect.generated.resources.notification_channel_filetransfer
import org.kde.kdeconnect.generated.resources.notification_channel_filetransfer_complete
import org.kde.kdeconnect.generated.resources.notification_channel_filetransfer_error
import org.kde.kdeconnect.generated.resources.notification_channel_filetransfer_upload
import org.kde.kdeconnect.generated.resources.notification_channel_high_priority
import org.kde.kdeconnect.generated.resources.notification_channel_keepwatching
import org.kde.kdeconnect.generated.resources.notification_channel_media_control
import org.kde.kdeconnect.generated.resources.notification_channel_persistent
import org.kde.kdeconnect.generated.resources.notification_channel_receivenotification

object NotificationHelper {
    fun initializeChannels(context: Context) {
        val persistentChannel = NotificationChannelCompat.Builder(Channels.PERSISTENT, NotificationManagerCompat.IMPORTANCE_MIN)
            .setName(runBlocking { getString(Res.string.notification_channel_persistent) })
            .build()
        val defaultChannel = NotificationChannelCompat.Builder(Channels.DEFAULT, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(runBlocking { getString(Res.string.notification_channel_default) })
            .build()
        val mediaChannel = NotificationChannelCompat.Builder(Channels.MEDIA_CONTROL, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(runBlocking { getString(Res.string.notification_channel_media_control) })
            .build()
        val fileTransferDownloadChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_DOWNLOAD, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(runBlocking { getString(Res.string.notification_channel_filetransfer) })
            .setVibrationEnabled(false)
            .build()
        val fileTransferDownloadCompleteChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_COMPLETE, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(runBlocking { getString(Res.string.notification_channel_filetransfer_complete) })
            .setVibrationEnabled(false)
            .build()
        val fileTransferUploadChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_UPLOAD, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(runBlocking { getString(Res.string.notification_channel_filetransfer_upload) })
            .setVibrationEnabled(false)
            .build()
        val fileTransferErrorChannel = NotificationChannelCompat.Builder(Channels.FILETRANSFER_ERROR, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(runBlocking { getString(Res.string.notification_channel_filetransfer_error) })
            .setVibrationEnabled(false)
            .build()
        val receiveNotificationChannel = NotificationChannelCompat.Builder(Channels.RECEIVENOTIFICATION, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(runBlocking { getString(Res.string.notification_channel_receivenotification) })
            .build()
        val  highPriorityChannel= NotificationChannelCompat.Builder(Channels.HIGHPRIORITY, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(runBlocking { getString(Res.string.notification_channel_high_priority) })
            .build()
        /* This notification should be highly visible *only* if the user looks at their phone */
        /* It should not be a distraction. It should be a convenient button to press          */
        val continueWatchingChannel = NotificationChannelCompat.Builder(Channels.CONTINUEWATCHING, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(runBlocking { getString(Res.string.notification_channel_keepwatching) })
            .setVibrationEnabled(false)
            .setLightsEnabled(false)
            .setSound(null, null)
            .build()
        val channels = listOf(
            persistentChannel,
            defaultChannel, mediaChannel, fileTransferDownloadChannel, fileTransferDownloadCompleteChannel, fileTransferUploadChannel,
            fileTransferErrorChannel, receiveNotificationChannel, highPriorityChannel,
            continueWatchingChannel
        )

        val nm = NotificationManagerCompat.from(context)

        nm.createNotificationChannelsCompat(channels)

        // Delete any notification channels which weren't added.
        // Use this to deprecate old channels.
        nm.deleteUnlistedNotificationChannels(channels.map { channel -> channel.id })
    }

    object Channels {
        const val PERSISTENT: String = "persistent"
        const val DEFAULT: String = "default"
        const val MEDIA_CONTROL: String = "media_control"

        const val FILETRANSFER_DOWNLOAD: String = "filetransfer"
        const val FILETRANSFER_UPLOAD: String = "filetransfer_upload"
        const val FILETRANSFER_ERROR: String = "filetransfer_error"
        const val FILETRANSFER_COMPLETE: String = "filetransfer_complete"

        const val RECEIVENOTIFICATION: String = "receive"
        const val HIGHPRIORITY: String = "highpriority"
        const val CONTINUEWATCHING: String = "continuewatching"
    }
}
