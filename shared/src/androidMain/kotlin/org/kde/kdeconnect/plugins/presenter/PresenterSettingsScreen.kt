package org.kde.kdeconnect.plugins.presenter

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.*
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.SliderPreference
import org.kde.kdeconnect.ui.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PresenterSettingsScreen(
    viewModel: PresenterSettingsViewModel = koinViewModel(),
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PresenterSettingsScreenContent(
        uiState = uiState,
        navigator = navigator,
        onEnableVolumeKeysChanged = viewModel::setEnableVolumeKeys,
        onSensitivityChanged = viewModel::setSensitivity
    )
}

@Composable
fun PresenterSettingsScreenContent(
    uiState: PresenterSettingsUiState,
    navigator: Navigator,
    onEnableVolumeKeysChanged: (Boolean) -> Unit,
    onSensitivityChanged: (Long) -> Unit
) {
    val sensitivityValues = (10L..100L step 10L).map { it to it.toString() }

    HazeScaffold(
        title = stringResource(Res.string.plugin_settings_with_name, stringResource(Res.string.pref_plugin_presenter)),
        backAction = BackAction.Normal(navigator),
    ) { 
        SwitchPreference(
            title = stringResource(Res.string.pref_presenter_enable_volume_keys_title),
            summary = stringResource(Res.string.pref_presenter_enable_volume_keys_summary),
            icon = painterResource(Res.drawable.volume_up),
            value = uiState.enableVolumeKeys,
            onValueChanged = onEnableVolumeKeysChanged
        )

        SliderPreference(
            modifierLabelText = Modifier.widthIn(min = 52.dp),
            title = stringResource(Res.string.pref_presenter_sensitivity_title),
            icon = painterResource(Res.drawable.touch_triple),
            value = uiState.sensitivity.toLong(),
            values = sensitivityValues,
            onValueChanged = onSensitivityChanged
        )
    }
}
