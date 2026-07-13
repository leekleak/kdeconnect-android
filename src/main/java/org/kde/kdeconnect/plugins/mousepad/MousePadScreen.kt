package org.kde.kdeconnect.plugins.mousepad

import android.app.Activity
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeButton
import org.kde.kdeconnect.ui.compose.components.SearchBar
import org.kde.kdeconnect.ui.compose.components.px
import org.kde.kdeconnect.ui.compose.components.smartDashBorder
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MousePadScreen(
    deviceId: String,
    viewModel: MousePadViewModel = koinViewModel(key = "MousePadViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val navigator = koinInject<Navigator>()

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
        title = stringResource(R.string.pref_plugin_mousepad),
        backButton = true,
        actions = {
            IconButton(
                modifier = Modifier.focusable(false),
                onClick = {
                    navigator.goTo(MousePadPluginSettingsKey)
                }
            ) {
                Icon(painterResource(R.drawable.ic_settings_24dp), stringResource(R.string.settings))
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
                placeholder = stringResource(R.string.send_text),
            ) {
                FilledIconButton(
                    onClick = {
                        viewModel.sendComposed(textFieldState.text.toString())
                        textFieldState.clearText()
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.send),
                        contentDescription = stringResource(R.string.send)
                    )
                }
            }
            FilledIconToggleButton(
                modifier = Modifier.fillMaxHeight(),
                checked = focusCaptured,
                enabled = viewModel.plugin?.isKeyboardEnabled == true,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        focusRequester.requestFocus()
                    } else {
                        focusManager.clearFocus()
                    }
                },
            ) {
                Icon(painterResource(R.drawable.ic_action_keyboard_24dp), stringResource(R.string.show_keyboard))
            }
            FilledIconToggleButton(
                modifier = Modifier.fillMaxHeight(),
                checked = viewModel.allowGyro,
                enabled = viewModel.isGyroSensorAvailable(),
                onCheckedChange = { isChecked ->
                    viewModel.setGyroEnabled(isChecked)
                },
            ) {
                Icon(painterResource(R.drawable.missing_controller), stringResource(R.string.gyro_mouse_enabled_title))
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
            if (viewModel.mouseButtonsEnabled) {
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
}

@Composable
fun TouchPad(
    modifier: Modifier = Modifier,
    viewModel: MousePadViewModel
) {
    val view = LocalView.current
    val density = LocalDensity.current.density
    val xdpi = android.content.res.Resources.getSystem().displayMetrics.xdpi
    val displayDpiMultiplier = 240.0f / xdpi

    val minDistanceToSendScroll = 2.5f * density
    val tapTimeout = ViewConfiguration.getTapTimeout().toLong()

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
                                    val dx =
                                        (change.position.x - lastX) * displayDpiMultiplier * viewModel.currentSensitivity
                                    val dy =
                                        (change.position.y - lastY) * displayDpiMultiplier * viewModel.currentSensitivity

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
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startTime = down.uptimeMillis
                    var maxPointers = 1

                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size > maxPointers) maxPointers = event.changes.size

                        if (event.type == PointerEventType.Release || event.changes.all { it.changedToUp() }) {
                            val duration = event.calculateTime() - startTime
                            if (duration < tapTimeout + 100) {
                                when (maxPointers) {
                                    1 -> viewModel.performClickAction(viewModel.singleTapAction)
                                    2 -> viewModel.performClickAction(viewModel.doubleTapAction)
                                    3 -> viewModel.performClickAction(viewModel.tripleTapAction)
                                }
                            }
                            break
                        }

                        if (!viewModel.doubleTapDragEnabled && maxPointers == 1 && event.calculateTime() - startTime > ViewConfiguration.getLongPressTimeout()) {
                            if (!viewModel.isDragging) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.sendSingleHold()
                            }
                        }
                    }
                }
            }
    )
}

private fun androidx.compose.ui.input.pointer.PointerEvent.calculateTime(): Long {
    return changes.firstOrNull()?.uptimeMillis ?: 0L
}
