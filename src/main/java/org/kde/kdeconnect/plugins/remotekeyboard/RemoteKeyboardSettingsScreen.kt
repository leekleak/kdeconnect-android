package org.kde.kdeconnect.plugins.remotekeyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RemoteKeyboardSettingsScreen(
    viewModel: RemoteKeyboardSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RemoteKeyboardSettingsScreenContent(
        uiState = uiState,
        onEditingOnlyChanged = viewModel::setEditingOnly
    )
}

@Composable
fun RemoteKeyboardSettingsScreenContent(
    uiState: RemoteKeyboardSettingsUiState,
    onEditingOnlyChanged: (Boolean) -> Unit
) {
    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_remotekeyboard)),
        backButton = true,
    ) {
        SwitchPreference(
            title = stringResource(R.string.remotekeyboard_editing_only_title),
            value = uiState.editingOnly,
            onValueChanged = onEditingOnlyChanged
        )
    }
}
