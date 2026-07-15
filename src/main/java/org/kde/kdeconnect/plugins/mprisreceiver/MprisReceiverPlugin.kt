/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.mprisreceiver

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.AppsHelper.appNameLookup
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.notifications.NotificationReceiver
import java.util.concurrent.ConcurrentHashMap

class MprisReceiverPlugin(context: Context, device: Device) : Plugin(context, device) {
    // TODO: Those two are always accessed together, merge them
    private val players: ConcurrentHashMap<String, MprisReceiverPlayer> = ConcurrentHashMap<String, MprisReceiverPlayer>()
    private val playerCbs: ConcurrentHashMap<String, MprisReceiverCallback> = ConcurrentHashMap<String, MprisReceiverCallback>()

    private var mediaSessionChangeListener: MediaSessionChangeListener? = null

    override val pluginInfo: MprisReceiverPluginInfo = MprisReceiverPluginInfo

    override fun onCreate(): Boolean {
        if (!NotificationReceiver.hasReadNotificationsPermission(context)) {
            return false
        }
        try {
            val manager = ContextCompat.getSystemService(
                context,
                MediaSessionManager::class.java
            ) ?: return false

            mediaSessionChangeListener = MediaSessionChangeListener()
            manager.addOnActiveSessionsChangedListener(
                mediaSessionChangeListener!!,
                ComponentName(context, NotificationReceiver::class.java),
                Handler(Looper.getMainLooper())
            )

            createPlayers(
                manager.getActiveSessions(
                    ComponentName(
                        context,
                        NotificationReceiver::class.java
                    )
                )
            )
            sendPlayerList()
        } catch (e: Exception) {
            Log.e(TAG, "Exception", e)
        }

        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        val manager = ContextCompat.getSystemService(context, MediaSessionManager::class.java)
        mediaSessionChangeListener?.let { manager?.removeOnActiveSessionsChangedListener(it) }
        mediaSessionChangeListener = null
    }

    private fun createPlayers(sessions: MutableList<MediaController>) {
        for (controller in sessions) {
            createPlayer(controller)
        }
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.getBoolean("requestPlayerList")) {
            sendPlayerList()
            return true
        }

        if (!np.has("player")) {
            return false
        }
        val player = players[np.getString("player")] ?: return false

        val artUrl = np.getString("albumArtUrl", "")
        if (artUrl.isNotEmpty()) {
            val playerName = player.name
            val cb = playerCbs[playerName] ?: run {
                Log.e(TAG, "no callback for $playerName (player likely stopped)")
                return false
            }
            // run it on a different thread to avoid blocking
            execute { sendAlbumArt(playerName, cb, artUrl) }
            return true
        }

        if (np.getBoolean("requestNowPlaying", false)) {
            sendMetadata(player)
            return true
        }

        if (np.has("SetPosition")) {
            val position = np.getLong("SetPosition", 0)
            player.position = position
        }

        if (np.has("setVolume")) {
            val volume = np.getInt("setVolume", 100)
            player.volume = volume
            //Setting volume doesn't seem to always trigger the callback
            sendMetadata(player)
        }

        if (np.has("action")) {
            val action = np.getString("action")

            when (action) {
                "Play" -> player.play()
                "Pause" -> player.pause()
                "PlayPause" -> player.playPause()
                "Next" -> player.next()
                "Previous" -> player.previous()
                "Stop" -> player.stop()
            }
        }

        return true
    }

    private inner class MediaSessionChangeListener : OnActiveSessionsChangedListener {
        override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
            if (controllers == null) return

            for (p in players) {
                playerCbs[p.value.name]?.let { p.value.controller.unregisterCallback(it) }
            }
            playerCbs.clear()
            players.clear()

            createPlayers(controllers)
            sendPlayerList()
        }
    }

    private fun createPlayer(controller: MediaController) {
        // Skip the media session we created ourselves as KDE Connect
        if (controller.packageName == context.packageName) return

        val player = MprisReceiverPlayer(controller, appNameLookup(context, controller.packageName))
        val cb = MprisReceiverCallback(this, player)
        controller.registerCallback(cb, Handler(Looper.getMainLooper()))
        playerCbs[player.name] = cb
        players[player.name] = player
    }

    private fun sendPlayerList() {
        val np = NetworkPacket(PACKET_TYPE_MPRIS)
        np["playerList"] = players.keys
        np["supportAlbumArtPayload"] = true
        device.sendPacket(np)
    }

    fun sendAlbumArt(playerName: String, cb: MprisReceiverCallback, requestedUrl: String?) {
        // NOTE: It is possible that the player gets killed in the middle of this method.
        // The proper thing to do this case would be to abort the send - but that gets into the
        //   territory of async cancellation or putting a lock.
        // For now, we just continue to send the art- cb stores the bitmap, so it will be valid.
        //   cb will get GC'd after this method completes.
        val localArtUrl = cb.artUrl ?: run {
            Log.w(TAG, "art not found!")
            return
        }
        val artUrl = requestedUrl ?: localArtUrl
        if (requestedUrl != null && requestedUrl != localArtUrl) {
            Log.w(TAG, "sendAlbumArt: Doesn't match current url")
            Log.d(TAG, "current:   $localArtUrl")
            Log.d(TAG, "requested: $requestedUrl")
            return
        }
        val p = cb.artAsArray ?: run {
            Log.w(TAG, "sendAlbumArt: Failed to get art stream")
            return
        }
        val np = NetworkPacket(PACKET_TYPE_MPRIS)
        np.payload = NetworkPacket.Payload(p)
        np["player"] = playerName
        np["transferringAlbumArt"] = true
        np["albumArtUrl"] = artUrl
        device.sendPacket(np)
    }

    fun sendMetadata(player: MprisReceiverPlayer) {
        val np = NetworkPacket(PACKET_TYPE_MPRIS)
        np["player"] = player.name
        np["title"] = player.title
        np["artist"] = player.artist
        np["nowPlaying"] = "${player.artist} - ${player.title}" // GSConnect 50 (so, Ubuntu 22.04) needs this
        np["album"] = player.album
        np["isPlaying"] = player.isPlaying()
        np["pos"] = player.position
        np["length"] = player.length
        np["canPlay"] = player.canPlay()
        np["canPause"] = player.canPause()
        np["canGoPrevious"] = player.canGoPrevious()
        np["canGoNext"] = player.canGoNext()
        np["canSeek"] = player.canSeek()
        np["volume"] = player.volume
        playerCbs[player.name]?.artUrl?.let { np["albumArtUrl"] = it } ?: run { np["albumArtUrl"] = "" }
        device.sendPacket(np)
    }

    companion object {
        private const val PACKET_TYPE_MPRIS = "kdeconnect.mpris"
        private const val PACKET_TYPE_MPRIS_REQUEST = "kdeconnect.mpris.request"

        private const val TAG = "MprisReceiver"
    }
}
