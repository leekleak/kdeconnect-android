/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.backends.bluetooth.BluetoothLinkProvider
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.helpers.PermissionHelper
import org.kde.kdeconnect.plugins.clipboard.ClipboardFloatingActivity
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * This class (still) does 3 things:
 * - Keeps the app running by creating a foreground notification.
 * - Holds references to the active LinkProviders, but doesn't handle the DeviceLink those create (the KdeConnect class does that).
 * - Listens for network connectivity changes and tells the LinkProviders to re-check for devices.
 * It can be started by the KdeConnectBroadcastReceiver on some events or when the MainActivity is launched.
 */
@OptIn(ExperimentalAtomicApi::class)
class BackgroundService : Service() {
    private val data: BackgroundServiceData by inject()
    private val deviceManager: DeviceManager by inject()
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val linkProviders = mutableListOf<BaseLinkProvider>()

    fun updateForegroundNotification(devices: Map<String, Device>) {
        // Update the foreground notification with the currently connected device list
        val notificationManager = getSystemService<NotificationManager>()
        notificationManager?.notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(devices))
    }

    private fun registerLinkProviders() {
        linkProviders.add(get<LanLinkProvider> { parametersOf(this) })
        //linkProviders.add(get<LoopbackLinkProvider> { parametersOf(this) })
        linkProviders.add(get<BluetoothLinkProvider> { parametersOf(this) })
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun onNetworkChange(network: Network?) {
        if (!initialized.load()) {
            Log.d(LOG_TAG, "ignoring onNetworkChange called before the service is initialized")
            return
        }
        Log.d(LOG_TAG, "onNetworkChange")
        for (linkProvider in linkProviders) {
            linkProvider.onNetworkChange(network)
        }
    }

    fun addConnectionListener(connectionReceiver: ConnectionReceiver) {
        for (linkProvider in linkProviders) {
            linkProvider.addConnectionReceiver(connectionReceiver)
        }
    }

    /** This will called only once, even if we launch the service intent several times */
    @MainThread
    override fun onCreate() {
        super.onCreate()
        Log.d("KdeConnect/BgService", "onCreate")
        instance = this

        serviceScope.launch {
            deviceManager.devices.collect { devices ->
                updateForegroundNotification(devices)
            }
        }

        // Register screen on listener
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        // See: https://developer.android.com/reference/android/net/ConnectivityManager.html#CONNECTIVITY_ACTION
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        registerReceiver(KdeConnectBroadcastReceiver(), filter)

        // Watch for changes on all network connections except cellular networks
        val networkRequestBuilder = createNonCellularNetworkRequestBuilder()
        val connectivityManager = this.getSystemService<ConnectivityManager>()
        connectivityManager?.registerNetworkCallback(networkRequestBuilder.build(), object : NetworkCallback() {

            // All callbacks runs on a dedicated thread that isn't the main thread
            override fun onAvailable(network: Network) {
                Log.i("BackgroundService", "Valid network available")
                data.setConnected(true)
                runBlocking { onNetworkChange(network) }
            }

            override fun onLost(network: Network) {
                Log.i("BackgroundService", "Valid network lost")
                data.setConnected(true)
            }
        })

        serviceScope.launch {
            registerLinkProviders()
            addConnectionListener(deviceManager.connectionListener) // Link Providers need to be already registered
            for (linkProvider in linkProviders) {
                linkProvider.onStart()
            }
            initialized.store(true)
        }
    }

    private fun createForegroundNotification(devices: Map<String, Device>): Notification {
        // Why is this needed: https://developer.android.com/guide/components/services#Foreground

        val connectedDevices = mutableListOf<String>()
        val connectedDeviceIds = mutableListOf<String>()
        for (device in devices.values) {
            if (device.isReachable && device.isPaired) {
                connectedDeviceIds.add(device.deviceId)
                connectedDevices.add(device.name)
            }
        }

        val intent = Intent(this, MainActivity::class.java)
        if (connectedDeviceIds.size == 1) {
            // Force open screen of the only connected device
            intent.putExtra(MainActivity.EXTRA_DEVICE_ID, connectedDeviceIds[0])
        }

        val pi = PendingIntent.getActivity(this, 0, intent, UPDATE_IMMUTABLE_FLAGS)
        val notification = NotificationCompat.Builder(this, NotificationHelper.Channels.PERSISTENT).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setContentIntent(pi)
            setPriority(NotificationCompat.PRIORITY_MIN) //MIN so it's not shown in the status bar before Oreo, on Oreo it will be bumped to LOW
            setShowWhen(false)
            setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            setAutoCancel(false)
            setGroup("BackgroundService")
        }

        if (connectedDevices.isEmpty()) {
            notification.setContentText(getString(R.string.foreground_notification_no_devices))
        }
        else {
            notification.setContentText(getString(R.string.foreground_notification_devices, connectedDevices.joinToString(", ")))

            // Adding an action button to send clipboard manually in Android 10 and later.
            if (!ClipboardPlugin.canSyncAutomatically(this)) {
                val sendClipboard = ClipboardFloatingActivity.getIntent(this, true)
                val sendPendingClipboard = PendingIntent.getActivity(this, 3, sendClipboard, UPDATE_IMMUTABLE_FLAGS)
                notification.addAction(0, getString(R.string.foreground_notification_send_clipboard), sendPendingClipboard)
            }
        }
        return notification.build()
    }

    override fun onDestroy() {
        Log.d("KdeConnect/BgService", "onDestroy")
        initialized.store(false)
        for (linkProvider in linkProviders) {
            linkProvider.onStop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "onStartCommand")

        if (!PermissionHelper.hasRequiredPermissions(this)) {
            Log.w(LOG_TAG, "BackgroundService started without required permissions, stopping")
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(emptyMap()), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } catch (e: IllegalStateException) { // To catch ForegroundServiceStartNotAllowedException
                Log.w("BackgroundService", "Couldn't startForeground", e)
                return START_STICKY
            }
        }
        else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(emptyMap()))
        }
        if (intent != null && intent.getBooleanExtra("refresh", false)) {
            runBlocking { onNetworkChange(null) }
        }
        return START_STICKY
    }

    companion object {
        const val LOG_TAG = "KDE/BackgroundService"

        const val UPDATE_IMMUTABLE_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private const val FOREGROUND_NOTIFICATION_ID = 1

        @JvmStatic
        var instance: BackgroundService? = null
            private set

        private var initialized = AtomicBoolean(false)

        private fun createNonCellularNetworkRequestBuilder(): NetworkRequest.Builder {
            return NetworkRequest.Builder().apply {
                addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                    addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                }
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S) {
                    addTransportType(NetworkCapabilities.TRANSPORT_USB)
                    addTransportType(NetworkCapabilities.TRANSPORT_LOWPAN)
                }
            }
        }

        fun start(context: Context) {
            Log.d(LOG_TAG, "Start")
            if (!PermissionHelper.hasRequiredPermissions(context)) {
                Log.w(LOG_TAG, "Skipping start because required permissions are not granted")
                return
            }
            val intent = Intent(context, BackgroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        @JvmStatic
        fun forceRefreshConnections(context: Context) {
            Log.d(LOG_TAG, "ForceRefreshConnections")
            if (!PermissionHelper.hasRequiredPermissions(context)) {
                Log.w(LOG_TAG, "Skipping forceRefreshConnections because required permissions are not granted")
                return
            }
            val intent = Intent(context, BackgroundService::class.java)
            intent.putExtra("refresh", true)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
