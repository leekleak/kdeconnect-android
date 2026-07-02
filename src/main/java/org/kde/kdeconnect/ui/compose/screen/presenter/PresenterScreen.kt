package org.kde.kdeconnect.ui.compose.screen.presenter

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media.VolumeProviderCompat
import org.kde.kdeconnect.ui.compose.KdeButton
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
    viewModel: PresenterViewModel = koinViewModel { parametersOf(deviceId) }
) {
    val context = LocalContext.current
    val plugin = viewModel.plugin ?: return
    val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
    var dropdownShownState by remember { mutableStateOf(false) }

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
        scrollState = null,
        backButton = true,
        actions = {
            Box(modifier = Modifier) {
                IconButton(onClick = { dropdownShownState = true }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.extra_options))
                }
                DropdownMenu(expanded = dropdownShownState, onDismissRequest = { dropdownShownState = false }) {
                    DropdownMenuItem(
                        onClick = {
                            dropdownShownState = false
                            plugin.sendFullscreen()
                        },
                        text = { Text(stringResource(R.string.presenter_fullscreen)) },
                    )
                    DropdownMenuItem(
                        onClick = {
                            dropdownShownState = false
                            plugin.sendEsc()
                        },
                        text = { Text(stringResource(R.string.presenter_exit)) },
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (viewModel.volumeKeys) Text(
                text = stringResource(if (offScreenControlsSupported) R.string.presenter_volume_keys_tip else R.string.presenter_volume_keys_foreground_tip),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            @Suppress("DEPRECATION") // we explicitly want the non-mirrored version of the icons
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(3f),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                KdeButton(
                    onClick = { plugin.sendPrevious() },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentDescription = stringResource(R.string.mpris_previous),
                    icon = Icons.Default.ArrowBack,
                )
                KdeButton(
                    onClick = { plugin.sendNext() },
                    contentDescription = stringResource(R.string.mpris_next),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    icon = Icons.Default.ArrowForward,
                )
            }
            if (sensorManager != null) KdeButton(
                onClick = {},
                colors = ButtonDefaults.filledTonalButtonColors(),
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
                text = stringResource(R.string.presenter_pointer),
            )
        }
    }
}
