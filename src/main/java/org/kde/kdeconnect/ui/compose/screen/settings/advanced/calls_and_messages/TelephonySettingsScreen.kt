package org.kde.kdeconnect.ui.compose.screen.settings.advanced.calls_and_messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.compose.components.card
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TelephonySettingsScreen(
    viewModel: TelephonySettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(R.string.calls_messages),
        backButton = true,
    ) {
        CategoryTitleTextSmall(stringResource(R.string.telephony_pref_blocked_title))
        BlockedNumberComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(R.string.mms))
        MMSComponent(viewModel, uiState)
    }
}

@Composable
private fun MMSComponent(
    viewModel: TelephonySettingsViewModel,
    uiState: TelephonySettingsUiState,
) {
    val convertToMmsAfterEntries = stringArrayResource(R.array.convert_to_mms_after_entries)
    val convertToMmsAfterValues = stringArrayResource(R.array.convert_to_mms_after_values)
    val convertToMmsAfterOptions = convertToMmsAfterValues.zip(convertToMmsAfterEntries)

    SwitchPreference(
        title = stringResource(R.string.set_group_message_as_mms_title),
        icon = painterResource(R.drawable.groups),
        value = uiState.groupMessageAsMms,
        onValueChanged = viewModel::setGroupMessageAsMms
    )

    SwitchPreference(
        title = stringResource(R.string.set_long_text_as_mms_title),
        icon = painterResource(R.drawable.receipt_long),
        value = uiState.longTextAsMms,
        onValueChanged = viewModel::setLongTextAsMms
    )

    DialogItemSelectPreference(
        title = stringResource(R.string.convert_to_mms_after_title),
        icon = painterResource(R.drawable.convert_to_text),
        value = uiState.convertToMmsAfter,
        values = convertToMmsAfterOptions,
        onValueChanged = viewModel::setConvertToMmsAfter
    )
}

@Composable
private fun BlockedNumberComponent(
    viewModel: TelephonySettingsViewModel,
    uiState: TelephonySettingsUiState
) {
    val textFieldState = rememberTextFieldState("+37063960629")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .card()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            state = textFieldState,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface)
        )
        IconButton(
            onClick = { viewModel.blockNumber(textFieldState.text.toString()) }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.add)
            )
        }
    }
    FlowRow(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.blockedNumbers.forEach { number ->
            InputChip(
                modifier = Modifier.height(32.dp),
                onClick = {
                    viewModel.unblockNumber(number)
                },
                label = { Text(number) },
                selected = false,
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.unblock_number),
                        modifier = Modifier.size(InputChipDefaults.AvatarSize)
                    )
                },
            )
        }
    }
}
