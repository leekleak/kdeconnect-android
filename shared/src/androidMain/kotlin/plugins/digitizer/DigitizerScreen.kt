package org.kde.kdeconnect.plugins.digitizer

import android.app.Activity
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.*
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DigitizerScreen(
    deviceId: String,
    viewModel: DigitizerViewModel = koinViewModel(key = "DigitizerViewModel_$deviceId") { parametersOf(deviceId) },
    navigator: Navigator
) {
    val context = LocalContext.current
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
        title = stringResource(Res.string.pref_plugin_digitizer),
        backAction = BackAction.Normal(navigator),
        scrollState = null,
        showTitle = !isFullscreen,
        actions = {
            IconButton(
                onClick = { enableFullscreen() }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.fullscreen),
                    contentDescription = stringResource(Res.string.enable_fullscreen)
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = BottomStart
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
                    containerColor = if (buttonPressed) FloatingActionButtonDefaults.containerColor else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(painterResource(Res.drawable.draw), null)
                }
            }
        }
    }
}
