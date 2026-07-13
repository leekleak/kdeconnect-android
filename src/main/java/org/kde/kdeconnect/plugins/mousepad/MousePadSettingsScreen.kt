package org.kde.kdeconnect.plugins.mousepad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.components.SliderPreference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MousePadSettingsScreen(
    viewModel: MousePadSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MousePadSettingsScreenContent(
        uiState = uiState,
        onSingleTapChanged = viewModel::setSingleTap,
        onDoubleTapChanged = viewModel::setDoubleTap,
        onTripleTapChanged = viewModel::setTripleTap,
        onSensitivityChanged = viewModel::setSensitivity,
        onAccelerationProfileChanged = viewModel::setAccelerationProfile,
        onScrollDirectionChanged = viewModel::setScrollDirection,
        onScrollSensitivityChanged = viewModel::setScrollSensitivity,
        onGyroSensitivityChanged = viewModel::setGyroSensitivity,
        onMouseButtonsEnabledChanged = viewModel::setMouseButtonsEnabled,
        onDoubleTapDragEnabledChanged = viewModel::setDoubleTapDragEnabled,
        onSendKeystrokesEnabledChanged = viewModel::setSendKeystrokesEnabled,
        onSendSafeTextImmediatelyChanged = viewModel::setSendSafeTextImmediately,
        onShowBackChanged = viewModel::setShowBack,
        onShowHomeChanged = viewModel::setShowHome,
        onHideMouseInputChanged = viewModel::setHideMouseInput
    )
}

@Composable
fun MousePadSettingsScreenContent(
    uiState: MousePadSettingsUiState,
    onSingleTapChanged: (String) -> Unit,
    onDoubleTapChanged: (String) -> Unit,
    onTripleTapChanged: (String) -> Unit,
    onSensitivityChanged: (String) -> Unit,
    onAccelerationProfileChanged: (String) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onScrollSensitivityChanged: (Long) -> Unit,
    onGyroSensitivityChanged: (Long) -> Unit,
    onMouseButtonsEnabledChanged: (Boolean) -> Unit,
    onDoubleTapDragEnabledChanged: (Boolean) -> Unit,
    onSendKeystrokesEnabledChanged: (Boolean) -> Unit,
    onSendSafeTextImmediatelyChanged: (Boolean) -> Unit,
    onShowBackChanged: (Boolean) -> Unit,
    onShowHomeChanged: (Boolean) -> Unit,
    onHideMouseInputChanged: (Boolean) -> Unit
) {
    val tapEntries = stringArrayResource(R.array.mousepad_tap_entries)
    val tapValues = stringArrayResource(R.array.mousepad_tap_values)
    val tapPairs = tapValues.zip(tapEntries)

    val sensitivityEntries = stringArrayResource(R.array.mousepad_sensitivity_entries)
    val sensitivityValues = stringArrayResource(R.array.mousepad_sensitivity_values)
    val sensitivityPairs = sensitivityValues.zip(sensitivityEntries)

    val accelEntries = stringArrayResource(R.array.mousepad_acceleration_profile_entries)
    val accelValues = stringArrayResource(R.array.mousepad_acceleration_profile_values)
    val accelPairs = accelValues.zip(accelEntries)

    val sensitivitySteps = (0L..200L step 10L).map { it to it.toString() }

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_mousepad)),
        backButton = true,
    ) {
        SectionHeader(title = stringResource(R.string.pref_plugin_bigscreen))
        SwitchPreference(
            title = stringResource(R.string.bigscreen_show_back_title),
            value = uiState.showBack,
            onValueChanged = onShowBackChanged
        )
        SwitchPreference(
            title = stringResource(R.string.bigscreen_show_home_title),
            value = uiState.showHome,
            onValueChanged = onShowHomeChanged
        )
        SwitchPreference(
            title = stringResource(R.string.bigscreen_hide_mouse_input),
            value = uiState.hideMouseInput,
            onValueChanged = onHideMouseInputChanged
        )
        SectionHeader(title = stringResource(R.string.pref_plugin_mousepad))
        DialogItemSelectPreference(
            title = stringResource(R.string.mousepad_single_tap_settings_title),
            value = uiState.singleTap,
            values = tapPairs,
            onValueChanged = onSingleTapChanged
        )
        DialogItemSelectPreference(
            title = stringResource(R.string.mousepad_double_tap_settings_title),
            value = uiState.doubleTap,
            values = tapPairs,
            onValueChanged = onDoubleTapChanged
        )
        DialogItemSelectPreference(
            title = stringResource(R.string.mousepad_triple_tap_settings_title),
            value = uiState.tripleTap,
            values = tapPairs,
            onValueChanged = onTripleTapChanged
        )
        DialogItemSelectPreference(
            title = stringResource(R.string.mousepad_sensitivity_settings_title),
            value = uiState.sensitivity,
            values = sensitivityPairs,
            onValueChanged = onSensitivityChanged
        )
        DialogItemSelectPreference(
            title = stringResource(R.string.mousepad_acceleration_profile_settings_title),
            value = uiState.accelerationProfile,
            values = accelPairs,
            onValueChanged = onAccelerationProfileChanged
        )

        SwitchPreference(
            title = stringResource(R.string.mousepad_scroll_direction_title),
            value = uiState.scrollDirection,
            onValueChanged = onScrollDirectionChanged
        )
        SliderPreference(
            title = stringResource(R.string.mousepad_scroll_sensitivity_title),
            value = uiState.scrollSensitivity,
            values = sensitivitySteps,
            onValueChanged = onScrollSensitivityChanged
        )

        SliderPreference(
            title = stringResource(R.string.gyro_mouse_sensitivity_title),
            value = uiState.gyroSensitivity,
            values = sensitivitySteps,
            onValueChanged = onGyroSensitivityChanged
        )

        SwitchPreference(
            title = stringResource(R.string.mousepad_mouse_buttons_title),
            value = uiState.mouseButtonsEnabled,
            onValueChanged = onMouseButtonsEnabledChanged
        )
        SwitchPreference(
            title = "Drag and drop behavior", // Using literal as in XML
            summary = if (uiState.doubleTapDragEnabled) stringResource(R.string.double_tap_to_drag) else stringResource(R.string.hold_to_drag),
            value = uiState.doubleTapDragEnabled,
            onValueChanged = onDoubleTapDragEnabledChanged
        )

        SectionHeader(title = stringResource(R.string.sendkeystrokes_pref_category_title))
        SwitchPreference(
            title = stringResource(R.string.sendkeystrokes_pref_enabled),
            summary = stringResource(R.string.sendkeystrokes_pref_enabled_summary),
            value = uiState.sendKeystrokesEnabled,
            onValueChanged = onSendKeystrokesEnabledChanged
        )
        SwitchPreference(
            title = stringResource(R.string.sendkeystrokes_safe_text_enabled),
            summary = stringResource(R.string.sendkeystrokes_safe_text_enabled_summary),
            value = uiState.sendSafeTextImmediately,
            onValueChanged = onSendSafeTextImmediatelyChanged
        )
    }
}
