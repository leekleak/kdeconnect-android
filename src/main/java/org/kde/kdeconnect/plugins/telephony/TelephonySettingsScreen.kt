package org.kde.kdeconnect.plugins.telephony

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.DialogTextPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TelephonySettingsScreen(
    viewModel: TelephonySettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_telephony)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            DialogTextPreference(
                title = stringResource(R.string.telephony_pref_blocked_title),
                summary = stringResource(R.string.telephony_pref_blocked_dialog_desc),
                value = uiState.blockedNumbers,
                singleLine = false,
                onValueChanged = viewModel::setBlockedNumbers
            )
        }
    }
}
