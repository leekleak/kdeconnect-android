package org.kde.kdeconnect.ui.compose.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.helpers.CreateFileParams
import org.kde.kdeconnect.helpers.CreateFileResultContract
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.sftp.SimpleSftpServer
import org.kde.kdeconnect.ui.AppTheme
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.compose.components.DialogTextPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.IconPreference
import org.kde.kdeconnect.ui.compose.components.NavigatePreference
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.ConnectionsSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationSettingsKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigator: Navigator = koinInject()

    val exportLogsLauncher = rememberLauncherForActivityResult(
        contract = CreateFileResultContract()
    ) { uri ->
        uri?.let { viewModel.exportLogs(context, it) }
    }

    HazeScaffold(
        title = stringResource(R.string.settings),
        backButton = true,
    ) {
        CategoryTitleTextSmall(stringResource(R.string.app))
        DialogTextPreference(
            title = stringResource(R.string.settings_rename),
            icon = painterResource(R.drawable.id_card),
            value = uiState.deviceName,
            filterInput = {
                DeviceHelper.filterInvalidCharactersFromDeviceName(it)
                    .take(DeviceHelper.MAX_DEVICE_NAME_LENGTH)
            },
            onValueChanged = {
                viewModel.setDeviceName(it)
            }
        )

        val themeEntries = stringArrayResource(R.array.theme_list)
        val themeOptions = AppTheme.entries.zip(themeEntries)

        DialogItemSelectPreference(
            title = stringResource(R.string.theme_dialog_title),
            icon = painterResource(R.drawable.colors),
            value = uiState.theme,
            values = themeOptions.toList(),
            onValueChanged = { viewModel.setTheme(it) }
        )

        CategoryTitleTextSmall(stringResource(R.string.advanced))
        NavigatePreference(
            title = stringResource(R.string.connections),
            icon = painterResource(R.drawable.responsive_layout),
            onClick = { navigator.goTo(ConnectionsSettingsKey) }
        )
        NavigatePreference(
            title = stringResource(R.string.calls_messages),
            icon = painterResource(R.drawable.perm_phone_msg),
            onClick = { navigator.goTo(TelephonyPluginSettingsKey) }
        )
        NavigatePreference(
            title = stringResource(R.string.notifications),
            icon = painterResource(R.drawable.notifications),
            onClick = { navigator.goTo(NotificationSettingsKey) }
        )
        if (!SimpleSftpServer.SUPPORTS_NATIVEFS) {
            NavigatePreference(
                title = stringResource(R.string.filesystem),
                icon = painterResource(R.drawable.folder_managed),
                onClick = { navigator.goTo(SftpPluginSettingsKey) }
            )
        }

        SwitchPreference(
            title = stringResource(R.string.enable_bluetooth),
            icon = painterResource(R.drawable.bluetooth),
            value = uiState.bluetoothEnabled,
            onValueChanged = { newValue ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && newValue) {
                    val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                    val permissionsGranted = permissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (!permissionsGranted) {
                        (context as? FragmentActivity)?.let {
                            PermissionsAlertDialogFragment.Builder()
                                .setTitle(R.string.location_permission_needed_title)
                                .setMessage(R.string.bluetooth_permission_needed_desc)
                                .setPermissions(permissions)
                                .setRequestCode(2)
                                .create().show(it.supportFragmentManager, null)
                        }
                        return@SwitchPreference
                    }
                }
                viewModel.setBluetoothEnabled(newValue)
            }
        )

        val destinationSelector = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { viewModel.saveStorageLocation(context, it) }
        }

        Row (
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Preference(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.share_destination_folder_preference),
                summary = uiState.fileDestination?.let { viewModel.getDisplayPath(context, it) },
                icon = painterResource(R.drawable.file_export),
                onClick = {
                    destinationSelector.launch(null)
                }
            )
            IconPreference(
                title = stringResource(R.string.reset),
                painter = painterResource(R.drawable.replay),
                enabled = !uiState.fileDestinationIsDefault
            ) {
                viewModel.resetStorageLocation()
            }
        }

        CategoryTitleTextSmall(stringResource(R.string.other))

        Preference(
            title = stringResource(R.string.settings_export_logs),
            summary = stringResource(R.string.settings_export_logs_text),
            icon = painterResource(R.drawable.export_notes),
            onClick = {
                exportLogsLauncher.launch(CreateFileParams("text/plain", "kdeconnect-log.txt"))
            }
        )

        NavigatePreference(
            title = stringResource(R.string.about),
            icon = painterResource(R.drawable.info),
            onClick = { navigator.goTo(AboutKey) }
        )
    }
}
