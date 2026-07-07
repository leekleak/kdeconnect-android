package org.kde.kdeconnect.plugins.mpris

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MprisSettingsScreen(
    viewModel: MprisSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val timeEntries = stringArrayResource(R.array.mpris_time_entries)
    val timeValues = stringArrayResource(R.array.mpris_time_entries_values)
    val timeOptions = timeValues.zip(timeEntries)

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_mpris)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DialogItemSelectPreference(
                title = stringResource(R.string.mpris_time_settings_title),
                summary = stringResource(R.string.mpris_time_settings_summary),
                value = uiState.seekTime,
                values = timeOptions,
                onValueChanged = viewModel::setSeekTime
            )

            SwitchPreference(
                title = stringResource(R.string.mpris_notification_settings_title),
                summary = stringResource(R.string.mpris_notification_settings_summary),
                value = uiState.notificationEnabled,
                onValueChanged = viewModel::setNotificationEnabled
            )

            SwitchPreference(
                title = stringResource(R.string.mpris_keepwatching_settings_title),
                summary = stringResource(R.string.mpris_keepwatching_settings_summary),
                value = uiState.keepWatchingEnabled,
                onValueChanged = viewModel::setKeepWatchingEnabled
            )
        }
    }
}
