package org.kde.kdeconnect.ui.compose.screen.presenter

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media.VolumeProviderCompat
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val VOLUME_UP = 1
private const val VOLUME_DOWN = -1

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PresenterScreen(
    deviceId: String,
    viewModel: PresenterViewModel = koinViewModel(key = "PresenterViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val context = LocalContext.current
    val plugin = viewModel.plugin ?: return
    val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager

    val offScreenControlsSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    LaunchedEffect(Unit) {
        viewModel.applyPrefs(context)
    }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (viewModel.volumeKeys && offScreenControlsSupported) {
        DisposableEffect(Unit) {
            val mediaSession = MediaSessionCompat(context, "kdeconnect")
            val volumeProvider = object : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE, 0, 0) {
                override fun onAdjustVolume(direction: Int) {
                    if (direction == VOLUME_UP) {
                        plugin.sendNext()
                    } else if (direction == VOLUME_DOWN) {
                        plugin.sendPrevious()
                    }
                }
            }
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, 0, 0f).build()
            )
            mediaSession.setPlaybackToRemote(volumeProvider)
            mediaSession.isActive = true

            onDispose {
                mediaSession.release()
            }
        }
    }

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_presenter),
        backButton = true,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            TopButton(
                text = stringResource(R.string.presenter_fullscreen),
                painter = painterResource(R.drawable.fullscreen),
                onClick = { plugin.sendFullscreen() }
            )
            TopButton(
                text = stringResource(R.string.presenter_exit),
                painter = painterResource(R.drawable.close),
                onClick = { plugin.sendEsc() }
            )
        }
        if (viewModel.volumeKeys) Text(
            text = stringResource(if (offScreenControlsSupported) R.string.presenter_volume_keys_tip else R.string.presenter_volume_keys_foreground_tip),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(3f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            FilledIconButton (
                onClick = { plugin.sendPrevious() },
                shape = MaterialTheme.shapes.extraLarge,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxHeight(0.35f)
                    .weight(1f),
            ) {
                Icon(
                    modifier = Modifier.size(42.dp),
                    painter = painterResource(R.drawable.arrow_back_ios_new),
                    contentDescription = stringResource(R.string.mpris_previous),
                )
            }
            FilledIconButton (
                onClick = { plugin.sendNext() },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxHeight(0.55f)
                    .weight(1f),
            ) {
                Icon(
                    modifier = Modifier.size(42.dp),
                    painter = painterResource(R.drawable.arrow_forward_ios),
                    contentDescription = stringResource(R.string.mpris_next)
                )
            }
        }
        if (sensorManager != null) {
            FilledIconButton (
                onClick = {},
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .pointerInteropFilter { event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                sensorManager.registerListener(
                                    viewModel,
                                    sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                                    SensorManager.SENSOR_DELAY_GAME
                                )
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                sensorManager.unregisterListener(viewModel)
                                viewModel.stopPointer()
                                false
                            }

                            else -> false
                        }
                    },
            ) {
                Icon(
                    modifier = Modifier.size(42.dp),
                    painter = painterResource(R.drawable.ads_click),
                    contentDescription = stringResource(R.string.presenter_pointer)
                )
            }
        }
    }
}

@Composable
private fun TopButton(
    text: String,
    painter: Painter,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
