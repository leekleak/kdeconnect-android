/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.mpris

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.NetworkPacket.Payload
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.helpers.VideoUrlsHelper
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginInfo
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.navigation.MprisKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import java.net.MalformedURLException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

data class MprisPlayerState(
    val playerName: String = "",
    val isPlaying: Boolean = false,
    val playStartTime: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtUrl: String = "",
    val url: String = "",
    val loopStatus: String = "",
    val isLoopStatusAllowed: Boolean = false,
    val shuffle: Boolean = false,
    val isShuffleAllowed: Boolean = false,
    val volume: Int = 50,
    val length: Long = -1,
    val lastPosition: Long = 0,
    val lastPositionTime: Long = System.currentTimeMillis(),
    val isPlayAllowed: Boolean = true,
    val isPauseAllowed: Boolean = true,
    val isGoNextAllowed: Boolean = true,
    val isGoPreviousAllowed: Boolean = true,
    val seekAllowed: Boolean = true
) {
    val isSeekAllowed: Boolean
        get() = seekAllowed && length >= 0 && position >= 0


    fun getHttpUrl(): String? {
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    val isSetVolumeAllowed: Boolean
        get() = volume > -1

    val position: Long
        get() = if (isPlaying) {
            lastPosition + (System.currentTimeMillis() - lastPositionTime)
        } else {
            lastPosition
        }
}

class MprisPlugin(
    context: Context,
    device: Device,
    private val dataStore: NotificationSettingsDataStore,
    private val mprisMediaSession: MprisMediaSession,
    private val videoUrlsHelper: VideoUrlsHelper
) : Plugin(context, device) {
    override val pluginInfo: PluginInfo = MprisPluginSettings

    private val _players = MutableStateFlow<Map<String, MprisPlayerState>>(emptyMap())
    val players: StateFlow<Map<String, MprisPlayerState>> = _players.asStateFlow()

    val deviceObj: Device get() = device

    private var supportAlbumArtPayload = false

    private val pendingAlbumArtFetches = ConcurrentHashMap<String, CompletableDeferred<Payload>>()

    override suspend fun onCreate(): Boolean {
        mprisMediaSession.onCreate(context.applicationContext, this, device.deviceId)

        // Always request the player list so the data is up-to-date
        requestPlayerList()

        return true
    }

    override suspend fun onDestroy() {
        super.onDestroy()
        _players.value = emptyMap()
        mprisMediaSession.onDestroy(this, device.deviceId)
    }

    private suspend fun sendCommand(player: String, method: String, value: String) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this[method] = value
        }
        device.sendPacket(np)
    }

    private suspend fun sendCommand(player: String, method: String, value: Boolean) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this[method] = value
        }
        device.sendPacket(np)
    }

    private suspend fun sendCommand(player: String, method: String, value: Int) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this[method] = value
        }
        device.sendPacket(np)
    }

    override suspend fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.getBoolean("transferringAlbumArt", false)) {
            val url = np.getString("albumArtUrl")
            np.payload?.let { payload ->
                pendingAlbumArtFetches.remove(url)?.complete(payload)
            } ?: pendingAlbumArtFetches.remove(url)?.cancel()
            return true
        }

        if (np.has("player")) {
            val playerName = np.getString("player")
            _players.update { current ->
                val oldState = current[playerName] ?: MprisPlayerState(playerName = playerName)
                val wasPlaying = oldState.isPlaying
                
                var newState = oldState.copy(
                    title = np.getString("title", oldState.title),
                    artist = np.getString("artist", oldState.artist),
                    album = np.getString("album", oldState.album),
                    url = np.getString("url", oldState.url).let { if (isInvalidPlayerUrl(it)) "" else it },
                    volume = np.getInt("volume", oldState.volume),
                    length = np.getLong("length", oldState.length),
                    isPlaying = np.getBoolean("isPlaying", oldState.isPlaying),
                    isPlayAllowed = np.getBoolean("canPlay", oldState.isPlayAllowed),
                    isPauseAllowed = np.getBoolean("canPause", oldState.isPauseAllowed),
                    isGoNextAllowed = np.getBoolean("canGoNext", oldState.isGoNextAllowed),
                    isGoPreviousAllowed = np.getBoolean("canGoPrevious", oldState.isGoPreviousAllowed),
                    seekAllowed = np.getBoolean("canSeek", oldState.seekAllowed)
                )

                if (np.has("loopStatus")) {
                    newState = newState.copy(
                        loopStatus = np.getString("loopStatus", newState.loopStatus),
                        isLoopStatusAllowed = true
                    )
                }
                if (np.has("shuffle")) {
                    newState = newState.copy(
                        shuffle = np.getBoolean("shuffle", newState.shuffle),
                        isShuffleAllowed = true
                    )
                }
                if (np.has("pos")) {
                    newState = newState.copy(
                        lastPosition = np.getLong("pos", newState.lastPosition),
                        lastPositionTime = System.currentTimeMillis()
                    )
                }
                if (newState.isPlaying && !wasPlaying) {
                    newState = newState.copy(playStartTime = System.currentTimeMillis())
                }

                val newAlbumArtUrlString = np.getString("albumArtUrl", newState.albumArtUrl)
                val newAlbumArtUrl = newAlbumArtUrlString.toUri()
                if (newAlbumArtUrl.scheme in ALLOWED_SCHEMES) {
                    newState = newState.copy(albumArtUrl = newAlbumArtUrl.toString())
                } else if (newAlbumArtUrlString.isNotEmpty()) {
                    Log.w("MprisControl", "Invalid album art URL: $newAlbumArtUrlString")
                    newState = newState.copy(albumArtUrl = "")
                }

                // Check to see if a stream has stopped playing and we should deliver a notification
                if (np.has("isPlaying") && !newState.isPlaying && wasPlaying) {
                    showContinueWatchingNotification(newState)
                }

                current + (playerName to newState)
            }
        }

        // Remember if the connected device support album art payloads
        supportAlbumArtPayload = np.getBoolean("supportAlbumArtPayload", supportAlbumArtPayload)

        val newPlayerList = np.getStringList("playerList")
        if (newPlayerList != null) {
            Log.e("MPRIS", "Updating player list")
            _players.update { current ->
                val updatedMap = current.toMutableMap()

                newPlayerList.forEach { playerName ->
                    if (!updatedMap.containsKey(playerName)) {
                        updatedMap[playerName] = MprisPlayerState(playerName = playerName)
                        requestPlayerStatus(playerName)
                    }
                }

                val iter = updatedMap.entries.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    if (!newPlayerList.contains(entry.key)) {
                        if (entry.value.isPlaying) {
                            showContinueWatchingNotification(entry.value)
                        }
                        iter.remove()
                    }
                }
                
                updatedMap
            }
        }

        return true
    }

    private fun isInvalidPlayerUrl(url: String): Boolean {
        // Not a valid video URL. Can happen when an on-hover preview is playing.
        return url == "https://www.youtube.com/" || url == "https://www.youtube.com/tv#/"
    }

    private fun showContinueWatchingNotification(playerStatus: MprisPlayerState) {
        if (playerStatus.playStartTime + 5000 > System.currentTimeMillis()) {
            // Playback was too short
            return
        }
        coroutineScope.launch {
            delay(500.milliseconds)
            if (getPlayerStatus(playerStatus.playerName)?.isPlaying == true) {
                // Pause was too short. Probably just the gap between songs
                return@launch
            }
            val httpUrl = playerStatus.getHttpUrl()
            if (dataStore.isMprisKeepWatchingEnabledBlocking() && httpUrl != null) {
                try {
                    val transformedUrl = httpUrl
                        .let { videoUrlsHelper.convertToAndFromYoutubeTvLinks(it) }
                        .let { videoUrlsHelper.formatUriWithSeek(it, playerStatus.position) }
                        .toUri()
                    val browserIntent = Intent(Intent.ACTION_VIEW, transformedUrl)
                    val pendingIntent = PendingIntent.getActivity(context, 0, browserIntent, PendingIntent.FLAG_IMMUTABLE)

                    Handler(Looper.getMainLooper()).post {
                        val notificationManager = context.getSystemService<NotificationManager>()!!
                        val builder = NotificationCompat.Builder(context, NotificationHelper.Channels.CONTINUEWATCHING)
                            .setContentTitle(context.resources.getString(R.string.kde_connect))
                            .setSmallIcon(R.drawable.ic_play_white)
                            .setTimeoutAfter(3000)
                            .setContentIntent(pendingIntent)
                            .setContentText(context.resources.getString(R.string.mpris_keepwatching) + " " + playerStatus.title)
                        notificationManager.notify(
                            System.currentTimeMillis().toInt(),
                            builder.build()
                        )
                    }
                } catch (e: MalformedURLException) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun sendPlayPause(playerName: String) {
        val player = getPlayerStatus(playerName) ?: return
        if (player.isPauseAllowed || player.isPlayAllowed) {
            sendCommand(playerName, "action", "PlayPause")
        }
    }

    suspend fun sendPlay(playerName: String) {
        if (getPlayerStatus(playerName)?.isPlayAllowed == true) {
            sendCommand(playerName, "action", "Play")
        }
    }

    suspend fun sendPause(playerName: String) {
        if (getPlayerStatus(playerName)?.isPauseAllowed == true) {
            sendCommand(playerName, "action", "Pause")
        }
    }

    suspend fun sendStop(playerName: String) {
        sendCommand(playerName, "action", "Stop")
    }

    suspend fun sendPrevious(playerName: String) {
        if (getPlayerStatus(playerName)?.isGoPreviousAllowed == true) {
            sendCommand(playerName, "action", "Previous")
        }
    }

    suspend fun sendNext(playerName: String) {
        if (getPlayerStatus(playerName)?.isGoNextAllowed == true) {
            sendCommand(playerName, "action", "Next")
        }
    }

    suspend fun sendSetLoopStatus(playerName: String, loopStatus: String) {
        sendCommand(playerName, "setLoopStatus", loopStatus)
    }

    suspend fun sendSetShuffle(playerName: String, shuffle: Boolean) {
        sendCommand(playerName, "setShuffle", shuffle)
    }

    suspend fun sendSetVolume(playerName: String, volume: Int) {
        if (getPlayerStatus(playerName)?.isSetVolumeAllowed == true) {
            sendCommand(playerName, "setVolume", volume)
        }
    }

    suspend fun sendSetPosition(playerName: String, position: Int) {
        val player = getPlayerStatus(playerName) ?: return
        if (player.isSeekAllowed) {
            sendCommand(playerName, "SetPosition", position)

            _players.update { current ->
                current[playerName]?.let {
                    current + (playerName to it.copy(
                        lastPosition = position.toLong(),
                        lastPositionTime = System.currentTimeMillis()
                    ))
                } ?: current
            }
        }
    }

    suspend fun sendSeek(playerName: String, offset: Int) {
        if (getPlayerStatus(playerName)?.isSeekAllowed == true) {
            sendCommand(playerName, "Seek", offset)
        }
    }

    override fun getUiButtons(): List<PluginUiButton> = listOf(
        PluginUiButton(
            name = context.getString(R.string.open_mpris_controls),
            iconRes = R.drawable.music_cast,
            category = ButtonCategory.CONTROL
        ) { parentActivity ->
            val navigator = (parentActivity as MainActivity).scope.get<Navigator>(
                Navigator::class.java.kotlin, null, null
            )
            navigator.goTo(MprisKey(device.deviceId))
        }
    )

    fun getPlayerStatus(player: String?): MprisPlayerState? = if (player == null) {
        null
    } else _players.value[player]

    val playingPlayer: MprisPlayerState?
        get() = _players.value.values.firstOrNull { it.isPlaying }

    suspend fun requestPlayerList() {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["requestPlayerList"] = true
        }
        device.sendPacket(np)
    }

    private suspend fun requestPlayerStatus(player: String) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST).apply {
            this["player"] = player
            this["requestNowPlaying"] = true
            this["requestVolume"] = true
        }
        device.sendPacket(np)
    }

    suspend fun fetchAlbumArt(url: String, playerName: String?): Payload? {
        val deferred = CompletableDeferred<Payload>()
        pendingAlbumArtFetches[url] = deferred

        return try {
            if (!askTransferAlbumArt(url, playerName)) {
                null
            } else {
                withTimeoutOrNull(10000.milliseconds) {
                    deferred.await()
                }
            }
        } finally {
            pendingAlbumArtFetches.remove(url)
        }
    }

    suspend fun askTransferAlbumArt(url: String, playerName: String?): Boolean {
        // First check if the remote supports transferring album art
        if (!supportAlbumArtPayload) return false
        if (url.isEmpty()) return false

        val player = getPlayerStatus(playerName) ?: return false

        if (player.albumArtUrl == url) {
            val np = NetworkPacket(PACKET_TYPE_MPRIS_REQUEST)
            np["player"] = player.playerName
            np["albumArtUrl"] = url
            device.sendPacket(np)
            return true
        }
        return false
    }

    companion object {
        const val PREFERENCES_NAME: String = "MprisPlugin_preferences"
        const val DEVICE_ID_KEY: String = "deviceId"
        const val PACKET_TYPE_MPRIS = "kdeconnect.mpris"
        const val PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request"
        val ALLOWED_SCHEMES = listOf("http", "https", "file", "kdeconnect")
    }
}

object MprisPluginSettings: PluginInfo(
    instantiableClass = MprisPlugin::class.java,
    displayNameRes = R.string.pref_plugin_mpris,
    descriptionRes = R.string.pref_plugin_mpris_desc,
    requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf()
    },
    supportedPacketTypes = arrayOf(MprisPlugin.PACKET_TYPE_MPRIS),
    outgoingPacketTypes = arrayOf(MprisPlugin.PACKET_TYPE_MPRIS_REQUEST),
) {
    //override val optionalPermissionExplanation: Int = R.string.mpris_notifications_explanation
}
