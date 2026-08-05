package org.kde.kdeconnect.plugins.mpris

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.card
import org.kde.kdeconnect.ui.compose.components.googleSans
import org.kde.kdeconnect.ui.navigation.MprisSinkKey
import org.kde.kdeconnect.ui.navigation.MprisSourceKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MprisScreen(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val hazeState: HazeState = koinInject()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.currentTab = page
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.thin()
            )
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PlayerIsland(viewModel)
        ControlsIsland(deviceId)
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun PlayerIsland(viewModel: MprisViewModel) {
    val selectedPlayerName by viewModel.selectedPlayerName.collectAsState()
    val playerStatus by viewModel.playerStatus.collectAsState()
    val playerPosition by viewModel.playerPosition.collectAsState()

    var isBright by remember(selectedPlayerName, playerStatus?.albumArtUrl) { mutableStateOf(true) }
    val backgroundColor by animateColorAsState(if (isBright) Color.White else Color.Black)
    val contentColor by animateColorAsState(if (isBright) Color.Black else Color.White)

    val hazeState = rememberHazeState()

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(backgroundColor)
    ) {
        AsyncImage(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth().hazeSource(hazeState)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(0.66f to Color.Transparent, 1f to backgroundColor),
                        topLeft = Offset(x = 0f, y = size.height*2/3),
                        size = Size(size.width, size.height/3f)
                    )
                },
            model = ImageRequest.Builder(LocalContext.current)
                .data(playerStatus?.let {
                    MprisAlbumArt(
                        viewModel.deviceId,
                        it.playerName,
                        it.albumArtUrl
                    )
                })
                .allowHardware(false)
                .build(),
            onSuccess = { result ->
                val bitmap = result.result.image.toBitmap(
                    result.result.image.width,
                    result.result.image.height
                )
                Palette.from(bitmap).generate { palette ->
                    palette?.dominantSwatch?.let {
                        isBright = it.hsl[2] > 0.5f
                    }
                }
            },
            contentDescription = null,
            error = painterResource(R.drawable.ic_album_art_placeholder),
            contentScale = ContentScale.Crop
        )
        Column {
            Spacer(modifier = Modifier.aspectRatio(1.5f).fillMaxWidth())
            Spacer(modifier = Modifier.aspectRatio(1.5f).fillMaxWidth()
                .hazeEffect(hazeState, HazeMaterials.thick(backgroundColor)) {
                    progressive =
                        HazeProgressive.verticalGradient(
                            startIntensity = 0f,
                            endIntensity = 1f
                        )
                }
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.aspectRatio(1.2f).fillMaxWidth())

            val title = playerStatus?.title ?: ""
            val artist = playerStatus?.artist ?: ""
            val fontBold = remember { googleSans(weight = 900f) }
            val font = remember { googleSans(weight = 600f) }
            Text(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontSize = 36.sp,
                color = contentColor,
                fontFamily = fontBold,
            )

            Text(
                text = artist,
                modifier = Modifier.basicMarquee(),
                fontSize = 16.sp,
                maxLines = 1,
                color = contentColor,
                fontFamily = font,
                fontStyle = FontStyle.Italic
            )

            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playerStatus?.isLoopStatusAllowed == true) {
                    IconButton(onClick = { viewModel.toggleLoopStatus() }) {
                        val icon = when (playerStatus?.loopStatus) {
                            "Track" -> R.drawable.ic_loop_track_black
                            "Playlist" -> R.drawable.ic_loop_playlist_black
                            else -> R.drawable.ic_loop_none_black
                        }
                        Icon(
                            painterResource(icon),
                            contentDescription = stringResource(R.string.mpris_loop)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.playPause() },
                    modifier = Modifier.size(64.dp)
                ) {
                    val icon = if (playerStatus?.isPlaying == true) R.drawable.ic_pause_black
                               else R.drawable.ic_play_black
                    Icon(
                        painterResource(icon),
                        contentDescription = stringResource(
                            if (playerStatus?.isPlaying == true) R.string.mpris_pause
                            else R.string.mpris_play
                        ),
                        modifier = Modifier.size(48.dp)
                    )
                }

                if (playerStatus?.isShuffleAllowed == true) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        val icon =
                            if (playerStatus?.shuffle == true) R.drawable.ic_shuffle_on_black else R.drawable.ic_shuffle_off_black
                        Icon(
                            painterResource(icon),
                            contentDescription = stringResource(R.string.mpris_shuffle)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { viewModel.previous() },
                    enabled = playerStatus?.isGoPreviousAllowed == true
                ) {
                    Icon(
                        painterResource(R.drawable.ic_previous_black),
                        contentDescription = stringResource(R.string.mpris_previous)
                    )
                }
                if (playerStatus?.isSeekAllowed == true) {
                    IconButton(onClick = { viewModel.seek(-10000) }) {
                        Icon(
                            painterResource(R.drawable.ic_rewind_black),
                            contentDescription = stringResource(R.string.mpris_rew)
                        )
                    }
                }
                IconButton(onClick = { viewModel.stop() }) {
                    Icon(
                        painterResource(R.drawable.ic_stop),
                        contentDescription = stringResource(R.string.mpris_stop)
                    )
                }
                if (playerStatus?.isSeekAllowed == true) {
                    IconButton(onClick = { viewModel.seek(10000) }) {
                        Icon(
                            painterResource(R.drawable.ic_fast_forward_black),
                            contentDescription = stringResource(R.string.mpris_ff)
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.next() },
                    enabled = playerStatus?.isGoNextAllowed == true
                ) {
                    Icon(
                        painterResource(R.drawable.ic_next_black),
                        contentDescription = stringResource(R.string.mpris_next)
                    )
                }
            }

            Spacer(modifier = Modifier.size(16.dp))

            if (playerStatus?.isSeekAllowed == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(durationToProgress(playerPosition.milliseconds))
                    Slider(
                        value = playerPosition.toFloat(),
                        onValueChange = { /* Update preview? */ },
                        onValueChangeFinished = { viewModel.setPosition(playerPosition) },
                        valueRange = 0f..(playerStatus?.length?.toFloat() ?: 0f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        durationToProgress(
                            playerStatus?.length?.milliseconds ?: 0.milliseconds
                        )
                    )
                }
            }

            if (playerStatus?.isSetVolumeAllowed == true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_volume),
                        contentDescription = stringResource(R.string.mpris_volume)
                    )
                    Slider(
                        value = playerStatus?.volume?.toFloat() ?: 0f,
                        onValueChange = { volume -> viewModel.setVolume(volume.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsIsland(
    deviceId: String
) {
    val navigator: Navigator = koinInject()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlButton(
                titleName = stringResource(R.string.input),
                contentName = stringResource(R.string.input),
                icon = painterResource(R.drawable.input),
                onClick = { navigator.goTo(MprisSourceKey(deviceId)) }
            )
            ControlButton(
                titleName = stringResource(R.string.output),
                contentName = stringResource(R.string.output),
                icon = painterResource(R.drawable.speaker_group),
                onClick = { navigator.goTo(MprisSinkKey(deviceId)) }
            )
        }
    }
}

@Composable
private fun RowScope.ControlButton(
    titleName: String,
    contentName: String,
    icon: Painter,
    onClick: () -> Unit
) {
    val font = remember { googleSans(weight = 600f) }
    Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = colorScheme.onSurface
            )
            Text(
                text = titleName,
                fontFamily = font,
                color = colorScheme.onSurface
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = onClick
        ) {
            Text(contentName)
        }
    }
}

