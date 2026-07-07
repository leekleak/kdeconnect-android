package org.kde.kdeconnect.plugins.runcommand

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RunCommandSettingsScreen(
    viewModel: RunCommandSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RunCommandSettingsScreenContent(
        uiState = uiState,
        onNameAsTitleChanged = viewModel::setNameAsTitle
    )
}

@Composable
fun RunCommandSettingsScreenContent(
    uiState: RunCommandSettingsUiState,
    onNameAsTitleChanged: (Boolean) -> Unit
) {
    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_runcommand)),
        backButton = true,
    ) {
        SectionHeader(
            title = stringResource(R.string.runcommand_category_device_controls_title)
        )
        SwitchPreference(
            title = stringResource(R.string.runcommand_name_as_title_title),
            summary = if (uiState.nameAsTitle) "Name -> title" else "Name -> subtitle",
            value = uiState.nameAsTitle,
            onValueChanged = onNameAsTitleChanged
        )
    }
}
