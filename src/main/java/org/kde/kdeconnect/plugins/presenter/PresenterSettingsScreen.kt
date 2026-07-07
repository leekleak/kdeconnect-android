package org.kde.kdeconnect.plugins.presenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SliderPreference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PresenterSettingsScreen(
    viewModel: PresenterSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PresenterSettingsScreenContent(
        uiState = uiState,
        onEnableVolumeKeysChanged = viewModel::setEnableVolumeKeys,
        onSensitivityChanged = viewModel::setSensitivity
    )
}

@Composable
fun PresenterSettingsScreenContent(
    uiState: PresenterSettingsUiState,
    onEnableVolumeKeysChanged: (Boolean) -> Unit,
    onSensitivityChanged: (Long) -> Unit
) {
    val sensitivityValues = (10L..100L step 10L).map { it to it.toString() }

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_presenter)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SwitchPreference(
                title = stringResource(R.string.pref_presenter_enable_volume_keys_title),
                summary = stringResource(R.string.pref_presenter_enable_volume_keys_summary),
                value = uiState.enableVolumeKeys,
                onValueChanged = onEnableVolumeKeysChanged
            )

            SliderPreference(
                modifierLabelText = Modifier.widthIn(min = 52.dp),
                title = stringResource(R.string.pref_presenter_sensitivity_title),
                value = uiState.sensitivity,
                values = sensitivityValues,
                onValueChanged = onSensitivityChanged
            )
        }
    }
}
