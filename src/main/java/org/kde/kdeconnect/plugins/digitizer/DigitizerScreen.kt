package org.kde.kdeconnect.plugins.digitizer

import android.app.Activity
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DigitizerScreen(
    deviceId: String,
    viewModel: DigitizerViewModel = koinViewModel(key = "DigitizerViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val context = LocalContext.current
    val uiState by viewModel.settingsUiState.collectAsStateWithLifecycle()
    var isFullscreen by remember { mutableStateOf(false) }
    var fingerTouchEventsEnabled by remember { mutableStateOf(false) }
    var buttonPressed by remember { mutableStateOf(false) }

    val window = (context as? Activity)?.window
    val windowInsetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

    fun enableFullscreen() {
        isFullscreen = true
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun disableFullscreen() {
        isFullscreen = false
        windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler(isFullscreen) {
        disableFullscreen()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.endSession()
            if (isFullscreen) {
                disableFullscreen()
            }
        }
    }

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_digitizer),
        backButton = true,
        scrollState = null,
        showTitle = !isFullscreen,
        actions = {
            IconButton(
                onClick = { enableFullscreen() }
            ) {
                Icon(
                    painter = painterResource(R.drawable.fullscreen),
                    contentDescription = stringResource(R.string.enable_fullscreen)
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DrawingPad(
                modifier = Modifier.fillMaxSize(),
                fingerTouchEventsEnabled = fingerTouchEventsEnabled,
                onToolEvent = { event ->
                    viewModel.reportEvent(event)
                },
                onFingerTouchEvent = { touching ->
                    buttonPressed = touching
                },
                onSizeChanged = { width, height, xdpi, ydpi ->
                    viewModel.startSession(width, height, xdpi, ydpi)
                }
            )

            if (!uiState.hideDrawButton) {
                val gravity = when (uiState.drawButtonSide) {
                    "top_left" -> Alignment.TopStart
                    "top_right" -> Alignment.TopEnd
                    "bottom_left" -> Alignment.BottomStart
                    "bottom_right" -> Alignment.BottomEnd
                    else -> Alignment.BottomStart
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = gravity
                ) {
                    FloatingActionButton(
                        onClick = { },
                        modifier = Modifier
                            .size(56.dp)
                            .pointerInteropFilter { event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        fingerTouchEventsEnabled = true
                                        true
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        fingerTouchEventsEnabled = false
                                        true
                                    }
                                    else -> false
                                }
                            },
                        containerColor = if (buttonPressed) androidx.compose.material3.FloatingActionButtonDefaults.containerColor else androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(painterResource(R.drawable.ic_draw_24dp), null)
                    }
                }
            }
        }
    }
}
