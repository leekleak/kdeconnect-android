package org.kde.kdeconnect.plugins.mpris

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.media3.session.MediaSession
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import java.io.ByteArrayOutputStream

class MprisMediaSession(
    private var context: Context?,
    private val dataStore: NotificationSettingsDataStore,
    private val imageLoader: ImageLoader
) {
    private lateinit var device: Device
    private var currentPlugin: MprisPlugin? = null

    private var mprisPlayer: MprisPlayer? = null
    private val _mediaSession = MutableStateFlow<MediaSession?>(null)
    val sessionFlow: StateFlow<MediaSession?> = _mediaSession.asStateFlow()
    val mediaSession: MediaSession? get() = _mediaSession.value

    private var currentAlbumArtUrl: String? = null
    private var currentAlbumArt: ByteArray? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectionJob: Job? = null

    @SuppressLint("UnsafeOptInUsageError")
    fun onCreate(context: Context, plugin: MprisPlugin, deviceId: String) {
        LoggerTagged.d { "onCreate for device $deviceId" }
        if (currentPlugin == plugin) return
        collectionJob?.cancel()
        currentPlugin = plugin
        this.context = context
        this.device = plugin.deviceObj

        val serviceIntent = Intent(context, MprisMediaSessionService::class.java)
        context.startService(serviceIntent)

        val player = MprisPlayer(context.mainLooper, null) { runBlocking { device.getPlugin(MprisPluginInfo.pluginKey) as? MprisPlugin }  }
        mprisPlayer = player

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID, device.deviceId)
            putExtra(KdeConnectKeyConstants.EXTRA_PLUGIN_KEY, "MprisPlugin")
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        collectionJob = serviceScope.launch {
            combine(dataStore.mprisNotificationEnabled, plugin.players) { enabled, players -> enabled to players }
                .collect { (enabled, players) ->
                    if (enabled) {
                        if (mediaSession == null) {
                            _mediaSession.update {
                                MediaSession.Builder(context, player)
                                    .setId("kdeconnect_${deviceId}")
                                    .setSessionActivity(pendingIntent)
                                    .build()
                            }
                        }
                        updateNotification(players, context, player)
                    } else {
                        _mediaSession.update { null }
                    }
                }
        }
    }

    private suspend fun updateNotification(
        players: Map<String, MprisPlayerState>,
        context: Context,
        player: MprisPlayer
    ) {
        val current = players.values.firstOrNull { it.isPlaying } ?: players.values.firstOrNull()
        if (current?.albumArtUrl != currentAlbumArtUrl) {
            currentAlbumArtUrl = current?.albumArtUrl
            if (current != null && current.albumArtUrl.isNotEmpty()) {
                serviceScope.launch(Dispatchers.IO) {
                    val request = ImageRequest.Builder(context)
                        .data(
                            MprisAlbumArt(
                                device.deviceId,
                                current.playerName,
                                current.albumArtUrl
                            )
                        )
                        .allowHardware(false)
                        .build()
                    val result = imageLoader.execute(request)
                    val bytes = result.image?.let { image ->
                        (image as? BitmapImage)?.bitmap?.let { bmp ->
                            ByteArrayOutputStream().apply {
                                bmp.compress(Bitmap.CompressFormat.JPEG, 100, this)
                            }.toByteArray()
                        }
                    }
                    currentAlbumArt = bytes
                    player.updatePlayer(current, currentAlbumArt)
                }
            } else {
                currentAlbumArt = null
                player.updatePlayer(current, null)
            }
        } else {
            player.updatePlayer(current, currentAlbumArt)
        }
    }

    fun onDestroy(plugin: MprisPlugin? = null) {
        if (plugin != null && currentPlugin != plugin) return
        collectionJob?.cancel()
        collectionJob = null
        _mediaSession.value?.release()
        _mediaSession.value = null
        currentPlugin = null
    }
}
