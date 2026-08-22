package org.kde.kdeconnect.plugins.mousepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.acceleration
import org.kde.kdeconnect.generated.resources.actions
import org.kde.kdeconnect.generated.resources.arrow_upload_ready
import org.kde.kdeconnect.generated.resources.backlight_high
import org.kde.kdeconnect.generated.resources.double_tap_to_drag
import org.kde.kdeconnect.generated.resources.drag_drop
import org.kde.kdeconnect.generated.resources.gyro
import org.kde.kdeconnect.generated.resources.hold_to_drag
import org.kde.kdeconnect.generated.resources.keystroke_capture
import org.kde.kdeconnect.generated.resources.missing_controller
import org.kde.kdeconnect.generated.resources.mousepad_double_tap_settings_title
import org.kde.kdeconnect.generated.resources.mousepad_scroll_direction_title
import org.kde.kdeconnect.generated.resources.mousepad_single_tap_settings_title
import org.kde.kdeconnect.generated.resources.mousepad_tap_entries
import org.kde.kdeconnect.generated.resources.mousepad_tap_values
import org.kde.kdeconnect.generated.resources.mousepad_triple_tap_settings_title
import org.kde.kdeconnect.generated.resources.plugin_settings_with_name
import org.kde.kdeconnect.generated.resources.pref_plugin_mousepad
import org.kde.kdeconnect.generated.resources.scroll
import org.kde.kdeconnect.generated.resources.scrolling
import org.kde.kdeconnect.generated.resources.sendkeystrokes_pref_enabled
import org.kde.kdeconnect.generated.resources.sendkeystrokes_pref_enabled_summary
import org.kde.kdeconnect.generated.resources.sendkeystrokes_safe_text_enabled
import org.kde.kdeconnect.generated.resources.sendkeystrokes_safe_text_enabled_summary
import org.kde.kdeconnect.generated.resources.sensitivity
import org.kde.kdeconnect.generated.resources.speed
import org.kde.kdeconnect.generated.resources.swap_calls
import org.kde.kdeconnect.generated.resources.swipe_down
import org.kde.kdeconnect.generated.resources.touch_double
import org.kde.kdeconnect.generated.resources.touch_triple
import org.kde.kdeconnect.generated.resources.trackpad_input
import org.kde.kdeconnect.generated.resources.trackpad_input_2
import org.kde.kdeconnect.generated.resources.trackpad_input_3
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.KdeThemePreviews
import org.kde.kdeconnect.ui.components.SliderPreference
import org.kde.kdeconnect.ui.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MousePadSettingsScreen(
    viewModel: MousePadSettingsViewModel = koinViewModel(),
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MousePadSettingsScreenContent(
        uiState = uiState,
        navigator = navigator,
        onSingleTapChanged = viewModel::setSingleTap,
        onDoubleTapChanged = viewModel::setDoubleTap,
        onTripleTapChanged = viewModel::setTripleTap,
        onSensitivityChanged = viewModel::setSensitivity,
        onAccelerationProfileChanged = viewModel::setAccelerationProfile,
        onScrollDirectionChanged = viewModel::setScrollDirection,
        onScrollSensitivityChanged = viewModel::setScrollSensitivity,
        onGyroSensitivityChanged = viewModel::setGyroSensitivity,
        onDoubleTapDragEnabledChanged = viewModel::setDoubleTapDragEnabled,
        onSendKeystrokesEnabledChanged = viewModel::setSendKeystrokesEnabled,
        onSendSafeTextImmediatelyChanged = viewModel::setSendSafeTextImmediately,
    )
}

