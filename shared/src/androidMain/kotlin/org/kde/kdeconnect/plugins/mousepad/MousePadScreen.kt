package org.kde.kdeconnect.plugins.mousepad

import android.app.Activity
import android.content.res.Resources
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.gyro_mouse_enabled_title
import org.kde.kdeconnect.generated.resources.keyboard
import org.kde.kdeconnect.generated.resources.missing_controller
import org.kde.kdeconnect.generated.resources.pref_plugin_mousepad
import org.kde.kdeconnect.generated.resources.send
import org.kde.kdeconnect.generated.resources.send_text
import org.kde.kdeconnect.generated.resources.settings
import org.kde.kdeconnect.generated.resources.show_keyboard
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.KdeButton
import org.kde.kdeconnect.ui.components.SearchBar
import org.kde.kdeconnect.ui.components.px
import org.kde.kdeconnect.ui.components.smartDashBorder
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.painterResource as painterResourceRes
import org.jetbrains.compose.resources.stringResource as stringResourceRes

@Composable
fun MousePadScreen(
    deviceId: String,
    viewModel: MousePadViewModel = koinViewModel(key = "MousePadViewModel_$deviceId") { parametersOf(deviceId) },
    navigator: Navigator,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val isKeyboardEnabled by viewModel.isKeyboardEnabled.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    var focusCaptured by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible && focusCaptured) {
            focusManager.clearFocus()
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.onResume()

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.onPause()
        }
    }

    HazeScaffold(
        title = stringResourceRes(Res.string.pref_plugin_mousepad),
        backAction = BackAction.Normal(navigator),
        actions = {
            IconButton(
                modifier = Modifier.focusable(false),
                onClick = {
                    navigator.goTo(MousePadPluginSettingsKey)
                }
            ) {
                Icon(painterResourceRes(Res.drawable.settings), stringResourceRes(Res.string.settings))
            }
        }
    ) {

        Row(modifier = Modifier.height(IntrinsicSize.Max)) {
            val textFieldState = rememberTextFieldState()
            SearchBar(
                modifier = Modifier
                    .weight(1f)
                    .background(colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge)
                    .padding(4.dp),
                state = textFieldState,
                contentColor = colorScheme.onPrimaryContainer,
                caretColor = colorScheme.primary,
                placeholder = stringResourceRes(Res.string.send_text),
            ) {
                FilledIconButton(
                    onClick = {
                        viewModel.sendComposed(textFieldState.text.toString())
                        textFieldState.clearText()
                    },
                ) {
                    Icon(
                        painter = painterResourceRes(Res.drawable.send),
                        contentDescription = stringResourceRes(Res.string.send)
                    )
                }
            }
            FilledIconToggleButton(
                modifier = Modifier.fillMaxHeight(),
                checked = focusCaptured,
                enabled = isKeyboardEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        focusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                },
            ) {
                Icon(painterResourceRes(Res.drawable.keyboard), stringResourceRes(Res.string.show_keyboard))
            }
            FilledIconToggleButton(
                modifier = Modifier.fillMaxHeight(),
                checked = viewModel.allowGyro,
                enabled = viewModel.isGyroSensorAvailable(),
                onCheckedChange = { isChecked ->
                    viewModel.setGyroEnabled(isChecked)
                },
            ) {
                Icon(painterResourceRes(Res.drawable.missing_controller), stringResourceRes(Res.string.gyro_mouse_enabled_title))
            }
        }

        val width = 2.dp.px
        val dashLength = 8.dp.px
        val cornerRadius = 22.dp.px
        val outlineColor = colorScheme.outline
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .drawBehind { smartDashBorder(cornerRadius, dashLength, width, outlineColor) }
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    if (it.text.length > textFieldValue.text.length) {
                        viewModel.sendChars(it.text.substring(textFieldValue.text.length))
                    }
                    textFieldValue = it
                },
                modifier = Modifier
                    .size(0.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        focusCaptured = focusState.isFocused
                    }
                    .onKeyEvent {
                        viewModel.onKeyEvent(it.nativeKeyEvent)
                    }
            )

            TouchPad(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel
            )
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KdeButton(
                    onClick = { viewModel.sendLeftClick() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusable(false),
                    shape = RoundedCornerShape(8.dp, 8.dp, 8.dp, 16.dp)
                )
                KdeButton(
                    onClick = { viewModel.sendRightClick() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusable(false),
                    shape = RoundedCornerShape(8.dp, 8.dp, 16.dp, 8.dp)
                )
            }
        }
    }
}