@Composable
fun SinkSelector(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val navigator: Navigator = koinInject()
    val sinks by viewModel.sinks.collectAsState()
    KdeTheme {
        Dialog(onDismissRequest = { navigator.goBack() }) {
            Column(
                modifier = Modifier
                    .height(400.dp)
                    .fillMaxWidth()
                    .card(colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val font = remember { googleSans(weight = 600f) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.speaker_group),
                        contentDescription = null,
                        tint = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.output),
                        fontFamily = font,
                        fontSize = 20.sp,
                        color = colorScheme.onSurface
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sinks) { sink ->
                        SinkItem(sink = sink, viewModel::setSinkEnabled, viewModel::setSinkVolume)
                    }
                }
                TextButton(
                    modifier = Modifier
                        .align(Alignment.End),
                    onClick = { navigator.goBack() }
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
fun SourceSelector(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val navigator: Navigator = koinInject()
    val playerList by viewModel.playerList.collectAsState()
    val selectedPlayerName by viewModel.selectedPlayerName.collectAsState()

    KdeTheme {
        Dialog(onDismissRequest = { navigator.goBack() }) {
            Column(
                modifier = Modifier
                    .height(400.dp)
                    .fillMaxWidth()
                    .card(colorScheme.surfaceContainerLowest)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val font = remember { googleSans(weight = 600f) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(32.dp),
                        painter = painterResource(R.drawable.input),
                        contentDescription = null,
                        tint = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.input),
                        fontFamily = font,
                        fontSize = 20.sp,
                        color = colorScheme.onSurface
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playerList) { source ->
                        SourceItem (source, source == selectedPlayerName) { viewModel.selectPlayer(source) }
                    }
                }
                TextButton(
                    modifier = Modifier
                        .align(Alignment.End),
                    onClick = { navigator.goBack() }
                ) {
                    Text(stringResource(R.string.close))
                }
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