@Composable
fun MousePadSettingsScreenContent(
    uiState: MousePadSettingsUiState,
    navigator: Navigator,
    onSingleTapChanged: (String) -> Unit,
    onDoubleTapChanged: (String) -> Unit,
    onTripleTapChanged: (String) -> Unit,
    onSensitivityChanged: (Long) -> Unit,
    onAccelerationProfileChanged: (Long) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onScrollSensitivityChanged: (Long) -> Unit,
    onGyroSensitivityChanged: (Long) -> Unit,
    onDoubleTapDragEnabledChanged: (Boolean) -> Unit,
    onSendKeystrokesEnabledChanged: (Boolean) -> Unit,
    onSendSafeTextImmediatelyChanged: (Boolean) -> Unit,
) {
    val tapEntries = stringArrayResource(Res.array.mousepad_tap_entries)
    val tapValues = stringArrayResource(Res.array.mousepad_tap_values)
    val tapPairs = tapValues.zip(tapEntries)

    val sensitivitySteps = (0L..200L step 10L).map { it to it.toString() }

    HazeScaffold(
        title = stringResource(Res.string.plugin_settings_with_name, stringResource(Res.string.pref_plugin_mousepad)),
        backAction = BackAction.Normal(navigator),
    ) {
        CategoryTitleTextSmall(stringResource(Res.string.actions))
        Row (
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogItemSelectPreference(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.mousepad_single_tap_settings_title),
                icon = painterResource(Res.drawable.trackpad_input),
                value = uiState.singleTap,
                values = tapPairs,
                onValueChanged = onSingleTapChanged
            )
            DialogItemSelectPreference(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = stringResource(Res.string.mousepad_double_tap_settings_title),
                icon = painterResource(Res.drawable.trackpad_input_2),
                value = uiState.doubleTap,
                values = tapPairs,
                onValueChanged = onDoubleTapChanged
            )
        }
        Row (
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogItemSelectPreference(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.mousepad_triple_tap_settings_title),
                icon = painterResource(Res.drawable.trackpad_input_3),
                value = uiState.tripleTap,
                values = tapPairs,
                onValueChanged = onTripleTapChanged
            )
            DialogItemSelectPreference(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(Res.string.drag_drop),
                icon = painterResource(Res.drawable.swipe_down),
                values = listOf(true to stringResource(Res.string.double_tap_to_drag), false to stringResource(Res.string.hold_to_drag)),
                value = uiState.doubleTapDragEnabled,
                onValueChanged = onDoubleTapDragEnabledChanged
            )
        }
        CategoryTitleTextSmall(stringResource(Res.string.sensitivity))
        SliderPreference(
            title = stringResource(Res.string.speed),
            icon = painterResource(Res.drawable.touch_double),
            value = uiState.sensitivity.toLong(),
            values = (1..10).map { it.toLong() to it.toString() },
            onValueChanged = onSensitivityChanged
        )
        SliderPreference (
            title = stringResource(Res.string.acceleration),
            icon = painterResource(Res.drawable.touch_triple),
            value = uiState.acceleration.toLong(),
            values = (0..10).map { it.toLong() to it.toString() },
            onValueChanged = onAccelerationProfileChanged
        )
        SliderPreference(
            title = stringResource(Res.string.scroll),
            icon = painterResource(Res.drawable.swipe_down),
            value = uiState.scrollSensitivity,
            values = sensitivitySteps,
            onValueChanged = onScrollSensitivityChanged
        )
        SliderPreference(
            title = stringResource(Res.string.gyro),
            icon = painterResource(Res.drawable.missing_controller),
            value = uiState.gyroSensitivity,
            values = sensitivitySteps,
            onValueChanged = onGyroSensitivityChanged
        )

        CategoryTitleTextSmall(stringResource(Res.string.scrolling))
        SwitchPreference(
            title = stringResource(Res.string.mousepad_scroll_direction_title),
            icon = painterResource(Res.drawable.swap_calls),
            value = uiState.scrollDirection,
            onValueChanged = onScrollDirectionChanged
        )

        CategoryTitleTextSmall(stringResource(Res.string.keystroke_capture))
        SwitchPreference(
            title = stringResource(Res.string.sendkeystrokes_pref_enabled),
            summary = stringResource(Res.string.sendkeystrokes_pref_enabled_summary),
            icon = painterResource(Res.drawable.backlight_high),
            value = uiState.sendKeystrokesEnabled,
            onValueChanged = onSendKeystrokesEnabledChanged
        )
        SwitchPreference(
            title = stringResource(Res.string.sendkeystrokes_safe_text_enabled),
            summary = stringResource(Res.string.sendkeystrokes_safe_text_enabled_summary),
            icon = painterResource(Res.drawable.arrow_upload_ready),
            value = uiState.sendSafeTextImmediately,
            onValueChanged = onSendSafeTextImmediatelyChanged
        )
    }
}

@KdeThemePreviews
@Composable
fun MousePadSettingsPreview() {
    MousePadSettingsScreenContent(
        uiState = MousePadSettingsUiState(),
        navigator = Navigator(),
        onSingleTapChanged = {},
        onDoubleTapChanged = {},
        onTripleTapChanged = {},
        onSensitivityChanged = {},
        onAccelerationProfileChanged = {},
        onScrollDirectionChanged = {},
        onScrollSensitivityChanged = {},
        onGyroSensitivityChanged = {},
        onDoubleTapDragEnabledChanged = {},
        onSendKeystrokesEnabledChanged = {},
        onSendSafeTextImmediatelyChanged = {}
    )
}