@Composable
fun TouchPad(
    modifier: Modifier = Modifier,
    viewModel: MousePadViewModel
) {
    val density = LocalDensity.current.density
    val xdpi = Resources.getSystem().displayMetrics.xdpi
    val displayDpiMultiplier = 240.0f / xdpi
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val minDistanceToSendScroll = 2.5f * density
    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()
    val doubleClickTimeout = ViewConfiguration.getDoubleTapTimeout().milliseconds

    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var accumulatedScrollY by remember { mutableDoubleStateOf(0.0) }

    var isScrolling by remember { mutableStateOf(false) }

    val mouseDelta = remember { PointerAccelerationProfile.MouseDelta() }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    lastX = down.position.x
                    lastY = down.position.y
                    accumulatedScrollY = 0.0
                    isScrolling = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        val change = event.changes.first()

                        if (event.type == PointerEventType.Move) {
                            if (pointerCount == 1) {
                                if (!isScrolling) {
                                    val dx = (change.position.x - lastX) * displayDpiMultiplier * viewModel.currentSensitivity
                                    val dy = (change.position.y - lastY) * displayDpiMultiplier * viewModel.currentSensitivity

                                    viewModel.accelerationProfile?.let { profile ->
                                        profile.touchMoved(dx, dy, event.calculateTime())
                                        profile.commitAcceleratedMouseDelta(mouseDelta)
                                        viewModel.sendMouseDelta(mouseDelta.x, mouseDelta.y)
                                    } ?: run {
                                        viewModel.sendMouseDelta(dx, dy)
                                    }
                                }
                            } else if (pointerCount >= 2) {
                                isScrolling = true
                                val dy = (change.position.y - lastY).toDouble()
                                accumulatedScrollY += dy * viewModel.scrollCoefficient

                                if (accumulatedScrollY > minDistanceToSendScroll || accumulatedScrollY < -minDistanceToSendScroll) {
                                    viewModel.sendScroll(viewModel.scrollDirection * accumulatedScrollY)
                                    accumulatedScrollY = 0.0
                                }
                            }

                            lastX = change.position.x
                            lastY = change.position.y
                        }

                        if (event.changes.all { it.changedToUp() }) {
                            if (viewModel.isDragging) {
                                viewModel.sendLeftClick()
                            }
                            break
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                var singleCLickQueued = false
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startTime = down.uptimeMillis
                    var maxPointers = 1
                    if (singleCLickQueued) {
                        singleCLickQueued = false
                        if (viewModel.doubleTapDragEnabled) {
                            if (!viewModel.isDragging) {
                                viewModel.sendSingleHold()
                                scope.launch {
                                    delay(100.milliseconds) // Haptics feel /too/ fast otherwise lol
                                    haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                }
                            }
                        } else {
                            viewModel.sendDoubleClick()
                        }
                        return@awaitEachGesture
                    }

                    val longPressJob = scope.launch {
                        delay(ViewConfiguration.getLongPressTimeout().milliseconds)
                        if (
                            !viewModel.doubleTapDragEnabled &&
                            !viewModel.isDragging &&
                            maxPointers == 1
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            viewModel.sendSingleHold()
                        }
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > maxPointers) maxPointers = event.changes.size
                        if (event.calculatePan().getDistance() > 0.0001f) longPressJob.cancel()

                        if (event.type == PointerEventType.Release || event.changes.all { it.changedToUp() }) {
                            val duration = event.calculateTime() - startTime

                            if (duration < tapTimeout) {
                                longPressJob.cancel()
                                when (maxPointers) {
                                    1 -> {
                                        assert(!singleCLickQueued)
                                        singleCLickQueued = true
                                        scope.launch {
                                            delay(doubleClickTimeout)

                                            if (singleCLickQueued) { // If the click hasn't been consumed as two, do simple action
                                                singleCLickQueued = false
                                                viewModel.performClickAction(viewModel.singleTapAction)
                                            }
                                        }

                                    }
                                    2 -> viewModel.performClickAction(viewModel.doubleTapAction)
                                    3 -> viewModel.performClickAction(viewModel.tripleTapAction)
                                }
                            }
                            break
                        }
                    }
                }
            }
    )
}

private fun PointerEvent.calculateTime(): Long {
    return changes.firstOrNull()?.uptimeMillis ?: 0L
}
