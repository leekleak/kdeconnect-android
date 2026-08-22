package org.kde.kdeconnect.async

import android.app.Notification

interface DataTransferJob {
    val id: Int
    suspend fun run()
    fun cancel()
    fun getNotification(): Notification
    fun getNotificationId(): Int
}
