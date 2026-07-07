package org.kde.kdeconnect.plugins.sms

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
fun SmsSettingsScreen(
    viewModel: SmsSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val convertToMmsAfterEntries = stringArrayResource(R.array.convert_to_mms_after_entries)
    val convertToMmsAfterValues = stringArrayResource(R.array.convert_to_mms_after_values)
    val convertToMmsAfterOptions = convertToMmsAfterValues.zip(convertToMmsAfterEntries)

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.pref_plugin_telepathy)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SwitchPreference(
                title = stringResource(R.string.set_group_message_as_mms_title),
                value = uiState.groupMessageAsMms,
                onValueChanged = viewModel::setGroupMessageAsMms
            )

            SwitchPreference(
                title = stringResource(R.string.set_long_text_as_mms_title),
                value = uiState.longTextAsMms,
                onValueChanged = viewModel::setLongTextAsMms
            )

            DialogItemSelectPreference(
                title = stringResource(R.string.convert_to_mms_after_title),
                value = uiState.convertToMmsAfter,
                values = convertToMmsAfterOptions,
                onValueChanged = viewModel::setConvertToMmsAfter
            )
        }
    }
}
