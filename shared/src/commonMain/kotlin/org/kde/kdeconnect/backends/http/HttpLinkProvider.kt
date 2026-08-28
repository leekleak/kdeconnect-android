package org.kde.kdeconnect.backends.http

import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.DrawableResource
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.lan.MdnsDiscovery
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.link
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.LoggerTagged
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket as clientWebSocket

class HttpLinkProvider(
    private val deviceHelper: DeviceHelper
) : BaseLinkProvider() {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val activeLinks = mutableMapOf<String, HttpLink>()
    private val linksMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val httpClient = HttpClient {
        install(ClientWebSockets)
    }

    private val mdnsDiscovery = MdnsDiscovery(
        deviceHelper = deviceHelper,
        tcpPortProvider = { 8080 }, // TODO: Make port configurable
        serviceType = MdnsDiscovery.SERVICE_TYPE_HTTP,
        onDeviceDiscovered = { deviceId, host, port ->
            val myId = deviceHelper.getDeviceId()
            if (deviceId == myId || myId.isEmpty()) return@MdnsDiscovery
            
            scope.launch {
                linksMutex.withLock {
                    if (activeLinks.containsKey(deviceId)) return@launch
                }
                
                try {
                    httpClient.clientWebSocket(
                        method = HttpMethod.Get,
                        host = host,
                        port = port,
                        path = "/ws/$myId"
                    ) {
                        val myInfo = deviceHelper.getDeviceInfo()
                        send(Frame.Text(myInfo.toIdentityPacket().serialize()))
                        
                        // Wait for server's identity
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val packet = NetworkPacket.unserialize(text)
                                if (packet.type == NetworkPacket.PACKET_TYPE_IDENTITY) {

                                    val remoteDeviceId = packet.getString("deviceId")
                                    if (remoteDeviceId == deviceId) {
                                        val deviceInfo = DeviceInfo.fromIdentityPacketAndCert(packet)
                                        addOrUpdateLink(deviceInfo, this)
                                        break
                                    }
                                }
                            }
                        }
                        closeReason.await()
                    }
                } catch (e: Exception) {
                    LoggerTagged.e(e) { "Error connecting to discovered HTTP device $deviceId" }
                }
            }
        }
    )

    override suspend fun onStart() {
        val deviceId = deviceHelper.getDeviceId()
        server = embeddedServer(Netty, configure = {
            envConfig(deviceId)
        }) {
            configureWebsockets()
            configureSerialization()
            configureRouting()
        }.start(wait = false)
        
        mdnsDiscovery.startAnnouncing()
        mdnsDiscovery.startDiscovering()
    }

    override fun onStop() {
        mdnsDiscovery.stopAnnouncing()
        mdnsDiscovery.stopDiscovering()
        server?.stop(1000, 1000)
        server = null
        httpClient.close()
        scope.cancel()
    }

    suspend fun addOrUpdateLink(deviceInfo: DeviceInfo, session: WebSocketSession): HttpLink {
        return linksMutex.withLock {
            val existing = activeLinks[deviceInfo.id]
            if (existing != null) {
                existing.deviceInfo = deviceInfo
                existing.reset(session)
                onDeviceInfoUpdated(deviceInfo)
                existing
            } else {
                val newLink = HttpLink(deviceInfo, this)
                newLink.reset(session)
                activeLinks[deviceInfo.id] = newLink
                onConnectionReceived(newLink)
                newLink
            }
        }
    }

    override fun onConnectionLost(link: org.kde.kdeconnect.backends.BaseLink) {
        scope.launch {
            linksMutex.withLock {
                activeLinks.remove(link.deviceId)
            }
        }
        super.onConnectionLost(link)
    }

    private fun Application.configureRouting() {
        routing {
            get("/") {
                val myInfo = deviceHelper.getDeviceInfo()
                call.respondText(myInfo.toIdentityPacket().serialize())
            }
            webSocket("/ws/{deviceId}") {
                val deviceIdFromPath = call.parameters["deviceId"] ?: return@webSocket

                val myInfo = deviceHelper.getDeviceInfo()
                send(Frame.Text(myInfo.toIdentityPacket().serialize()))

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val packet = NetworkPacket.unserialize(text)
                        if (packet.type == NetworkPacket.PACKET_TYPE_IDENTITY) {
                            val deviceId = packet.getString("deviceId")
                            if (deviceId == null || deviceId != deviceIdFromPath) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Device ID mismatch"))
                                return@webSocket
                            }

                            val deviceInfo = DeviceInfo.fromIdentityPacketAndCert(packet)

                            addOrUpdateLink(deviceInfo, this)
                            break
                        }
                    }
                }

                closeReason.await()
            }
        }
    }

    override val name: String = "HttpLinkProvider"
    override val icon: DrawableResource = Res.drawable.link
    override val priority: Int = 10

    private fun ApplicationEngine.Configuration.envConfig(deviceId: String) {
        val privateKeyPassword = deviceId
        val keyStorePassword = deviceId.hashCode().toString()
        val keyStore = buildKeyStore {
            certificate("serverCertificate") {
                password = privateKeyPassword
                domains = listOf("127.0.0.1", "0.0.0.0", "localhost")
            }
        }

        connector {
            port = 8080
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = "serverCertificate",
            keyStorePassword = { keyStorePassword.toCharArray() },
            privateKeyPassword = { privateKeyPassword.toCharArray() }) {
            port = 8443
        }
    }
}

fun Application.configureWebsockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}
