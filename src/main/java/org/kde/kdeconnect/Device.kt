/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.collections4.MultiValuedMap
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap
import org.kde.kdeconnect.DeviceInfo.Companion.loadFromSettings
import org.kde.kdeconnect.DeviceStats.countReceived
import org.kde.kdeconnect.DeviceStats.countSent
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLink.PacketReceiver
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.helpers.TrustedDevices
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect_tp.R
import org.koin.core.component.KoinComponent
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import java.io.IOException
import java.security.cert.Certificate
import java.util.Vector
import java.util.concurrent.CopyOnWriteArrayList

class Device : PacketReceiver, KoinComponent {

    data class NetworkPacketWithCallback(val np : NetworkPacket, val callback: SendPacketStatusCallback)
    val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun launchPairStateSync() {
        jobScope.launch {
            combine(pairingHandler.state, pairingHandler.verificationKey) { a, b ->
                a to b
            }.collect { (pairStatus, verificationKey) ->
                updateState { it.copy(pairStatus = pairStatus, verificationKey = verificationKey) }
            }
        }
    }

    private val _state: MutableStateFlow<DeviceState>
    val state: StateFlow<DeviceState>
    private fun updateState(transform: (DeviceState) -> DeviceState) = _state.update(transform)

    val deviceId: String get() = state.value.deviceInfo.id
    val certificate: Certificate get() = state.value.deviceInfo.certificate
    val deviceInfo: DeviceInfo get() = state.value.deviceInfo
    val loadedPlugins: Map<String, Plugin> get() = state.value.loadedPlugins
    val supportedPlugins: List<String> get() = state.value.supportedPlugins
    val pluginsWithoutPermissions: Map<String, Plugin> get() = state.value.pluginsWithoutPermissions
    val isReachable: Boolean get() = state.value.isReachable
    val verificationKey: String? get() = state.value.verificationKey

    private val context: Context

    val koinScope: Scope

    /**
     * The notification ID for the pairing notification.
     * This ID should be only set once, and it should be unique for each device.
     * We use the current time in milliseconds as the ID as default.
     */
    private var notificationId = 0

    @VisibleForTesting
    var pairingHandler: PairingHandler

    private val links = CopyOnWriteArrayList<BaseLink>()

    /**
     * Same as loadedPlugins but indexed by incoming packet type
     */
    private var pluginsByIncomingInterface: MultiValuedMap<String, String> = ArrayListValuedHashMap()

    private val pluginsChangedListeners = CopyOnWriteArrayList<PluginsChangedListener>()

    private val sendChannel = Channel<NetworkPacketWithCallback>(Channel.UNLIMITED)
    private var sendCoroutine : Job? = null

    /**
     * Constructor for remembered, already-trusted devices.
     * Given the deviceId, it will load the other properties from SharedPreferences.
     */
    internal constructor(context: Context, deviceId: String) {
        this.context = context
        val deviceInfo = loadFromSettings(context, deviceId)
        val supportedPlugins = Vector(PluginFactory.availablePlugins) // Assume all are supported until we receive capabilities
        this._state = MutableStateFlow(DeviceState(
            deviceInfo = deviceInfo,
            pairStatus = PairingHandler.PairState.Paired,
            isReachable = false,
            verificationKey = null,
            loadedPlugins = emptyMap(),
            pluginsWithoutPermissions = emptyMap(),
            supportedPlugins = supportedPlugins
        ))
        this.state = _state.asStateFlow()
        this.pairingHandler = PairingHandler(this, createDefaultPairingCallback(), PairingHandler.PairState.Paired)
        this.koinScope = getKoin().getOrCreateScope(deviceId, named<Device>(), this)
        launchPairStateSync()
        Log.i("Device", "Loading trusted device: ${deviceInfo.name}")
    }

    /**
     * Constructor for devices discovered but not trusted yet.
     * Gets the DeviceInfo by calling link.getDeviceInfo() on the link passed.
     * This constructor also calls addLink() with the link you pass to it, since it's not legal to have an unpaired Device with 0 links.
     */
    internal constructor(context: Context, link: BaseLink) {
        this.context = context
        val deviceInfo = link.deviceInfo
        val supportedPlugins = Vector(PluginFactory.availablePlugins) // Assume all are supported until we receive capabilities
        this._state = MutableStateFlow(DeviceState(
            deviceInfo = deviceInfo,
            pairStatus = PairingHandler.PairState.NotPaired,
            isReachable = false,
            verificationKey = null,
            loadedPlugins = emptyMap(),
            pluginsWithoutPermissions = emptyMap(),
            supportedPlugins = supportedPlugins
        ))
        this.state = _state.asStateFlow()
        this.pairingHandler = PairingHandler(this, createDefaultPairingCallback(), PairingHandler.PairState.NotPaired)
        this.koinScope = getKoin().getOrCreateScope(deviceInfo.id, named<Device>(), this)
        launchPairStateSync()
        Log.i("Device", "Creating untrusted device: " + deviceInfo.name)
        addLink(link)
    }

