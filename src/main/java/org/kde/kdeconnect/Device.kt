/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.DeviceStats.countReceived
import org.kde.kdeconnect.DeviceStats.countSent
import org.kde.kdeconnect.PairingHandler.Companion.getVerificationKey
import org.kde.kdeconnect.PairingHandler.Companion.getVerificationKeyV7
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLink.PacketReceiver
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.PairingActivity
import org.koin.core.annotation.InjectedParam
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.scope.Scope
import java.io.IOException
import java.security.cert.Certificate
import kotlin.time.Duration.Companion.milliseconds

class Device(
    private val context: Context,
    private val deviceSettings: DeviceSettings,
    private val sslHelper: SslHelper,
    @InjectedParam deviceId: String,
    @InjectedParam link: BaseLink? = null
) : PacketReceiver, KoinScopeComponent {

    override val scope: Scope by lazy { createScope(this) }

    private val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: StateFlow<DeviceState>
        field = MutableStateFlow(DeviceState(
            deviceInfo = link?.deviceInfo ?: runBlocking { deviceSettings.getDeviceInfo(deviceId) ?: throw RuntimeException("Device $deviceId not found in settings") },
            pairState = if (link == null) PairState.Paired else PairState.NotPaired,
            supportedPlugins = PluginFactory.availablePlugins.toList(),
        ))

    private val stateUpdateMutex = Mutex()
    private suspend fun updateState(transform: (DeviceState) -> DeviceState) = stateUpdateMutex.withLock {
        val newState = reloadPluginsFromSettings(transform(state.value))
        state.update { newState }
        jobScope.launch { if (newState.deviceInfo.trusted) deviceSettings.addTrustedDevice(newState.deviceInfo) }
    }

    val deviceId: String get() = state.value.deviceInfo.id
    val certificate: Certificate get() = sslHelper.parseCertificate(state.value.deviceInfo.certificate)
    val deviceInfo: DeviceInfo get() = state.value.deviceInfo
    val loadedPlugins: Map<String, Plugin> get() = state.value.loadedPlugins
    val isReachable: Boolean get() = state.value.isReachable
    val name: String get() = deviceInfo.name
    val icon: Drawable get() = deviceInfo.type.getIcon(context)
    val deviceType: DeviceType get() = deviceInfo.type
    val protocolVersion: Int get() = deviceInfo.protocolVersion

    internal val pairingHandler: PairingHandler = PairingHandler(
        device = this,
        callback = createDefaultPairingCallback(),
    )

    private fun supportsPacketType(type: String): Boolean =
        NetworkPacket.PROTOCOL_PACKET_TYPES.contains(type) || deviceInfo.incomingCapabilities.contains(type)

    init {
        runBlocking {
            link?.let { addLink(it) }
        }
    }

    internal fun updatePairState(pairState: PairState, timestamp: Long) {
        val key = if (protocolVersion >= 8) {
            if (pairState != PairState.Requested && pairState != PairState.RequestedByPeer) {
                null
            } else {
                Log.e("Device timestampo", timestamp.toString())
                getVerificationKey(sslHelper.certificate, certificate, timestamp)
            }
        } else {
            getVerificationKeyV7(sslHelper.certificate, certificate)
        }
        jobScope.launch { updateState { it.copy(pairState = pairState, verificationKey = key) } }
    }

    // Returns 0 if the version matches, < 0 if it is older or > 0 if it is newer
    fun compareProtocolVersion(): Int =
        deviceInfo.protocolVersion - DeviceHelper.PROTOCOL_VERSION

    val isPaired: Boolean get() = state.value.pairState == PairState.Paired

    suspend fun requestPairing() {
        pairingHandler.requestPairing()
    }

    suspend fun unpair() {
        pairingHandler.unpair()
    }

    /* This method is called after accepting pair request form GUI */
    suspend fun acceptPairing() {
        Log.i("Device", "Accepted pair request started by the other device")
        pairingHandler.acceptPairing()
    }

    /* This method is called after rejecting pairing from GUI */
    suspend fun cancelPairing() {
        Log.i("Device", "This side cancelled the pair request")
        pairingHandler.cancelPairing()
    }

    private fun createDefaultPairingCallback(): PairingCallback {
        return object : PairingCallback {
            override fun incomingPairRequest() {
                val intent = Intent(context, PairingActivity::class.java).apply {
                    putExtra("deviceId", deviceId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }

            override fun pairingSuccessful() {
                Log.i("Device", "pairing successful, adding to trusted devices list")

                jobScope.launch {
                    updateState {
                        it.copy(
                            deviceInfo = it.deviceInfo.copy(trusted = true),
                            pairState = PairState.Paired,
                        )
                    }
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_DEVICE_ID, deviceId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }

            override fun pairingFailed(error: Int) {
            }

            override fun unpaired(device: Device) {
                assert(device == this@Device)
                Log.i("Device", "unpaired, removing from trusted devices list")
                jobScope.launch {
                    updateState {
                        it.copy(
                            deviceInfo = it.deviceInfo.copy(trusted = false),
                            pairState = PairState.NotPaired,
                            batteryInfo = null,
                            verificationKey = null,
                        )
                    }
                }
                jobScope.launch { deviceSettings.removeTrustedDevice(deviceId) }
            }
        }
    }

    suspend fun addLink(link: BaseLink){
        link.addPacketReceiver(this@Device)

        updateState { state ->
            state.copy(
                links = (state.links + link).sortedByDescending { it.linkProvider.priority }
            )
        }
    }

    @WorkerThread
    suspend fun removeLink(link: BaseLink) {
        link.removePacketReceiver(this)
        updateState { state ->
            val newLinks = state.links.minus(link)

            Log.i("KDE/Device", "removeLink: ${link.linkProvider.name} -> $name active links: ${newLinks.size}")
            state.copy(links = newLinks)
        }
    }

    suspend fun updateDeviceInfo(newDeviceInfo: DeviceInfo) {
        Log.e("Updating device info", "${newDeviceInfo.name}: ${newDeviceInfo.protocolVersion}")
        val updatedSupportedPlugins: List<String> = PluginFactory.pluginsForCapabilities(
            newDeviceInfo.incomingCapabilities,
            newDeviceInfo.outgoingCapabilities
        ).toList()

        updateState { state ->
            state.copy(
                deviceInfo = state.deviceInfo.copy(
                    name = newDeviceInfo.name,
                    type = newDeviceInfo.type,
                    protocolVersion = newDeviceInfo.protocolVersion,
                    outgoingCapabilities = newDeviceInfo.outgoingCapabilities,
                    incomingCapabilities = newDeviceInfo.incomingCapabilities,
                    settings = deviceInfo.settings
                ).withPopulatedSettings(),
                supportedPlugins = updatedSupportedPlugins
            )
        }
        Log.e("Updated device info", "${newDeviceInfo.name}: ${state.value.deviceInfo.protocolVersion}")
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Unit = stateUpdateMutex.withLock {
        countReceived(deviceId, np.type)

        if (NetworkPacket.PACKET_TYPE_PAIR == np.type) {
            Log.i("KDE/Device", "Pair packet")
            pairingHandler.packetReceived(np)
            return
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

    private suspend fun notifyPluginPacketReceived(np: NetworkPacket) {
        val targetPlugins = state.value.pluginsByIncomingInterface[np.type] // Returns an empty collection if the key doesn't exist
        if (targetPlugins == null) {
            Log.e("Device", "Ignoring packet with type ${np.type} because no plugin can handle it")

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
     * Send `np` over one of this device's connected links.
     *
     * @param np                        the packet to send
     * @param callback                  a callback that can receive realtime updates
     * won't return until the Payload has been received by the
     * other end, or times out after 10 seconds
     * @return true if the packet was sent ok, false otherwise
     * @see BaseLink.sendPacket
     */
    @WorkerThread
    suspend fun sendPacket(
        np: NetworkPacket,
        callback: SendPacketStatusCallback = defaultCallback,
    ): Boolean {
        while (!isReachable) delay(200.milliseconds)
        if (!supportsPacketType(np.type)) {
            Log.e("KDE/sendPacket", "Tried to send an unsupported packet type ${np.type} to: ${deviceInfo.name}")
            return false
        }

        val currentLinks = state.value.links
        val success = currentLinks.any { link ->
            try {
                link.sendPacket(np, callback)
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
                "No device link (of ${currentLinks.size} available) could send the packet. Packet ${np.type} to ${deviceInfo.name} lost!"
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

    suspend fun setPluginEnabled(pluginKey: String, value: Boolean) = withContext(Dispatchers.IO) {
        updateState { it.copy(deviceInfo = it.deviceInfo.copy(settings = it.deviceInfo.settings + (pluginKey to value))) }
    }

    @WorkerThread
    fun reloadPluginsFromSettings(deviceState: DeviceState): DeviceState {
        Log.i("Device", "${this@Device.deviceInfo.name}: reloading plugins")
        val newPluginsByIncomingInterface: MutableMap<String, MutableList<String>> = mutableMapOf()
        val deviceInfo = deviceState.deviceInfo

        val oldLoadedPlugins = deviceState.loadedPlugins
        val newLoadedPlugins = mutableMapOf<String, Plugin>()

        deviceState.supportedPlugins.forEach { pluginKey ->
            val pluginInfo = PluginFactory.getPluginInfo(pluginKey)

            val pluginEnabled = deviceState.pairState == PairState.Paired && deviceState.isReachable && (deviceInfo.settings[pluginKey] == true)

            if (pluginEnabled) {
                val plugin = oldLoadedPlugins[pluginKey] ?: PluginFactory.instantiatePluginForDevice(pluginKey, this)

                if (plugin != null && plugin.isCompatible) {
                    newLoadedPlugins[pluginKey] = plugin

                    pluginInfo.supportedPacketTypes.forEach { packetType ->
                        newPluginsByIncomingInterface.getOrPut(packetType){mutableListOf()}.add(pluginKey)
                    }
                }
            }
        }

        oldLoadedPlugins.filter { !newLoadedPlugins.containsKey(it.key) }.forEach { (pluginKey, plugin) ->
            runCatching {
                plugin.onDestroy()
            }.onFailure {
                Log.e("KDE/removePlugin", "Exception calling onDestroy for plugin $pluginKey")
            }
        }

        newLoadedPlugins.filter { !oldLoadedPlugins.containsKey(it.key) }.forEach { (pluginKey, plugin) ->
            runCatching {
                plugin.onCreate()
            }.onFailure {
                Log.e("KDE/addPlugin", "plugin failed to load $pluginKey", it)
            }
        }

        return deviceState.copy(
            pluginsByIncomingInterface = newPluginsByIncomingInterface,
            loadedPlugins = newLoadedPlugins
        )
    }

    internal fun updateBatteryInfo(newInfo: DeviceBatteryInfo) {
        jobScope.launch {
            updateState { it.copy(batteryInfo = newInfo) }
        }
    }

    fun close() {
        jobScope.cancel()
        scope.close()
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
    val pairState: PairState,
    val batteryInfo: DeviceBatteryInfo? = null,
    val verificationKey: String? = null,
    val loadedPlugins: Map<String, Plugin> = emptyMap(),
    val supportedPlugins: List<String> = emptyList(),
    val pluginsByIncomingInterface: Map<String, List<String>> = emptyMap(),
    val links: List<BaseLink> = emptyList(),
) {
    val isReachable: Boolean get() = links.isNotEmpty()
}
