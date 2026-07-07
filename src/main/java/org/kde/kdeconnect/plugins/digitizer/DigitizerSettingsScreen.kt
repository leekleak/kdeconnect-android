package org.kde.kdeconnect.plugins.digitizer

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
fun DigitizerSettingsScreen(
    viewModel: DigitizerSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val sideEntries = stringArrayResource(R.array.digitizer_preference_entries_draw_button_side)
    val sideValues = stringArrayResource(R.array.digitizer_preference_values_draw_button_side)
    val sidePairs = sideValues.zip(sideEntries)

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_digitizer)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SwitchPreference(
                title = stringResource(R.string.digitizer_preference_title_hide_draw_button),
                summary = stringResource(R.string.digitizer_preference_summary_hide_draw_button),
                value = uiState.hideDrawButton,
                onValueChanged = viewModel::setHideDrawButton
            )

            DialogItemSelectPreference(
                title = stringResource(R.string.digitizer_preference_title_draw_button_side),
                value = uiState.drawButtonSide,
                values = sidePairs,
                onValueChanged = viewModel::setDrawButtonSide
            )
        }
    }
}
