package org.kde.kdeconnect.ui.screen.settings.advanced.calls_and_messages

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.R
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.add
import org.kde.kdeconnect.generated.resources.calls_messages
import org.kde.kdeconnect.generated.resources.close
import org.kde.kdeconnect.generated.resources.convert_to_mms_after_entries
import org.kde.kdeconnect.generated.resources.convert_to_mms_after_title
import org.kde.kdeconnect.generated.resources.convert_to_text
import org.kde.kdeconnect.generated.resources.enter_number
import org.kde.kdeconnect.generated.resources.find_my
import org.kde.kdeconnect.generated.resources.findmyphone_camera_explanation
import org.kde.kdeconnect.generated.resources.findmyphone_preference_title_flashlight
import org.kde.kdeconnect.generated.resources.groups
import org.kde.kdeconnect.generated.resources.highlight
import org.kde.kdeconnect.generated.resources.mms
import org.kde.kdeconnect.generated.resources.notification_sound
import org.kde.kdeconnect.generated.resources.receipt_long
import org.kde.kdeconnect.generated.resources.select_ringtone
import org.kde.kdeconnect.generated.resources.set_group_message_as_mms_title
import org.kde.kdeconnect.generated.resources.set_long_text_as_mms_title
import org.kde.kdeconnect.generated.resources.telephony_pref_blocked_title
import org.kde.kdeconnect.generated.resources.unblock_number
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.Preference
import org.kde.kdeconnect.ui.components.SettingsSearchBar
import org.kde.kdeconnect.ui.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun TelephonySettingsScreen(
    viewModel: TelephonySettingsViewModel = koinViewModel(),
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(Res.string.calls_messages),
        backAction = BackAction.Normal(navigator),
    ) {
        CategoryTitleTextSmall(stringResource(Res.string.telephony_pref_blocked_title))
        BlockedNumberComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(Res.string.mms))
        MMSComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(Res.string.find_my))
        FindMyComponent(viewModel, uiState)
    }
}

@Composable
private fun FindMyComponent(
    viewModel: TelephonySettingsViewModel,
    uiState: TelephonySettingsUiState
) {
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val uri = IntentCompat.getParcelableExtra(
                    data,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
                uri?.let { viewModel.setRingtone(it.toString()) }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setFlashlightEnabled(true)
        }
    }
    Preference(
        title = stringResource(Res.string.select_ringtone),
        icon = painterResource(Res.drawable.notification_sound),
        summary = viewModel.getRingtoneTitle(LocalContext.current),
        onClick = {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    Settings.System.DEFAULT_NOTIFICATION_URI
                )

                val existingUri = if (uiState.ringtoneUri.isNotEmpty()) {
                    uiState.ringtoneUri.toUri()
                } else {
                    Settings.System.DEFAULT_RINGTONE_URI
                }
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
            }
            ringtonePickerLauncher.launch(intent)
        }
    )

    SwitchPreference(
        title = stringResource(Res.string.findmyphone_preference_title_flashlight),
        summary = stringResource(Res.string.findmyphone_camera_explanation),
        icon = painterResource(Res.drawable.highlight),
        value = uiState.flashlightEnabled,
        onValueChanged = { enabled ->
            if (enabled) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                viewModel.setFlashlightEnabled(false)
            }
        }
    )
}

@Composable
private fun MMSComponent(
    viewModel: TelephonySettingsViewModel,
    uiState: TelephonySettingsUiState,
) {
    val convertToMmsAfterEntries = stringArrayResource(Res.array.convert_to_mms_after_entries)
    val convertToMmsAfterValues = integerArrayResource(R.array.convert_to_mms_after_values)
    val convertToMmsAfterOptions = convertToMmsAfterValues.zip(convertToMmsAfterEntries)

    SwitchPreference(
        title = stringResource(Res.string.set_group_message_as_mms_title),
        icon = painterResource(Res.drawable.groups),
        value = uiState.groupMessageAsMms,
        onValueChanged = viewModel::setGroupMessageAsMms
    )

    SwitchPreference(
        title = stringResource(Res.string.set_long_text_as_mms_title),
        icon = painterResource(Res.drawable.receipt_long),
        value = uiState.longTextAsMms,
        onValueChanged = viewModel::setLongTextAsMms
    )

    DialogItemSelectPreference(
        title = stringResource(Res.string.convert_to_mms_after_title),
        icon = painterResource(Res.drawable.convert_to_text),
        value = uiState.convertToMmsAfter,
        values = convertToMmsAfterOptions,
        onValueChanged = { viewModel.setConvertToMmsAfter(it) }
    )
}

@Composable
private fun BlockedNumberComponent(
    viewModel: TelephonySettingsViewModel,
    uiState: TelephonySettingsUiState
) {
    val textFieldState = rememberTextFieldState()
    SettingsSearchBar(
        state = textFieldState,
        placeholder = stringResource(Res.string.enter_number),
        actionButton = {
            IconButton(
                onClick = {
                    viewModel.blockNumber(textFieldState.text.toString())
                    textFieldState.clearText()
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.add),
                    contentDescription = stringResource(Res.string.add)
                )
            }
        }
    )
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
                        painter = painterResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.unblock_number),
                        modifier = Modifier.size(InputChipDefaults.AvatarSize)
                    )
                },
            )
        }
    }
}