    fun supportsPacketType(type: String): Boolean =
        NetworkPacket.PROTOCOL_PACKET_TYPES.contains(type) || deviceInfo.incomingCapabilities?.contains(type) ?: true

    fun interface PluginsChangedListener {
        fun onPluginsChanged(device: Device)
    }

    val connectivityType: String?
        get() = links.firstOrNull()?.name

    val name: String
        get() = deviceInfo.name

    val icon: Drawable
        get() = deviceInfo.type.getIcon(context)

    val iconDrawable: Int
        @DrawableRes
        get() = deviceInfo.type.toDrawableId()

    val deviceType: DeviceType
        get() = deviceInfo.type

    val protocolVersion: Int
        get() = deviceInfo.protocolVersion

    // Returns 0 if the version matches, < 0 if it is older or > 0 if it is newer
    fun compareProtocolVersion(): Int =
        deviceInfo.protocolVersion - DeviceHelper.PROTOCOL_VERSION

    val isPaired: Boolean
        get() = state.value.pairStatus == PairingHandler.PairState.Paired

    fun requestPairing() {
        pairingHandler.requestPairing()
    }

    fun unpair() = pairingHandler.unpair()

    /* This method is called after accepting pair request form GUI */
    fun acceptPairing() {
        Log.i("Device", "Accepted pair request started by the other device")
        pairingHandler.acceptPairing()
    }

    /* This method is called after rejecting pairing from GUI */
    fun cancelPairing() {
        Log.i("Device", "This side cancelled the pair request")
        pairingHandler.cancelPairing()
    }

    private fun createDefaultPairingCallback(): PairingCallback {
        return object : PairingCallback {
            override fun incomingPairRequest() {
                displayPairingNotification()
            }

            override fun pairingSuccessful() {
                Log.i("Device", "pairing successful, adding to trusted devices list")

                hidePairingNotification()

                // Store current device certificate so we can check it in the future (TOFU)
                deviceInfo.saveInSettings(context)

                // Store as trusted device
                TrustedDevices.addTrustedDevice(context, deviceInfo.id)

                try {
                    reloadPluginsFromSettings()
                } catch (e: Exception) {
                    Log.e("Device", "Exception in pairingSuccessful. Not unpairing because saving the trusted device succeeded", e)
                }
            }

            override fun pairingFailed(error: Int) {
                hidePairingNotification()
            }

            override fun unpaired(device: Device) {
                assert(device == this@Device)
                Log.i("Device", "unpaired, removing from trusted devices list")
                TrustedDevices.removeTrustedDevice(context, deviceInfo.id)

                notifyPluginsOfDeviceUnpaired(context, deviceInfo.id)

                reloadPluginsFromSettings()
            }
        }
    }

