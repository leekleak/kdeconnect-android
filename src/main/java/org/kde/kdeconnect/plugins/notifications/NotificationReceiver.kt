/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex

class NotificationReceiver : NotificationListenerService() {
    var isConnected: Boolean = false
        private set

    interface NotificationListener {
        fun onNotificationPosted(statusBarNotification: StatusBarNotification)

        fun onNotificationRemoved(statusBarNotification: StatusBarNotification)

        fun onListenerConnected(service: NotificationReceiver)
    }

    private val listeners = ArrayList<NotificationListener>()

    fun addListener(listener: NotificationListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: NotificationListener) {
        listeners.remove(listener)
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification) {
        //Log.e("NotificationReceiver.onNotificationPosted","listeners: " + listeners.size());
        for (listener in listeners) {
            listener.onNotificationPosted(statusBarNotification)
        }
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification) {
        for (listener in listeners) {
            listener.onNotificationRemoved(statusBarNotification)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        for (listener in listeners) {
            listener.onListenerConnected(this)
        }
        this.isConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        this.isConnected = false
    }

    //This will be called for each intent launch, even if the service is already started and is reused
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runBlocking { mutex.lock() }
        try {
            for (c in callbacks) { c(this) }
            callbacks.clear()
        } finally {
            mutex.unlock()
        }
        return START_STICKY
    }


    companion object {
        // Reading notifications uses a different kind of permission, because it was added before the runtime permissions model
        fun hasReadNotificationsPermission(context: Context): Boolean {
            val notificationListenerList = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (notificationListenerList == null) {
                return false
            }
            val thisComponentName =
                ComponentName(context, NotificationReceiver::class.java).flattenToString()
            return notificationListenerList.contains(thisComponentName)
        }


        private val callbacks = ArrayList<(NotificationReceiver) -> Unit>()

        private val mutex: Mutex = Mutex()

        suspend fun runCommand(c: Context?, callback: ((NotificationReceiver) -> Unit)?) {
            if (callback != null) {
                mutex.lock()
                try {
                    callbacks.add(callback)
                } finally {
                    mutex.unlock()
                }
            }
            val serviceIntent = Intent(c, NotificationReceiver::class.java)
            c?.startService(serviceIntent)
        }
    }
}
