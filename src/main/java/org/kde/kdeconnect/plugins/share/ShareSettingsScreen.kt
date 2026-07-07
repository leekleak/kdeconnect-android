package org.kde.kdeconnect.plugins.share

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ShareSettingsScreen(
    viewModel: ShareSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.saveStorageLocation(uri)
        }
    }

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_sharereceiver)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SwitchPreference(
                title = stringResource(R.string.share_destination_customize),
                summary = if (uiState.customDestinationEnabled) {
                    stringResource(R.string.share_destination_customize_summary_enabled)
                } else {
                    stringResource(R.string.share_destination_customize_summary_disabled)
                },
                value = uiState.customDestinationEnabled,
                onValueChanged = viewModel::setCustomDestinationEnabled
            )

            Preference(
                title = stringResource(R.string.share_destination_folder_preference),
                summary = uiState.destinationFolderSummary,
                enabled = uiState.customDestinationEnabled,
                onClick = {
                    launcher.launch(null)
                }
            )

            SwitchPreference(
                title = stringResource(R.string.share_notification_preference),
                summary = stringResource(R.string.share_notification_preference_summary),
                value = uiState.notificationEnabled,
                onValueChanged = viewModel::setNotificationEnabled
            )
        }
    }
}
