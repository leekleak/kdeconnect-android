package org.kde.kdeconnect.backends.http

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.device.SendPacketStatusCallback
import org.kde.kdeconnect.helpers.LoggerTagged

class HttpLink(
    override var deviceInfo: DeviceInfo,
    linkProvider: BaseLinkProvider,
) : BaseLink(linkProvider) {
    override val name: String = "HttpLink"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var session: WebSocketSession? = null

    fun reset(session: WebSocketSession) {
        this.session = session
        scope.launch {
            try {
                for (frame in session.incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val packet = NetworkPacket.unserialize(text)
                        packetReceived(packet)
                    }
                }
            } catch (e: Exception) {
                LoggerTagged.i { "WebSocket closed for ${deviceInfo.id}: ${e.message}" }
            } finally {
                if (this@HttpLink.session === session) {
                    linkProvider.onConnectionLost(this@HttpLink)
                }
            }
        }
    }

    override suspend fun sendPacket(
        np: NetworkPacket,
        callback: SendPacketStatusCallback
    ): Boolean {
        val s = session ?: run {
            callback.onFailure(Exception("No active session"))
            return false
        }
        return try {
            s.send(Frame.Text(np.serialize()))
            callback.onSuccess()
            true
        } catch (e: Exception) {
            callback.onFailure(e)
            false
        }
    }

    override suspend fun disconnect() {
        session?.close()
        scope.cancel()
    }
}
