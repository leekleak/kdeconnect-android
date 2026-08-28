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
import org.kde.kdeconnect.backends.AndroidLinkProvider
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.backends.http.HttpLinkProvider
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.foreground_notification_devices
import org.kde.kdeconnect.generated.resources.foreground_notification_no_devices
import org.kde.kdeconnect.generated.resources.foreground_notification_send_clipboard
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.helpers.PermissionHelper
import org.kde.kdeconnect.plugins.clipboard.ClipboardFloatingActivity
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext
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
        //linkProviders.add(get<LanLinkProvider>())
        linkProviders.add(get<HttpLinkProvider>())
        //linkProviders.add(get<LoopbackLinkProvider>())
        //linkProviders.add(get<BluetoothLinkProvider>())
    }

    suspend fun onNetworkChange(network: Network?) {
        if (!initialized.load()) {
            LoggerTagged.d { "ignoring onNetworkChange called before the service is initialized" }
            return
        }
        LoggerTagged.d { "onNetworkChange" }
        for (linkProvider in linkProviders) {
            (linkProvider as? AndroidLinkProvider)?.onNetworkChange(network)
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
        LoggerTagged.d { "onCreate" }
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
                LoggerTagged.i { "Valid network available" }
                data.setConnected(true)
                runBlocking { onNetworkChange(network) }
            }

            override fun onLost(network: Network) {
                LoggerTagged.i { "Valid network lost" }
                data.setConnected(false)
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

        val intent = Intent().setClassName(this, BuildConfig.MAIN_ACTIVITY_NAME)
        if (connectedDeviceIds.size == 1) {
            // Force open screen of the only connected device
            intent.putExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID, connectedDeviceIds[0])
        }

        val pi = PendingIntent.getActivity(this, 0, intent, UPDATE_IMMUTABLE_FLAGS)
        val notification = NotificationCompat.Builder(this, NotificationHelper.Channels.PERSISTENT).apply {
            setSmallIcon(R.drawable.ic_notification)
            setOngoing(true)
            setContentIntent(pi)
            priority = NotificationCompat.PRIORITY_MIN //MIN so it's not shown in the status bar before Oreo, on Oreo it will be bumped to LOW
            setShowWhen(false)
            foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            setAutoCancel(false)
            setGroup("BackgroundService")
        }

        if (connectedDevices.isEmpty()) {
            notification.setContentText(runBlocking { org.jetbrains.compose.resources.getString(Res.string.foreground_notification_no_devices) })
        }
        else {
            notification.setContentText(runBlocking { org.jetbrains.compose.resources.getString(Res.string.foreground_notification_devices, connectedDevices.joinToString(", ")) })

            // Adding an action button to send clipboard manually in Android 10 and later.
            if (!ClipboardPlugin.canSyncAutomatically(this)) {
                val sendClipboard = ClipboardFloatingActivity.getIntent(this, true)
                val sendPendingClipboard = PendingIntent.getActivity(this, 3, sendClipboard, UPDATE_IMMUTABLE_FLAGS)
                notification.addAction(0, runBlocking { org.jetbrains.compose.resources.getString(Res.string.foreground_notification_send_clipboard) }, sendPendingClipboard)
            }
        }
        return notification.build()
    }

    override fun onDestroy() {
        LoggerTagged.d { "onDestroy" }
        initialized.store(false)
        for (linkProvider in linkProviders) {
            linkProvider.onStop()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LoggerTagged.d { "onStartCommand" }

        if (!PermissionHelper.hasRequiredPermissions(this)) {
            LoggerTagged.w { "BackgroundService started without required permissions, stopping" }
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(emptyMap()), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } catch (e: IllegalStateException) { // To catch ForegroundServiceStartNotAllowedException
                LoggerTagged.w(e) { "Couldn't startForeground" }
                return START_STICKY
            }
        }
        else {
            startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification(emptyMap()))
        }
        if (intent != null && intent.getBooleanExtra("refresh", false)) {
            serviceScope.launch {
                onNetworkChange(null)
            }
        }
        return START_STICKY
    }

    companion object {
        const val UPDATE_IMMUTABLE_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private const val FOREGROUND_NOTIFICATION_ID = 1

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
            LoggerTagged.d { "Start" }
            if (!PermissionHelper.hasRequiredPermissions(context)) {
                LoggerTagged.w { "Skipping start because required permissions are not granted" }
                return
            }
            val intent = Intent(context, BackgroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun forceRefreshConnections() {
            LoggerTagged.d { "ForceRefreshConnections" }
            val context: Context = GlobalContext.get().get()
            if (!PermissionHelper.hasRequiredPermissions(context)) {
                LoggerTagged.w { "Skipping forceRefreshConnections because required permissions are not granted" }
                return
            }
            val intent = Intent(context, BackgroundService::class.java)
            intent.putExtra("refresh", true)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
