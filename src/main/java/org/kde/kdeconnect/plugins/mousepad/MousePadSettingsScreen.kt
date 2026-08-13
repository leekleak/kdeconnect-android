package org.kde.kdeconnect.plugins.mousepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.BackAction
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.SliderPreference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
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
    val tapEntries = stringArrayResource(R.array.mousepad_tap_entries)
    val tapValues = stringArrayResource(R.array.mousepad_tap_values)
    val tapPairs = tapValues.zip(tapEntries)

    val sensitivitySteps = (0L..200L step 10L).map { it to it.toString() }

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_mousepad)),
        backAction = BackAction.Normal(navigator),
    ) {
        CategoryTitleTextSmall(stringResource(R.string.actions))
        Row (
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialogItemSelectPreference(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(R.string.mousepad_single_tap_settings_title),
                icon = painterResource(R.drawable.trackpad_input),
                value = uiState.singleTap,
                values = tapPairs,
                onValueChanged = onSingleTapChanged
            )
            DialogItemSelectPreference(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                title = stringResource(R.string.mousepad_double_tap_settings_title),
                icon = painterResource(R.drawable.trackpad_input_2),
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
                title = stringResource(R.string.mousepad_triple_tap_settings_title),
                icon = painterResource(R.drawable.trackpad_input_3),
                value = uiState.tripleTap,
                values = tapPairs,
                onValueChanged = onTripleTapChanged
            )
            DialogItemSelectPreference(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                title = stringResource(R.string.drag_drop),
                icon = painterResource(R.drawable.swipe_down),
                values = listOf(true to stringResource(R.string.double_tap_to_drag), false to stringResource(R.string.hold_to_drag)),
                value = uiState.doubleTapDragEnabled,
                onValueChanged = onDoubleTapDragEnabledChanged
            )
        }
        CategoryTitleTextSmall(stringResource(R.string.sensitivity))
        SliderPreference(
            title = stringResource(R.string.speed),
            icon = painterResource(R.drawable.touch_double),
            value = uiState.sensitivity.toLong(),
            values = (1..10).map { it.toLong() to it.toString() },
            onValueChanged = onSensitivityChanged
        )
        SliderPreference (
            title = stringResource(R.string.acceleration),
            icon = painterResource(R.drawable.touch_triple),
            value = uiState.acceleration.toLong(),
            values = (0..10).map { it.toLong() to it.toString() },
            onValueChanged = onAccelerationProfileChanged
        )
        SliderPreference(
            title = stringResource(R.string.scroll),
            icon = painterResource(R.drawable.swipe_down),
            value = uiState.scrollSensitivity,
            values = sensitivitySteps,
            onValueChanged = onScrollSensitivityChanged
        )
        SliderPreference(
            title = stringResource(R.string.gyro),
            icon = painterResource(R.drawable.missing_controller),
            value = uiState.gyroSensitivity,
            values = sensitivitySteps,
            onValueChanged = onGyroSensitivityChanged
        )

        CategoryTitleTextSmall(stringResource(R.string.scrolling))
        SwitchPreference(
            title = stringResource(R.string.mousepad_scroll_direction_title),
            icon = painterResource(R.drawable.swap_calls),
            value = uiState.scrollDirection,
            onValueChanged = onScrollDirectionChanged
        )

        CategoryTitleTextSmall(stringResource(R.string.keystroke_capture))
        SwitchPreference(
            title = stringResource(R.string.sendkeystrokes_pref_enabled),
            summary = stringResource(R.string.sendkeystrokes_pref_enabled_summary),
            icon = painterResource(R.drawable.backlight_high),
            value = uiState.sendKeystrokesEnabled,
            onValueChanged = onSendKeystrokesEnabledChanged
        )
        SwitchPreference(
            title = stringResource(R.string.sendkeystrokes_safe_text_enabled),
            summary = stringResource(R.string.sendkeystrokes_safe_text_enabled_summary),
            icon = painterResource(R.drawable.arrow_upload_ready),
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
