/*
 * SPDX-FileCopyrightText: 2017 Matthijs Tijink <matthijstijink@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.mpris

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.service.notification.StatusBarNotification
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.plugins.notifications.NotificationReceiver
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumeProvider
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.kde.kdeconnect_tp.R

/**
 * Controls the mpris media control notification
 *
 *
 * There are two parts to this:
 * - The notification (with buttons etc.)
 * - The media session (via MediaSessionCompat; for lock screen control on
 * older Android version. And in the future for lock screen album covers)
 */
class MprisMediaSession(
    private var context: Context?,
    private val dataStore: NotificationSettingsDataStore,
    private val imageLoader: ImageLoader
) : SystemVolumeProvider.ProviderStateListener {
    private lateinit var device: Device
    private var notificationDeviceId: String? = null
    var mediaSession: MediaSessionCompat? = null
        private set
    private lateinit var metadata: MediaMetadataCompat.Builder
    private lateinit var playbackState: PlaybackStateCompat.Builder
    private var notificationPlayer: MprisPlayerState? = null
    private var spotifyRunning = false
    private var currentProvider: SystemVolumeProvider? = null

    private var currentAlbumArtUrl: String? = null
    private var currentAlbumArt: Bitmap? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectionJob: Job? = null
    private var enabled: Boolean = false

    private val mediaSessionCallback: MediaSessionCompat.Callback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            serviceScope.launch {
                notificationPlayer?.let {
                    val plugin = it.getPlugin() ?: return@launch
                    plugin.sendPlay(it.playerName)
                }
            }
        }

        override fun onPause() {
            serviceScope.launch {
                notificationPlayer?.let {
                    val plugin = it.getPlugin() ?: return@launch
                    plugin.sendPause(it.playerName)
                }
            }
        }

        override fun onSkipToNext() {
            serviceScope.launch {
                notificationPlayer?.let {
                    val plugin = it.getPlugin() ?: return@launch
                    plugin.sendNext(it.playerName)
                }
            }
        }

        override fun onSkipToPrevious() {
            serviceScope.launch {
                notificationPlayer?.let {
                    val plugin = it.getPlugin() ?: return@launch
                    plugin.sendPrevious(it.playerName)
                }
            }
        }

        override fun onStop() {
            serviceScope.launch {
                notificationPlayer?.let {
                    val plugin = it.getPlugin() ?: return@launch
                    plugin.sendStop(it.playerName)
                }
            }
        }

        override fun onSeekTo(pos: Long) {
            serviceScope.launch {
                notificationPlayer?.let {
                    val plugin = it.getPlugin() ?: return@launch
                    plugin.sendSetPosition(it.playerName, pos.toInt())
                }
            }
        }

        private fun MprisPlayerState.getPlugin(): MprisPlugin? {
            val device = this@MprisMediaSession.device
            return device.getPlugin(MprisPlugin::class.java)
        }
    }

    fun onCreate(context: Context, plugin: MprisPlugin, deviceId: String) {
        this.context = context
        this.device = plugin.deviceObj
        notificationDeviceId = deviceId
        metadata = MediaMetadataCompat.Builder()
        playbackState = PlaybackStateCompat.Builder()

        collectionJob?.cancel()
        collectionJob = serviceScope.launch {
            combine(
                dataStore.mprisNotificationEnabled,
                plugin.players
            ) {enabled, players -> enabled to players}.collect {(enabled, players) ->
                this@MprisMediaSession.enabled = enabled
                updateMediaNotification(players.values.toList())
            }
        }

        currentProvider = SystemVolumeProvider.getInstance().apply {
            setPlugin(device.getPlugin(SystemVolumePlugin::class.java))
            addStateListener(this@MprisMediaSession)
        }
    }

    fun onDestroy(plugin: MprisPlugin, deviceId: String) {
        collectionJob?.cancel()
        collectionJob = null
        closeMediaNotification()
        currentProvider?.release()
        currentProvider = null
    }

    fun updateMediaNotification(players: List<MprisPlayerState>?) {
        if (!enabled) return

        notificationPlayer = players?.firstOrNull() ?: notificationPlayer

        val currentPlayer = notificationPlayer ?: return
        
        if (currentPlayer.albumArtUrl != currentAlbumArtUrl) {
            currentAlbumArtUrl = currentPlayer.albumArtUrl
            currentAlbumArt = null
            serviceScope.launch {
                val request = ImageRequest.Builder(context!!)
                    .data(MprisAlbumArt(device.deviceId, currentPlayer.playerName, currentPlayer.albumArtUrl))
                    .build()
                val result = imageLoader.execute(request)
                currentAlbumArt = (result.image as? BitmapImage)?.bitmap
                updateMediaNotification(null)
            }
        }

        metadata
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentPlayer.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentPlayer.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentPlayer.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentPlayer.length)

        if (currentAlbumArt != null) {
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, currentAlbumArt)
        }

        playbackState.setState(
            if (currentPlayer.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            currentPlayer.position,
            1.0f
        )

        // Actions for the notification
        val iPrevious = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PREVIOUS)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDeviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piPrevious = PendingIntent.getBroadcast(
            context,
            0,
            iPrevious,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aPrevious = NotificationCompat.Action.Builder(
            R.drawable.ic_previous_white, context!!.getString(R.string.mpris_previous), piPrevious
        )

        val iPause = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PAUSE)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDeviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piPause = PendingIntent.getBroadcast(
            context,
            0,
            iPause,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aPause = NotificationCompat.Action.Builder(
            R.drawable.ic_pause_white, context!!.getString(R.string.mpris_pause), piPause
        )

        val iPlay = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_PLAY)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDeviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piPlay = PendingIntent.getBroadcast(
            context,
            0,
            iPlay,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aPlay = NotificationCompat.Action.Builder(
            R.drawable.ic_play_white, context!!.getString(R.string.mpris_play), piPlay
        )

        val iNext = Intent(context, MprisMediaNotificationReceiver::class.java).apply {
            setAction(MprisMediaNotificationReceiver.ACTION_NEXT)
            putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDeviceId)
            putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
        }
        val piNext = PendingIntent.getBroadcast(
            context,
            0,
            iNext,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val aNext = NotificationCompat.Action.Builder(
            R.drawable.ic_next_white, context!!.getString(R.string.mpris_next), piNext
        )

        val iOpenActivity = Intent(context, MainActivity::class.java).apply {
            putExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID, notificationDeviceId)
            putExtra(KdeConnectKeyConstants.EXTRA_PLUGIN_KEY, "MprisPlugin")
            putExtra("player", currentPlayer.playerName)
        }

        val piOpenActivity = TaskStackBuilder.create(context!!)
            .addNextIntentWithParentStack(iOpenActivity)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context!!, NotificationHelper.Channels.MEDIA_CONTROL)

        notification
            .setAutoCancel(false)
            .setContentIntent(piOpenActivity)
            .setSmallIcon(R.drawable.ic_play_white)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSubText(device.name)

        notification.setContentTitle(currentPlayer.title)

        // Only set the notification body text if we have an author and/or album
        if (currentPlayer.artist.isNotEmpty() && currentPlayer.album.isNotEmpty()) {
            notification.setContentText(currentPlayer.artist + " - " + currentPlayer.album + " (" + currentPlayer.playerName + ")")
        } else if (currentPlayer.artist.isNotEmpty()) {
            notification.setContentText(currentPlayer.artist + " (" + currentPlayer.playerName + ")")
        } else if (currentPlayer.album.isNotEmpty()) {
            notification.setContentText(currentPlayer.album + " (" + currentPlayer.playerName + ")")
        } else {
            notification.setContentText(currentPlayer.playerName)
        }

        if (currentAlbumArt != null) {
            notification.setLargeIcon(currentAlbumArt)
        }

        if (!currentPlayer.isPlaying) {
            val iCloseNotification = Intent(context, MprisMediaNotificationReceiver::class.java)
            iCloseNotification.setAction(MprisMediaNotificationReceiver.ACTION_CLOSE_NOTIFICATION)
            iCloseNotification.putExtra(MprisMediaNotificationReceiver.EXTRA_DEVICE_ID, notificationDeviceId)
            iCloseNotification.putExtra(MprisMediaNotificationReceiver.EXTRA_MPRIS_PLAYER, currentPlayer.playerName)
            val piCloseNotification = PendingIntent.getBroadcast(
                context,
                0,
                iCloseNotification,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notification.setDeleteIntent(piCloseNotification)
        }

        // Add media control actions
        var numActions = 0
        var playbackActions: Long = 0
        if (currentPlayer.isGoPreviousAllowed) {
            notification.addAction(aPrevious.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            ++numActions
        }
        if (currentPlayer.isPlaying && currentPlayer.isPauseAllowed) {
            notification.addAction(aPause.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PAUSE
            ++numActions
        }
        if (!currentPlayer.isPlaying && currentPlayer.isPlayAllowed) {
            notification.addAction(aPlay.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_PLAY
            ++numActions
        }
        if (currentPlayer.isGoNextAllowed) {
            notification.addAction(aNext.build())
            playbackActions = playbackActions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            ++numActions
        }
        // Documentation says that this was added in Lollipop (21) but it seems to cause crashes on < Pie (28)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (currentPlayer.isSeekAllowed) {
                playbackActions = playbackActions or PlaybackStateCompat.ACTION_SEEK_TO
            }
        }
        playbackState.setActions(playbackActions)

        // Only allow deletion if no music is currentPlayer
        notification.setOngoing(currentPlayer.isPlaying)

        // Use the MediaStyle notification, so it feels like other media players. That also allows adding actions
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
        if (numActions == 1) {
            mediaStyle.setShowActionsInCompactView(0)
        } else if (numActions == 2) {
            mediaStyle.setShowActionsInCompactView(0, 1)
        } else if (numActions >= 3) {
            mediaStyle.setShowActionsInCompactView(0, 1, 2)
        }
        notification.setGroup("MprisMediaSession")

        // Display the notification
        synchronized(this) {
            val mediaSession = mediaSession ?: MediaSessionCompat(context!!, MPRIS_MEDIA_SESSION_TAG).apply {
                setCallback(mediaSessionCallback, Handler(context!!.mainLooper))
            }
            mediaSession.setMetadata(metadata.build())
            mediaSession.setPlaybackState(playbackState.build())
            mediaStyle.setMediaSession(mediaSession.sessionToken)
            notification.setStyle(mediaStyle)
            mediaSession.isActive = true
            ContextCompat.getSystemService(context!!, NotificationManager::class.java)?.notify(MPRIS_MEDIA_NOTIFICATION_ID, notification.build())
            if (this.mediaSession == null) {
                this.mediaSession = mediaSession
            }
        }
    }

    fun closeMediaNotification() {
        // Remove the notification
        val nm = ContextCompat.getSystemService(context!!, NotificationManager::class.java)
        nm?.cancel(MPRIS_MEDIA_NOTIFICATION_ID)

        // Clear the current player and media session
        notificationPlayer = null
        synchronized(this) {
            mediaSession?.apply {
                setPlaybackState(PlaybackStateCompat.Builder().build())
                setMetadata(MediaMetadataCompat.Builder().build())
                isActive = false
                release()
            }
            mediaSession = null
            currentProvider?.release()
        }
    }

    fun playerSelected(player: MprisPlayerState?) {
        notificationPlayer = player
        updateMediaNotification(null)
    }

    override fun onProviderStateChanged(systemVolumeProvider: SystemVolumeProvider, isActive: Boolean) {
        val mediaSession = mediaSession ?: return

        if (isActive) {
            mediaSession.setPlaybackToRemote(systemVolumeProvider)
        } else {
            mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
        }
    }
    
    companion object {
        const val TAG = "MprisMediaSession"

        private const val MPRIS_MEDIA_NOTIFICATION_ID =
            0x91b70463.toInt() // echo MprisNotification | md5sum | head -c 8
        private const val MPRIS_MEDIA_SESSION_TAG = "org.kde.kdeconnect_tp.media_session"
    }
}