    //
    // Notification related methods used during pairing
    //
    fun displayPairingNotification() {
        hidePairingNotification()

        notificationId = System.currentTimeMillis().toInt()

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_PENDING)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_ACCEPTED)
        }
        val rejectIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
            putExtra(MainActivity.PAIR_REQUEST_STATUS, MainActivity.PAIRING_REJECTED)
        }

        val acceptedPendingIntent = PendingIntent.getActivity(
            context,
            2,
            acceptIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val rejectedPendingIntent = PendingIntent.getActivity(
            context,
            4,
            rejectIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val res = context.resources

        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)!!

        val noti = NotificationCompat.Builder(context, NotificationHelper.Channels.DEFAULT)
            .setContentTitle(res.getString(R.string.pairing_request_from, name))
            .setContentText(res.getString(R.string.pairing_verification_code, verificationKey))
            .setTicker(res.getString(R.string.pair_requested))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_accept_pairing_24dp, res.getString(R.string.pairing_accept), acceptedPendingIntent)
            .addAction(R.drawable.ic_reject_pairing_24dp, res.getString(R.string.pairing_reject), rejectedPendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(notificationId, noti)
    }

    fun hidePairingNotification() {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java)!!
        notificationManager.cancel(notificationId)
    }

    fun addLink(link: BaseLink) {
        synchronized(sendChannel) {
            if (sendCoroutine == null) {
                sendCoroutine = CoroutineScope(Dispatchers.IO).launch {
                    for ((np, callback) in sendChannel) {
                        sendPacketBlocking(np, callback)
                    }
                }
            }
        }

        // FilesHelper.LogOpenFileCount();
        links.add(link)

        links.sortWith { o1, o2 ->
            o2.linkProvider.priority compareTo o1.linkProvider.priority
        }

        link.addPacketReceiver(this)

        updateState { it.copy(isReachable = true) }

        val hasChanges = updateDeviceInfo(link.deviceInfo)

        if (hasChanges || links.size == 1) {
            reloadPluginsFromSettings()
        }
    }

    @WorkerThread
    fun removeLink(link: BaseLink) {
        // FilesHelper.LogOpenFileCount();

        link.removePacketReceiver(this)
        links.remove(link)
        Log.i(
            "KDE/Device",
            "removeLink: ${link.linkProvider.name} -> $name active links: ${links.size}"
        )
        if (links.isEmpty()) {
            updateState { it.copy(isReachable = false) }
            reloadPluginsFromSettings()
            synchronized(sendChannel) {
                sendCoroutine?.cancel(CancellationException("Device disconnected"))
                sendCoroutine = null
            }
        }
    }

    fun updateDeviceInfo(newDeviceInfo: DeviceInfo): Boolean {
        var hasChanges = false
        val currentInfo = deviceInfo

        var updatedInfo = currentInfo
        if (currentInfo.name != newDeviceInfo.name || currentInfo.type != newDeviceInfo.type || currentInfo.protocolVersion != newDeviceInfo.protocolVersion) {
            hasChanges = true
            updatedInfo = updatedInfo.copy(
                name = newDeviceInfo.name,
                type = newDeviceInfo.type,
                protocolVersion = newDeviceInfo.protocolVersion
            )
        }

        val oldIncomingCapabilities = currentInfo.incomingCapabilities
        val oldOutgoingCapabilities = currentInfo.outgoingCapabilities
        val newIncomingCapabilities = newDeviceInfo.incomingCapabilities
        val newOutgoingCapabilities = newDeviceInfo.outgoingCapabilities
        var updatedSupportedPlugins: List<String>? = null

        if (
            !newIncomingCapabilities.isNullOrEmpty() &&
            !newOutgoingCapabilities.isNullOrEmpty() &&
            (
                oldIncomingCapabilities != newIncomingCapabilities ||
                oldOutgoingCapabilities != newOutgoingCapabilities
            )
        ) {
            hasChanges = true
            Log.i("updateDeviceInfo", "Updating supported plugins according to new capabilities")
            updatedInfo = updatedInfo.copy(
                outgoingCapabilities = newOutgoingCapabilities,
                incomingCapabilities = newIncomingCapabilities
            )
            updatedSupportedPlugins = PluginFactory.pluginsForCapabilities(
                newIncomingCapabilities,
                newOutgoingCapabilities
            ).toList()
        }

        if (hasChanges) {
            updateState { state ->
                state.copy(
                    deviceInfo = updatedInfo,
                    supportedPlugins = updatedSupportedPlugins ?: state.supportedPlugins
                )
            }
            if (isPaired) {
                updatedInfo.saveInSettings(context)
            }
        }

        return hasChanges
    }

    override fun onPacketReceived(np: NetworkPacket) {
        countReceived(deviceId, np.type)

        if (NetworkPacket.PACKET_TYPE_PAIR == np.type) {
            Log.i("KDE/Device", "Pair packet")
            pairingHandler.packetReceived(np)
            return
        }

        // pluginsByIncomingInterface may not be built yet
        if (pluginsByIncomingInterface.isEmpty) {
            reloadPluginsFromSettings()
        }

        if (!isPaired) {
            // If it is pair packet, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs,
            // but other don't know like unpairing when wi-fi is off.

            unpair()
        }

        // The following code when `isPaired == false` is NOT USED.
        // It adds support for receiving packets from not trusted devices,
        // but as of March 2023 no plugin implements "onUnpairedDevicePacketReceived".
        notifyPluginPacketReceived(np)
    }

    private fun notifyPluginPacketReceived(np: NetworkPacket) {
        val targetPlugins = pluginsByIncomingInterface[np.type] // Returns an empty collection if the key doesn't exist
        if (targetPlugins.isEmpty()) {
            Log.w("Device", "Ignoring packet with type ${np.type} because no plugin can handle it")

            // If there is a payload close it to not leak sockets
            np.payload?.close()
            return
        }
        targetPlugins
            .asSequence()
            .mapNotNull { loadedPlugins[it] }
            .forEach { plugin ->
                runCatching {
                    if (isPaired) {
                        plugin.onPacketReceived(np)
                    } else {
                        plugin.onUnpairedDevicePacketReceived(np)
                    }
                }.onFailure { e ->
                    Log.e("Device", "Exception in ${plugin.pluginKey}'s onPacketReceived()", e)
                }
            }
    }

    abstract class SendPacketStatusCallback {
        abstract fun onSuccess()

        abstract fun onFailure(e: Throwable)

        open fun onPayloadProgressChanged(percent: Int) {}
    }

    private val defaultCallback: SendPacketStatusCallback = object : SendPacketStatusCallback() {
        override fun onSuccess() {
        }

        override fun onFailure(e: Throwable) {
            Log.e("Device", "Send packet exception", e)
        }
    }

    /**
     * Send a packet to the device asynchronously
     * @param np The packet
     * @param callback A callback for success/failure
     */
    @AnyThread
    fun sendPacket(np: NetworkPacket, callback: SendPacketStatusCallback) {
        sendChannel.trySend(NetworkPacketWithCallback(np, callback))
    }

    @AnyThread
    fun sendPacket(np: NetworkPacket) = sendPacket(np, defaultCallback)

    @WorkerThread
    fun sendPacketBlocking(np: NetworkPacket, callback: SendPacketStatusCallback): Boolean =
        sendPacketBlocking(np, callback, false)

    @WorkerThread
    fun sendPacketBlocking(np: NetworkPacket): Boolean = sendPacketBlocking(np, defaultCallback, false)

    /**
     * Send `np` over one of this device's connected [.links].
     *
     * @param np                        the packet to send
     * @param callback                  a callback that can receive realtime updates
     * @param sendPayloadFromSameThread when set to true and np contains a Payload, this function
     * won't return until the Payload has been received by the
     * other end, or times out after 10 seconds
     * @return true if the packet was sent ok, false otherwise
     * @see BaseLink.sendPacket
     */
    @WorkerThread
    fun sendPacketBlocking(
        np: NetworkPacket,
        callback: SendPacketStatusCallback,
        sendPayloadFromSameThread: Boolean
    ): Boolean {
        if (!supportsPacketType(np.type)) {
            Log.e("KDE/sendPacket", "Tried to send an unsupported packet type ${np.type} to: ${deviceInfo.name}")
            return false
        }

        val success = links.any { link ->
            try {
                link.sendPacket(np, callback, sendPayloadFromSameThread)
            } catch (e: IOException) {
                Log.w("KDE/sendPacket", "Failed to send packet", e)
                false
            }.also { sent ->
                countSent(deviceId, np.type, sent)
            }
        }

        if (!success) {
            Log.e(
                "KDE/sendPacket",
                "No device link (of ${links.size} available) could send the packet. Packet ${np.type} to ${deviceInfo.name} lost!"
            )
        }

        return success
    }

    //
    // Plugin-related functions
    //
    fun <T : Plugin> getPlugin(pluginClass: Class<T>): T? {
        val plugin = getPlugin(getPluginKey(pluginClass))
        return plugin?.let(pluginClass::cast)
    }

    fun getPlugin(pluginKey: String): Plugin? = loadedPlugins[pluginKey]

    fun getPluginIncludingWithoutPermissions(pluginKey: String): Plugin? {
        return loadedPlugins[pluginKey] ?: pluginsWithoutPermissions[pluginKey]
    }

    fun setPluginEnabled(pluginKey: String, value: Boolean) {
        TrustedDevices.getDeviceSettings(context, deviceId).edit { putBoolean(pluginKey, value) }
        reloadPluginsFromSettings()
    }

    fun isPluginEnabled(pluginKey: String): Boolean {
        val enabledByDefault = PluginFactory.getPluginInfo(pluginKey).isEnabledByDefault
        return TrustedDevices.getDeviceSettings(context, deviceId).getBoolean(pluginKey, enabledByDefault)
    }

    fun notifyPluginsOfDeviceUnpaired(context: Context, deviceId: String) {
        for (pluginKey in supportedPlugins) {
            // This is a hacky way to temporarily create plugins just so that they can be notified of the
            // device being unpaired. This else part will only come into picture when 1) the user tries to
            // unpair a device while that device is not reachable or 2) the plugin was never initialized
            // for this device, e.g., the plugins that need additional permissions from the user, and those
            // permissions were never granted.
            val plugin = getPlugin(pluginKey) ?: PluginFactory.instantiatePluginForDevice(context, pluginKey, this)
            plugin?.onDeviceUnpaired(context, deviceId)
        }
    }

    fun launchBackgroundReloadPluginsFromSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            reloadPluginsFromSettings()
        }
    }

    @Synchronized
    @WorkerThread
    fun reloadPluginsFromSettings() {
        Log.i("Device", "${deviceInfo.name}: reloading plugins")
        val newPluginsByIncomingInterface: MultiValuedMap<String, String> = ArrayListValuedHashMap()

        val oldLoadedPlugins = loadedPlugins
        val newLoadedPlugins = mutableMapOf<String, Plugin>()
        val newPluginsWithoutPermissions = mutableMapOf<String, Plugin>()

        supportedPlugins.forEach { pluginKey ->
            val pluginInfo = PluginFactory.getPluginInfo(pluginKey)

            val pluginEnabled = isPaired && this.isReachable && isPluginEnabled(pluginKey)

            if (pluginEnabled) {
                val isNewPlugin = !oldLoadedPlugins.containsKey(pluginKey)
                val plugin = oldLoadedPlugins[pluginKey]
                    ?: PluginFactory.instantiatePluginForDevice(context, pluginKey, this)

                if (plugin != null && plugin.isCompatible) {
                    val requiredPermissionsGranted = plugin.preferences?.let {
                        plugin.pluginInfo.checkRequiredPermissions(it, context)
                    } ?: plugin.pluginInfo.checkRequiredPermissions(context)

                    newLoadedPlugins[pluginKey] = plugin
                    if (!requiredPermissionsGranted) {
                        newPluginsWithoutPermissions[pluginKey] = plugin
                    }

                    if (isNewPlugin) {
                        runCatching { plugin.onCreate() }.onFailure {
                            Log.e("KDE/addPlugin", "plugin failed to load $pluginKey", it)
                        }
                    }

                    pluginInfo.supportedPacketTypes.forEach { packetType ->
                        newPluginsByIncomingInterface.put(packetType, pluginKey)
                    }
                }
            }
        }

        // Handle onDestroy for plugins that are no longer loaded
        oldLoadedPlugins.forEach { (pluginKey, plugin) ->
            if (!newLoadedPlugins.containsKey(pluginKey)) {
                try {
                    plugin.onDestroy()
                } catch (e: Exception) {
                    Log.e("KDE/removePlugin", "Exception calling onDestroy for plugin $pluginKey", e)
                }
            }
        }

        updateState { it.copy(
            loadedPlugins = newLoadedPlugins,
            pluginsWithoutPermissions = newPluginsWithoutPermissions,
        ) }

        pluginsByIncomingInterface = newPluginsByIncomingInterface

        onPluginsChanged()
    }

    fun onPluginsChanged() = pluginsChangedListeners.forEach { it.onPluginsChanged(this) }

    fun addPluginsChangedListener(listener: PluginsChangedListener) = pluginsChangedListeners.add(listener)

    fun removePluginsChangedListener(listener: PluginsChangedListener) = pluginsChangedListeners.remove(listener)

    fun disconnect() {
        links.forEach(BaseLink::disconnect)
    }

    fun close() {
        jobScope.cancel()
        koinScope.close()
    }

    override fun toString(): String {
        return "Device(name=$name, id=$deviceId)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Device) return false
        // There should never be two instances of Device if they have the same ID
        return deviceId == other.deviceId
    }

    override fun hashCode(): Int {
        return deviceId.hashCode()
    }
}

data class DeviceState(
    val deviceInfo: DeviceInfo,
    val pairStatus: PairingHandler.PairState,
    val isReachable: Boolean,
    val verificationKey: String?,
    val loadedPlugins: Map<String, Plugin>,
    val pluginsWithoutPermissions: Map<String, Plugin>,
    val supportedPlugins: List<String>,
)
