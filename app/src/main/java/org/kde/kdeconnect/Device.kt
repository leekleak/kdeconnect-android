/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect

import androidx.annotation.WorkerThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.kde.kdeconnect.DeviceStats.countReceived
import org.kde.kdeconnect.DeviceStats.countSent
import org.kde.kdeconnect.PairingHandler.Companion.getVerificationKey
import org.kde.kdeconnect.PairingHandler.Companion.getVerificationKeyV7
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLink.PacketReceiver
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.Plugin.Companion.getPluginKey
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.koin.core.annotation.InjectedParam
import org.koin.core.component.KoinScopeComponent
import org.koin.core.component.createScope
import org.koin.core.scope.Scope
import java.io.IOException
import java.security.cert.Certificate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Device(
    internal val deviceSettings: DeviceSettings,
    private val sslHelper: SslHelper,
    pairingCallbackFactory: (Device) -> PairingHandler.PairingCallback,
    @InjectedParam deviceInfo: DeviceInfo,
) : PacketReceiver, KoinScopeComponent {

    override val scope: Scope by lazy { createScope(this) }

    internal val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pluginReloadMutex = Mutex()

    private val loadedPlugins: MutableStateFlow<Map<String, Plugin>> = MutableStateFlow(emptyMap())

    val state: StateFlow<DeviceState>
        field = MutableStateFlow(
            DeviceState(
                deviceInfo = deviceInfo,
                pairState = if (deviceInfo.trusted) PairState.Paired else PairState.NotPaired,
                supportedPlugins = emptyList(),
            )
        )

    private fun calculateButtons(deviceState: DeviceState): List<PluginUiButton> {
        return deviceState.supportedPlugins.flatMap { pluginKey ->
            val pluginInfo = runCatching { PluginFactory.getPluginInfo(pluginKey) }.getOrNull()
            pluginInfo?.getUiButtons(this) ?: emptyList()
        }
    }

    private fun calculateIncomingInterfaces(deviceState: DeviceState): Map<String, List<String>> {
        val mapping = mutableMapOf<String, MutableList<String>>()
        deviceState.supportedPlugins.forEach { pluginKey ->
            val pluginInfo = runCatching { PluginFactory.getPluginInfo(pluginKey) }.getOrNull()
            pluginInfo?.supportedPacketTypes?.forEach { packetType ->
                mapping.getOrPut(packetType) { mutableListOf() }.add(pluginKey)
            }
        }
        return mapping
    }

    internal fun updateState(transform: (DeviceState) -> DeviceState) {
        state.update { oldState ->
            val newState = transform(oldState)
            val pluginsChanged = newState.supportedPlugins != oldState.supportedPlugins

            val uiButtons = if (!pluginsChanged && oldState.uiButtons.isNotEmpty()) {
                oldState.uiButtons
            } else {
                calculateButtons(newState)
            }

            val incomingInterfaces = if (!pluginsChanged && oldState.pluginsByIncomingInterface.isNotEmpty()) {
                oldState.pluginsByIncomingInterface
            } else {
                calculateIncomingInterfaces(newState)
            }

            newState.copy(
                uiButtons = uiButtons,
                pluginsByIncomingInterface = incomingInterfaces
            )
        }
    }

    val deviceId: String get() = state.value.deviceInfo.id
    val certificate: Certificate get() = sslHelper.parseCertificate(state.value.deviceInfo.certificate)
    val deviceInfo: DeviceInfo get() = state.value.deviceInfo
    val isReachable: Boolean get() = state.value.isReachable
    val name: String get() = deviceInfo.name
    val iconRes: Int get() = deviceInfo.type.toDrawableRes()
    val deviceType: DeviceType get() = deviceInfo.type
    val protocolVersion: Int get() = deviceInfo.protocolVersion

    internal val pairingHandler: PairingHandler = PairingHandler(
        device = this,
        callback = pairingCallbackFactory(this),
    )

    private fun supportsPacketType(type: String): Boolean =
        NetworkPacket.PROTOCOL_PACKET_TYPES.contains(type) || deviceInfo.incomingCapabilities.contains(type)

    init {
        jobScope.launch {
            state.distinctUntilChanged { old, new ->
                old.supportedPlugins == new.supportedPlugins &&
                old.pairState == new.pairState &&
                old.isReachable == new.isReachable &&
                old.deviceInfo.settings == new.deviceInfo.settings
            }.collect { reloadNonLazyPlugins(it) }
        }
        jobScope.launch {
            state.map { it.pairState to it.deviceInfo }
                .distinctUntilChanged()
                .collect { (pairState, info) ->
                    if (pairState == PairState.Paired) {
                        deviceSettings.addTrustedDevice(info)
                    }
                }
        }
    }

    internal fun updatePairState(pairState: PairState, timestamp: Long) {
        val key = if (protocolVersion >= 8) {
            if (pairState != PairState.Requested && pairState != PairState.RequestedByPeer) {
                null
            } else {
                getVerificationKey(sslHelper.certificate, certificate, timestamp)
            }
        } else {
            getVerificationKeyV7(sslHelper.certificate, certificate)
        }
        updateState { it.copy(pairState = pairState, verificationKey = key) }
    }

    val isPaired: Boolean get() = state.value.pairState == PairState.Paired

    suspend fun requestPairing() {
        pairingHandler.requestPairing()
    }

    suspend fun unpair() {
        pairingHandler.unpair()
    }

    /* This method is called after accepting pair request form GUI */
    suspend fun acceptPairing() {
        LoggerTagged.i { "Accepted pair request started by the other device" }
        pairingHandler.acceptPairing()
    }

    /* This method is called after rejecting pairing from GUI */
    suspend fun cancelPairing() {
        LoggerTagged.i { "This side cancelled the pair request" }
        pairingHandler.cancelPairing()
    }

    fun addLink(link: BaseLink) {
        updateDeviceInfo(link.deviceInfo)
        updateState {
            it.copy(
                links = (it.links + link).sortedByDescending { link -> link.linkProvider.priority }
            )
        }

        link.addPacketReceiver(this)
    }

    @WorkerThread
    fun removeLink(link: BaseLink) {
        link.removePacketReceiver(this)
        updateState { state ->
            val newLinks = state.links.minus(link)

            LoggerTagged.i { "removeLink: ${link.linkProvider.name} -> $name active links: ${newLinks.size}" }
            state.copy(links = newLinks)
        }
    }

    fun updateDeviceInfo(newDeviceInfo: DeviceInfo) {
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
    }

    override suspend fun onPacketReceived(np: NetworkPacket) {
        countReceived(deviceId, np.type)

        if (NetworkPacket.PACKET_TYPE_PAIR == np.type) {
            LoggerTagged.i { "Pair packet" }
            pairingHandler.packetReceived(np)
            return
        }

        if (!isPaired) {
            // If it is pair packet, it should be captured by "if" at start
            // If not and device is paired, it should be captured by isPaired
            // Else unpair, this handles the situation when one device unpairs,
            // but other don't know like unpairing when wi-fi is off.

            unpair()
        } else {
            notifyPluginPacketReceived(np)
        }
    }

    private suspend fun notifyPluginPacketReceived(np: NetworkPacket) {
        val targetPlugins = state.value.pluginsByIncomingInterface[np.type] // Returns an empty collection if the key doesn't exist
        if (targetPlugins == null) {
            LoggerTagged.e { "Ignoring packet with type ${np.type} because no plugin can handle it" }

            // If there is a payload close it to not leak sockets
            np.payload?.close()
            return
        }
        targetPlugins
            .mapNotNull { getPlugin(it) } // This triggers lazy loading if needed
            .forEach { plugin ->
                runCatching {
                    plugin.onPacketReceived(np)
                }.onFailure { e ->
                    LoggerTagged.e(e) { "Exception in ${plugin.pluginKey}'s onPacketReceived()" }
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
            LoggerTagged.e(e) { "Send packet exception" }
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
        val reachable = withTimeoutOrNull(1.seconds) {
            while (!isReachable) delay(200.milliseconds)
        } != null

        if (!reachable) {
            return false
        }

        if (!supportsPacketType(np.type)) {
            LoggerTagged.e { "Tried to send an unsupported packet type ${np.type} to: ${deviceInfo.name}" }
            return false
        }

        val currentLinks = state.value.links
        val success = currentLinks.any { link ->
            try {
                link.sendPacket(np, callback)
            } catch (e: IOException) {
                LoggerTagged.w(e) { "Failed to send packet" }
                false
            }.also { sent ->
                countSent(deviceId, np.type, sent)
            }
        }

        if (!success) {
            LoggerTagged.e {
                "No device link (of ${currentLinks.size} available) could send the packet. Packet ${np.type} to ${deviceInfo.name} lost!"
            }
        }

        return success
    }

    //
    // Plugin-related functions
    //
    suspend fun <T : Plugin> getPlugin(pluginClass: Class<T>): T? {
        val plugin = getPlugin(getPluginKey(pluginClass))
        return plugin?.let(pluginClass::cast)
    }

    suspend fun getPlugin(pluginKey: String): Plugin? {
        loadedPlugins.value[pluginKey]?.let { return it }

        val pluginInfo = runCatching { PluginFactory.getPluginInfo(pluginKey) }.getOrNull() ?: return null
        if (!pluginInfo.lazy) return null

        val currentState = state.value
        if (pluginKey !in currentState.supportedPlugins) return null

        val pluginEnabled = currentState.pairState == PairState.Paired
            && currentState.isReachable
            && (currentState.deviceInfo.settings[pluginKey] == true)
        if (!pluginEnabled) return null

        return pluginReloadMutex.withLock {
            LoggerTagged.i { "lazy loading $pluginKey" }
            loadedPlugins.value[pluginKey]?.let { return@withLock it }

            val plugin = PluginFactory.instantiatePluginForDevice(pluginKey, this@Device) ?: return@withLock null
            if (!plugin.isCompatible) return@withLock null

            runCatching { plugin.onCreate() }
                .onFailure { LoggerTagged.e(it) { "plugin failed to load $pluginKey" } }

            loadedPlugins.update { it + (pluginKey to plugin) }

            plugin
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T : Plugin> pluginFlow(pluginClass: Class<T>): Flow<T?> {
        val pluginKey = getPluginKey(pluginClass)
        return state.map { s ->
            s.pairState == PairState.Paired
                    && s.isReachable
                    && (pluginKey in s.supportedPlugins)
                    && (s.deviceInfo.settings[pluginKey] == true)
        }.distinctUntilChanged().flatMapLatest { enabled ->
            if (enabled) {
                loadedPlugins.map { it[pluginKey] }.onStart {
                    if (loadedPlugins.value[pluginKey] == null) {
                        getPlugin(pluginKey)
                    }
                }
            } else {
                flowOf(null)
            }
        }.map { it?.let(pluginClass::cast) }.distinctUntilChanged()
    }

    suspend fun setPluginEnabled(pluginKey: String, value: Boolean) = withContext(Dispatchers.IO) {
        updateState { it.copy(deviceInfo = it.deviceInfo.copy(settings = it.deviceInfo.settings + (pluginKey to value))) }
    }

    private suspend fun reloadNonLazyPlugins(state: DeviceState) = pluginReloadMutex.withLock {
        LoggerTagged.i { "${this@Device.deviceInfo.name}: reloading plugins" }

        val info = state.deviceInfo
        val oldLoadedPlugins = loadedPlugins.value
        val newLoadedPlugins = mutableMapOf<String, Plugin>()

        if (state.pairState == PairState.Paired && isReachable) {
            state.supportedPlugins.forEach { pluginKey ->
                val pluginInfo = PluginFactory.getPluginInfo(pluginKey)

                val pluginEnabled = info.settings[pluginKey] == true

                if (pluginEnabled) {
                    if (!pluginInfo.lazy) {
                        val plugin = oldLoadedPlugins[pluginKey] ?: PluginFactory.instantiatePluginForDevice(pluginKey, this)

                        if (plugin != null && plugin.isCompatible) {
                            newLoadedPlugins[pluginKey] = plugin
                        }
                    } else if (oldLoadedPlugins.containsKey(pluginKey)) {
                        val plugin = oldLoadedPlugins[pluginKey]!!
                        if (plugin.isCompatible) {
                            newLoadedPlugins[pluginKey] = plugin
                        }
                    }
                }
            }
        }

        oldLoadedPlugins.filter { !newLoadedPlugins.containsKey(it.key) }.forEach { (pluginKey, plugin) ->
            runCatching {
                plugin.onDestroy()
            }.onFailure {
                LoggerTagged.e(it) { "Exception calling onDestroy for plugin $pluginKey" }
            }
        }

        newLoadedPlugins.filter { !oldLoadedPlugins.containsKey(it.key) }.forEach { (pluginKey, plugin) ->
            runCatching {
                plugin.onCreate()
            }.onFailure {
                LoggerTagged.e(it) { "plugin failed to load $pluginKey" }
            }
        }

        loadedPlugins.value = newLoadedPlugins
    }

    internal fun updateBatteryInfo(newInfo: DeviceBatteryInfo) {
        updateState { it.copy(batteryInfo = newInfo) }
    }

    fun updateShortcuts(shortcuts: List<String>) {
        updateState {
            it.copy(
                deviceInfo = it.deviceInfo.copy(
                    shortcuts = shortcuts
                )
            )
        }
    }

    fun kill() {
        val plugins = loadedPlugins.value
        plugins.forEach { (key, plugin) ->
            runCatching { plugin.onDestroy() }
                .onFailure { LoggerTagged.e(it) { "Exception calling onDestroy for plugin $key during kill" } }
        }
        loadedPlugins.value = emptyMap()
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
    val supportedPlugins: List<String> = emptyList(),
    val pluginsByIncomingInterface: Map<String, List<String>> = emptyMap(),
    val links: List<BaseLink> = emptyList(),
    val uiButtons: List<PluginUiButton> = emptyList(),
) {
    val isReachable: Boolean get() = links.isNotEmpty()
    val isPaired: Boolean get() = pairState == PairState.Paired
}
