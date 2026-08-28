package org.kde.kdeconnect.backends.http

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.DrawableResource
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.DeviceType
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.link
import org.kde.kdeconnect.helpers.DeviceHelper
import kotlin.time.Duration.Companion.seconds

class HttpLinkProvider(
    private val deviceHelper: DeviceHelper
) : BaseLinkProvider() {

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val activeLinks = mutableMapOf<String, HttpLink>()
    private val linksMutex = Mutex()

    override suspend fun onStart() {
        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            configureWebsockets()
            configureSerialization()
            configureRouting()
        }.start(wait = false)
    }

    override fun onStop() {
        server?.stop(1000, 1000)
        server = null
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
        activeLinks.remove(link.deviceId)
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

                            val deviceInfo = DeviceInfo(
                                id = deviceId,
                                certificate = byteArrayOf(), // TODO: support HTTPS/WSS with certs
                                name = packet.getString("deviceName", "Unknown"),
                                type = DeviceType.fromString(packet.getString("deviceType", "desktop")),
                                protocolVersion = packet.getInt("protocolVersion", 0)
                            )

                            addOrUpdateLink(deviceInfo, this)
                            break
                        }
                    }
                }

                // Keep the session alive until it's closed
                closeReason.await()
            }
            get("/json/kotlinx-serialization") {
                call.respond(mapOf("hello" to "world"))
            }
        }
    }

    override val name: String = "HttpLinkProvider"
    override val icon: DrawableResource = Res.drawable.link
    override val priority: Int = 10
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


