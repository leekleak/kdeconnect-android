package org.kde.kdeconnect.plugins.mpris

import android.annotation.SuppressLint
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@SuppressLint("UnsafeOptInUsageError")
class MprisPlayer(
    applicationLooper: Looper,
    private var currentPlayer: MprisPlayerState?,
    private val plugin: () -> MprisPlugin?
) : SimpleBasePlayer(applicationLooper) {

    private var currentArtwork: ByteArray? = null

    override fun getState(): State {
        val p = currentPlayer
        val availableCommands = Player.Commands.Builder().apply {
            addAll(COMMAND_GET_METADATA, COMMAND_GET_CURRENT_MEDIA_ITEM, COMMAND_GET_TIMELINE, COMMAND_STOP)
            if (p?.isPlayAllowed == true) add(COMMAND_PLAY_PAUSE)
            if (p?.isPauseAllowed == true) add(COMMAND_PLAY_PAUSE)
            if (p?.isGoNextAllowed == true) add(COMMAND_SEEK_TO_NEXT)
            if (p?.isGoPreviousAllowed == true) add(COMMAND_SEEK_TO_PREVIOUS)
            if (p?.isSeekAllowed == true) add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        }.build()

        val mediaItem = p?.let {
            MediaItem.Builder()
                .setMediaId(it.playerName)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.artist)
                        .setAlbumTitle(it.album)
                        .setArtworkData(currentArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER) //Todo: Investigate blurriness, maybe media3 bug?
                        .build()
                )
                .build()
        }

        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(if (p == null) STATE_IDLE else STATE_READY)
            .setPlayWhenReady(p?.isPlaying == true, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(listOfNotNull(mediaItem?.let {
                MediaItemData.Builder(it.mediaId).setMediaItem(it).setDurationUs(abs(p.length * 1000)).build()
            }))
            .setContentPositionMs { p?.position ?: 0L }
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val p = currentPlayer ?: return Futures.immediateVoidFuture()
        val plugin = plugin() ?: return Futures.immediateVoidFuture()
        CoroutineScope(Dispatchers.IO).launch {
            if (playWhenReady) plugin.sendPlay(p.playerName) else plugin.sendPause(p.playerName)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val p = currentPlayer ?: return Futures.immediateVoidFuture()
        val plugin = plugin() ?: return Futures.immediateVoidFuture()
        CoroutineScope(Dispatchers.IO).launch {
            when (seekCommand) {
                COMMAND_SEEK_TO_NEXT -> plugin.sendNext(p.playerName)
                COMMAND_SEEK_TO_PREVIOUS -> plugin.sendPrevious(p.playerName)
                else -> plugin.sendSetPosition(p.playerName, positionMs.toInt())
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        val p = currentPlayer ?: return Futures.immediateVoidFuture()
        val plugin = plugin() ?: return Futures.immediateVoidFuture()
        CoroutineScope(Dispatchers.IO).launch {
            plugin.sendStop(p.playerName)
        }
        return Futures.immediateVoidFuture()
    }

    suspend fun updatePlayer(newState: MprisPlayerState?, artwork: ByteArray? = null) {
        currentPlayer = newState
        currentArtwork = artwork
        withContext(Dispatchers.Main) {
            invalidateState()
        }
    }

}