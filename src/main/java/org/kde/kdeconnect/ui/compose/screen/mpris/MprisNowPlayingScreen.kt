package org.kde.kdeconnect.ui.compose.screen.mpris

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.kde.kdeconnect.plugins.mpris.MprisAlbumArt
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MprisNowPlayingScreen(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val playerList by viewModel.playerList.collectAsState()
    val selectedPlayerName by viewModel.selectedPlayerName.collectAsState()
    val playerStatus by viewModel.playerStatus.collectAsState()
    val playerPosition by viewModel.playerPosition.collectAsState()
    val plugin = viewModel.plugin

    Column(
        modifier = Modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = playerStatus?.let { MprisAlbumArt(deviceId, it.playerName, it.albumArtUrl) },
            contentDescription = null,
            placeholder = painterResource(R.drawable.ic_album_art_placeholder),
            error = painterResource(R.drawable.ic_album_art_placeholder),
            modifier = Modifier
                .size(200.dp)
                .padding(16.dp),
            contentScale = ContentScale.Fit
        )

        if (playerList.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = selectedPlayerName ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Player") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        .fillMaxWidth(),
                    colors = ExposedDropdownMenuDefaults.textFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    playerList.forEach { playerName ->
                        DropdownMenuItem(
                            text = { Text(playerName) },
                            onClick = {
                                viewModel.selectPlayer(playerName)
                                expanded = false
                            }
                        )
                    }
                }
            }
        } else {
            Text(stringResource(R.string.no_players_connected))
        }

        Spacer(modifier = Modifier.size(16.dp))

        // Now Playing Title
        val title = playerStatus?.title ?: ""
        val artist = playerStatus?.artist ?: ""
        val nowPlaying = if (artist.isNotEmpty()) "$title - $artist" else title
        Text(
            text = nowPlaying,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.basicMarquee(),
            maxLines = 1
        )

        Spacer(modifier = Modifier.size(16.dp))

        // Main Controls (Loop, Play/Pause, Shuffle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playerStatus?.isLoopStatusAllowed == true) {
                IconButton(onClick = {
                    val status = playerStatus ?: return@IconButton
                    val p = plugin ?: return@IconButton
                    when (status.loopStatus) {
                        "None" -> p.sendSetLoopStatus(status.playerName, "Track")
                        "Track" -> p.sendSetLoopStatus(status.playerName, "Playlist")
                        "Playlist" -> p.sendSetLoopStatus(status.playerName, "None")
                    }
                }) {
                    val icon = when (playerStatus?.loopStatus) {
                        "Track" -> R.drawable.ic_loop_track_black
                        "Playlist" -> R.drawable.ic_loop_playlist_black
                        else -> R.drawable.ic_loop_none_black
                    }
                    Icon(painterResource(icon), contentDescription = stringResource(R.string.mpris_loop))
                }
            }

            IconButton(
                onClick = { playerStatus?.let { plugin?.sendPlayPause(it.playerName) } },
                modifier = Modifier.size(64.dp)
            ) {
                val icon = if (playerStatus?.isPlaying == true) R.drawable.ic_pause_black else R.drawable.ic_play_black
                Icon(
                    painterResource(icon),
                    contentDescription = stringResource(if (playerStatus?.isPlaying == true) R.string.mpris_pause else R.string.mpris_play),
                    modifier = Modifier.size(48.dp)
                )
            }

            if (playerStatus?.isShuffleAllowed == true) {
                IconButton(onClick = { playerStatus?.let { plugin?.sendSetShuffle(it.playerName, !it.shuffle) } }) {
                    val icon = if (playerStatus?.shuffle == true) R.drawable.ic_shuffle_on_black else R.drawable.ic_shuffle_off_black
                    Icon(painterResource(icon), contentDescription = stringResource(R.string.mpris_shuffle))
                }
            }
        }

        // Secondary Controls (Prev, Rew, Stop, FF, Next)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = { playerStatus?.let { plugin?.sendPrevious(it.playerName) } },
                enabled = playerStatus?.isGoPreviousAllowed == true
            ) {
                Icon(painterResource(R.drawable.ic_previous_black), contentDescription = stringResource(R.string.mpris_previous))
            }
            if (playerStatus?.isSeekAllowed == true) {
                IconButton(onClick = { playerStatus?.let { plugin?.sendSeek(it.playerName, -10000) } }) {
                    Icon(painterResource(R.drawable.ic_rewind_black), contentDescription = stringResource(R.string.mpris_rew))
                }
            }
            IconButton(onClick = { playerStatus?.let { plugin?.sendStop(it.playerName) } }) {
                Icon(painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.mpris_stop))
            }
            if (playerStatus?.isSeekAllowed == true) {
                IconButton(onClick = { playerStatus?.let { plugin?.sendSeek(it.playerName, 10000) } }) {
                    Icon(painterResource(R.drawable.ic_fast_forward_black), contentDescription = stringResource(R.string.mpris_ff))
                }
            }
            IconButton(
                onClick = { playerStatus?.let { plugin?.sendNext(it.playerName) } },
                enabled = playerStatus?.isGoNextAllowed == true
            ) {
                Icon(painterResource(R.drawable.ic_next_black), contentDescription = stringResource(R.string.mpris_next))
            }
        }

        Spacer(modifier = Modifier.size(16.dp))

        // Position Slider
        if (playerStatus?.isSeekAllowed == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(durationToProgress(playerPosition.milliseconds))
                Slider(
                    value = playerPosition.toFloat(),
                    onValueChange = { /* Update preview? */ },
                    onValueChangeFinished = { playerStatus?.let { plugin?.sendSetPosition(it.playerName, playerPosition.toInt()) } },
                    valueRange = 0f..(playerStatus?.length?.toFloat() ?: 0f),
                    modifier = Modifier.weight(1f)
                )
                Text(durationToProgress(playerStatus?.length?.milliseconds ?: 0.milliseconds))
            }
        }

        // Volume Slider
        if (playerStatus?.isSetVolumeAllowed == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_volume), contentDescription = stringResource(R.string.mpris_volume))
                Slider(
                    value = playerStatus?.volume?.toFloat() ?: 0f,
                    onValueChange = { volume -> playerStatus?.let { plugin?.sendSetVolume(it.playerName, volume.toInt()) } },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun durationToProgress(duration: Duration): String = buildString {
    val length = duration.inWholeSeconds
    var minutes = length / 60
    if (minutes > 60) {
        val hours = minutes / 60
        minutes %= 60
        append(hours)
        append(':')
        if (minutes < 10) append('0')
    }
    append(minutes)
    append(':')
    val seconds = (length % 60)
    if (seconds < 10) append('0')
    append(seconds)
}
