package org.kde.kdeconnect.plugins.presenter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Looper
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PresenterPluginSettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PresenterScreen(
    deviceId: String,
    viewModel: PresenterViewModel = koinViewModel(key = "PresenterViewModel_$deviceId") { parametersOf(deviceId) },
    navigator: Navigator,
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    val offScreenControlsSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val volumeKeys by viewModel.volumeKeys.collectAsStateWithLifecycle()
    if (volumeKeys && offScreenControlsSupported) {
        DisposableEffect(Unit) {
            val player = object : SimpleBasePlayer(Looper.getMainLooper()) {
                private var state = State.Builder()
                    .setAvailableCommands(
                        Player.Commands.Builder()
                            .addAll(COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS)
                            .build()
                    )
                    .setPlaybackState(STATE_READY)
                    .setPlayWhenReady(true, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    .setPlaylist(
                        listOf(
                            MediaItemData.Builder("kdeconnect-dummy")
                                .setMediaItem(MediaItem.EMPTY)
                                .build()
                        )
                    )
                    .setDeviceInfo(
                        DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                            .build()
                    )
                    .build()

                override fun getState(): State = state

                override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
                    viewModel.sendNext()
                    return Futures.immediateFuture(null)
                }

                override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
                    viewModel.sendPrevious()
                    return Futures.immediateFuture(null)
                }

                override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
                    return Futures.immediateFuture(null)
                }
            }

            val mediaSession = MediaSession.Builder(context, player).build()

            onDispose {
                mediaSession.release()
                player.release()
            }
        }
    }

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_presenter),
        backAction = BackAction.Normal(navigator),
        actions = {
            IconButton(
                onClick = { navigator.goTo(PresenterPluginSettingsKey) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(id = R.string.open)
                )
            }
        }
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            TopButton(
                text = stringResource(R.string.presenter_fullscreen),
                painter = painterResource(R.drawable.fullscreen),
                onClick = { viewModel.sendFullscreen() }
            )
            TopButton(
                text = stringResource(R.string.presenter_exit),
                painter = painterResource(R.drawable.close),
                onClick = { viewModel.sendEsc() }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(3f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            FilledIconButton(
                onClick = { viewModel.sendPrevious() },
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
            FilledIconButton(
                onClick = { viewModel.sendNext() },
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
            FilledIconButton(
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
