package org.kde.kdeconnect.plugins.mpris

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.FancyDialog
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
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        PlayerIsland(viewModel)
        ControlsIsland(deviceId, viewModel)
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun PlayerIsland(viewModel: MprisViewModel) {
    val playerStatus by viewModel.playerStatus.collectAsStateWithLifecycle()
    val playerPosition by viewModel.playerPosition.collectAsStateWithLifecycle()

    var isBright by remember { mutableStateOf(true) }
    val backgroundColor by animateColorAsState(if (isBright) Color.White else Color.Black)
    val contentColor by animateColorAsState(if (isBright) Color.Black else Color.White)

    val hazeState = rememberHazeState()

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(backgroundColor)
    ) {
        var currentCover by remember { mutableStateOf<Painter?>(null) }
        AsyncImage(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .hazeSource(hazeState)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.66f to Color.Transparent,
                            1f to backgroundColor
                        ),
                        topLeft = Offset(x = 0f, y = size.height * 2 / 3),
                        size = Size(size.width, size.height / 3f)
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
                .allowHardware(false) // Needed to get a pallete
                .build(),
            placeholder = currentCover,
            onSuccess = { result ->
                currentCover = result.painter
                val bitmap = (result.result.image as BitmapImage).bitmap
                Palette.from(bitmap).generate { palette ->
                    palette?.dominantSwatch?.let {
                        isBright = it.hsl[2] > 0.5f
                    }
                }
            },
            contentDescription = null,
            error = painterResource(R.drawable.music_note),
            contentScale = ContentScale.Crop
        )
        Column {
            Spacer(modifier = Modifier
                .aspectRatio(1.5f)
                .fillMaxWidth())
            Spacer(modifier = Modifier
                .aspectRatio(1.5f)
                .fillMaxWidth()
                .hazeEffect(hazeState, HazeMaterials.thick(backgroundColor)) {
                    progressive =
                        HazeProgressive.verticalGradient(
                            startIntensity = 0f,
                            endIntensity = 1f
                        )
                }
            )
        }

        Row(Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)) {
            if (playerStatus?.isShuffleAllowed == true) {
                val checked = playerStatus?.shuffle == true
                FilledIconToggleButton(
                    checked = checked,
                    onCheckedChange = { viewModel.toggleShuffle() }
                ) {
                    AnimatedContent(checked) {
                        Icon(
                            painterResource(if (it) R.drawable.shuffle_on else R.drawable.shuffle),
                            contentDescription = stringResource(R.string.mpris_shuffle)
                        )
                    }
                }
            }
            if (playerStatus?.isLoopStatusAllowed == true) {
                val checked = playerStatus?.loopStatus != "Track" && playerStatus?.loopStatus != "Playlist"
                FilledIconToggleButton(
                    checked = checked,
                    onCheckedChange = { viewModel.toggleLoopStatus() }
                ) {
                    val icon = when (playerStatus?.loopStatus) {
                        "Track" -> R.drawable.repeat_one
                        "Playlist" -> R.drawable.repeat_on
                        else -> R.drawable.repeat
                    }
                    AnimatedContent(icon) {
                        Icon(
                            painterResource(it),
                            contentDescription = stringResource(R.string.mpris_loop)
                        )
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier
                .aspectRatio(1.2f)
                .fillMaxWidth())

            val title = playerStatus?.title ?: ""
            val artist = playerStatus?.artist ?: ""
            val fontBold = remember { googleSans(weight = 900f) }
            val font = remember { googleSans(weight = 600f) }
            AnimatedContent(title) {
                Text(
                    text = it,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    fontSize = 36.sp,
                    color = contentColor,
                    fontFamily = fontBold,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center
                )
            }
            AnimatedContent(artist) {
                Text(
                    text = it,
                    modifier = Modifier.basicMarquee(),
                    fontSize = 16.sp,
                    maxLines = 1,
                    color = contentColor,
                    fontFamily = font,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            MaterialTheme(colorScheme =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isBright) dynamicLightColorScheme(LocalContext.current)
                    else dynamicDarkColorScheme(LocalContext.current)
                } else {
                    if (isBright) lightColorScheme()
                    else darkColorScheme()
                }
            ) {

                val monospacedFont = FontFamily(Font(R.font.jetbrains_mono_regular))
                if (playerStatus?.isSeekAllowed == true) {
                    Column (Modifier.padding(16.dp)) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = { playerPosition.toFloat()/(playerStatus?.length?.toFloat() ?: 0f )},
                            waveSpeed = if (playerStatus?.isPlaying == true) WavyProgressIndicatorDefaults.LinearDeterminateWavelength else 0.dp
                        )
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = durationToProgress(playerPosition.milliseconds),
                                fontFamily = monospacedFont,
                                color = contentColor
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = durationToProgress(playerStatus?.length?.milliseconds ?: 0.milliseconds),
                                fontFamily = monospacedFont,
                                color = contentColor
                            )
                        }
                    }
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (playerStatus?.isSeekAllowed == true) {
                        FilledTonalIconButton(
                            modifier = Modifier
                                .width(42.dp)
                                .height(36.dp),
                            shape = MaterialTheme.shapes.medium,
                            onClick = { viewModel.seek(-30000) }
                        ) {
                            Icon(
                                painterResource(R.drawable.replay_30),
                                contentDescription = stringResource(R.string.mpris_rew)
                            )
                        }
                    }
                    FilledTonalIconButton(
                        modifier = Modifier
                            .width(56.dp)
                            .height(42.dp),
                        onClick = { viewModel.previous() },
                        shape = MaterialTheme.shapes.medium,
                        enabled = playerStatus?.isGoPreviousAllowed == true
                    ) {
                        Icon(
                            painterResource(R.drawable.skip_previous),
                            contentDescription = stringResource(R.string.mpris_previous)
                        )
                    }
                    val playChecked = playerStatus?.isPlaying == true
                    FilledIconToggleButton(
                        modifier = Modifier
                            .width(100.dp)
                            .height(64.dp),
                        checked = playChecked,
                        onCheckedChange = { viewModel.playPause() },
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        AnimatedContent(playChecked) {
                            Icon(
                                modifier = Modifier.size(48.dp),
                                painter = painterResource(if (it) R.drawable.pause else R.drawable.play_arrow),
                                contentDescription = stringResource(if (it) R.string.mpris_pause else R.string.mpris_play),
                            )
                        }
                    }
                    FilledTonalIconButton(
                        modifier = Modifier
                            .width(56.dp)
                            .height(42.dp),
                        onClick = { viewModel.next() },
                        shape = MaterialTheme.shapes.medium,
                        enabled = playerStatus?.isGoNextAllowed == true
                    ) {
                        Icon(
                            painterResource(R.drawable.skip_next),
                            contentDescription = stringResource(R.string.mpris_next)
                        )
                    }
                    if (playerStatus?.isSeekAllowed == true) {
                        FilledTonalIconButton(
                            modifier = Modifier
                                .width(42.dp)
                                .height(36.dp),
                            shape = MaterialTheme.shapes.medium,
                            onClick = { viewModel.seek(30000) }
                        ) {
                            Icon(
                                painterResource(R.drawable.forward_30),
                                contentDescription = stringResource(R.string.mpris_ff)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsIsland(
    deviceId: String,
    viewModel: MprisViewModel
) {
    val navigator: Navigator = koinInject()
    val playerStatus by viewModel.playerStatus.collectAsStateWithLifecycle()
    val sinks by viewModel.sinks.collectAsStateWithLifecycle()
    val outputName by remember { derivedStateOf {
        sinks.firstOrNull { it.isDefault }?.description
    } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .card(colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlButton(
                titleName = stringResource(R.string.input),
                contentName = playerStatus?.playerName ?: stringResource(R.string.no_input),
                icon = painterResource(R.drawable.input),
                onClick = { navigator.goTo(MprisSourceKey(deviceId)) }
            )
            ControlButton(
                titleName = stringResource(R.string.output),
                contentName = outputName ?: stringResource(R.string.no_output),
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                color = colorScheme.onSurface,
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(0.dp),
            onClick = onClick
        ) {
            Box(modifier = Modifier
                .basicMarquee()
                .padding(vertical = 8.dp, horizontal = 16.dp)) {
                Text(
                    text = contentName,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SinkSelector(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val navigator: Navigator = koinInject()
    val sinks by viewModel.sinks.collectAsStateWithLifecycle()
    KdeTheme {
        FancyDialog(
            modifier = Modifier.height(400.dp),
            title = stringResource(R.string.output),
            icon = painterResource(R.drawable.speaker_group),
            content = {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sinks) { sink ->
                        SinkItem(sink = sink, viewModel::setSinkEnabled, viewModel::setSinkVolume)
                    }
                }
            },
            actionButton = {},
            onDismissRequest = { navigator.goBack() }
        )
    }
}

@Composable
fun SourceSelector(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val navigator: Navigator = koinInject()
    val playerList by viewModel.playerList.collectAsStateWithLifecycle()
    val selectedPlayerName by viewModel.selectedPlayerName.collectAsStateWithLifecycle()

    KdeTheme {
        FancyDialog(
            modifier = Modifier.height(400.dp),
            title = stringResource(R.string.input),
            icon = painterResource(R.drawable.input),
            content = {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playerList) { source ->
                        SourceItem (source, source == selectedPlayerName) { viewModel.selectPlayer(source) }
                    }
                }
            },
            actionButton = {},
            onDismissRequest = { navigator.goBack() }
        )
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
