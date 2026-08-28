package org.kde.kdeconnect.ui.screen.settings

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.about
import org.kde.kdeconnect.generated.resources.advanced
import org.kde.kdeconnect.generated.resources.app
import org.kde.kdeconnect.generated.resources.bluetooth
import org.kde.kdeconnect.generated.resources.bluetooth_permission_request
import org.kde.kdeconnect.generated.resources.calls_messages
import org.kde.kdeconnect.generated.resources.colors
import org.kde.kdeconnect.generated.resources.connections
import org.kde.kdeconnect.generated.resources.devices
import org.kde.kdeconnect.generated.resources.enable_bluetooth
import org.kde.kdeconnect.generated.resources.export_notes
import org.kde.kdeconnect.generated.resources.file_export
import org.kde.kdeconnect.generated.resources.filesystem
import org.kde.kdeconnect.generated.resources.folder_managed
import org.kde.kdeconnect.generated.resources.grant
import org.kde.kdeconnect.generated.resources.id_card
import org.kde.kdeconnect.generated.resources.info
import org.kde.kdeconnect.generated.resources.notifications
import org.kde.kdeconnect.generated.resources.notifications_media
import org.kde.kdeconnect.generated.resources.other
import org.kde.kdeconnect.generated.resources.perm_phone_msg
import org.kde.kdeconnect.generated.resources.replay
import org.kde.kdeconnect.generated.resources.reset
import org.kde.kdeconnect.generated.resources.responsive_layout
import org.kde.kdeconnect.generated.resources.saved_devices
import org.kde.kdeconnect.generated.resources.settings
import org.kde.kdeconnect.generated.resources.settings_export_logs
import org.kde.kdeconnect.generated.resources.settings_export_logs_text
import org.kde.kdeconnect.generated.resources.settings_rename
import org.kde.kdeconnect.generated.resources.share_destination_folder_preference
import org.kde.kdeconnect.generated.resources.theme_dialog_title
import org.kde.kdeconnect.generated.resources.theme_list
import org.kde.kdeconnect.helpers.CreateFileParams
import org.kde.kdeconnect.helpers.CreateFileResultContract
import org.kde.kdeconnect.helpers.MAX_DEVICE_NAME_LENGTH
import org.kde.kdeconnect.helpers.filterInvalidCharactersFromDeviceName
import org.kde.kdeconnect.plugins.sftp.SimpleSftpServer
import org.kde.kdeconnect.ui.AppTheme
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionRequest
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.components.DialogTextPreference
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.IconPreference
import org.kde.kdeconnect.ui.components.KdeThemePreviews
import org.kde.kdeconnect.ui.components.NavigatePreference
import org.kde.kdeconnect.ui.components.Preference
import org.kde.kdeconnect.ui.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.ConnectionsSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationSettingsKey
import org.kde.kdeconnect.ui.navigation.SavedDevicesKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    exportLogs: (Context, Uri) -> Unit,
    setBluetoothEnabled: (Boolean) -> Unit,
    setDeviceName: (String) -> Unit,
    setTheme: (AppTheme) -> Unit,
    saveStorageLocation: (Context, Uri) -> Unit,
    resetStorageLocation: () -> Unit,
    getDisplayPath: (Context, Uri) -> String,
    navigator: Navigator
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLogsLauncher = rememberLauncherForActivityResult(
        contract = CreateFileResultContract()
    ) { uri ->
        uri?.let { exportLogs(context, it) }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setBluetoothEnabled(result.resultCode == RESULT_OK)
    }

    HazeScaffold(
        title = stringResource(Res.string.settings),
        backAction = BackAction.Normal(navigator),
    ) {
        CategoryTitleTextSmall(stringResource(Res.string.app))
        DialogTextPreference(
            title = stringResource(Res.string.settings_rename),
            icon = painterResource(Res.drawable.id_card),
            value = uiState.deviceName,
            filterInput = {
                filterInvalidCharactersFromDeviceName(it).take(MAX_DEVICE_NAME_LENGTH)
            },
            onValueChanged = {
                setDeviceName(it)
            }
        )

        val themeEntries = stringArrayResource(Res.array.theme_list)
        val themeOptions = AppTheme.entries.zip(themeEntries)

        DialogItemSelectPreference(
            title = stringResource(Res.string.theme_dialog_title),
            icon = painterResource(Res.drawable.colors),
            value = uiState.theme,
            values = themeOptions.toList(),
            onValueChanged = { setTheme(it) }
        )

        NavigatePreference(
            title = stringResource(Res.string.saved_devices),
            icon = painterResource(Res.drawable.devices),
            onClick = { navigator.goTo(SavedDevicesKey) }
        )

        CategoryTitleTextSmall(stringResource(Res.string.advanced))
        NavigatePreference(
            title = stringResource(Res.string.connections),
            icon = painterResource(Res.drawable.responsive_layout),
            onClick = { navigator.goTo(ConnectionsSettingsKey) }
        )
        NavigatePreference(
            title = stringResource(Res.string.calls_messages),
            icon = painterResource(Res.drawable.perm_phone_msg),
            onClick = { navigator.goTo(TelephonyPluginSettingsKey) }
        )
        NavigatePreference(
            title = stringResource(Res.string.notifications_media),
            icon = painterResource(Res.drawable.notifications),
            onClick = { navigator.goTo(NotificationSettingsKey) }
        )
        if (!SimpleSftpServer.SUPPORTS_NATIVEFS) {
            NavigatePreference(
                title = stringResource(Res.string.filesystem),
                icon = painterResource(Res.drawable.folder_managed),
                onClick = { navigator.goTo(SftpPluginSettingsKey) }
            )
        }

        SwitchPreference(
            title = stringResource(Res.string.enable_bluetooth),
            icon = painterResource(Res.drawable.bluetooth),
            value = uiState.bluetoothEnabled,
            onValueChanged = { newValue ->
                scope.launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && newValue) {
                        val missingPermissionRequests =
                            arrayOf(BLUETOOTH_CONNECT, BLUETOOTH_SCAN).filter {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    it
                                ) != PackageManager.PERMISSION_GRANTED
                            }.map { permission ->
                                PermissionRequest(
                                    title = getString(Res.string.enable_bluetooth),
                                    description = getString(Res.string.bluetooth_permission_request),
                                    intentAction = permission,
                                    positiveButton = getString(Res.string.grant)
                                )
                            }

                        if (missingPermissionRequests.isNotEmpty()) {
                            bluetoothPermissionLauncher.launch(
                                Intent(
                                    context,
                                    PermissionExplanationActivity::class.java
                                ).apply {
                                    // Take 1 because I think you only need to ask for one of them and you get all? Todo: Test that
                                    putExtra(
                                        "permissionRequests",
                                        Json.encodeToString(missingPermissionRequests.take(1))
                                    )
                                })
                            return@launch
                        }
                    }
                    setBluetoothEnabled(newValue)
                }
            }
        )

        val destinationSelector = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { saveStorageLocation(context, it) }
        }

        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Preference(
                modifier = Modifier.weight(1f),
                title = stringResource(Res.string.share_destination_folder_preference),
                summary = uiState.fileDestination?.let { getDisplayPath(context, it) },
                icon = painterResource(Res.drawable.file_export),
                onClick = {
                    destinationSelector.launch(null)
                }
            )
            IconPreference(
                title = stringResource(Res.string.reset),
                painter = painterResource(Res.drawable.replay),
                enabled = !uiState.fileDestinationIsDefault
            ) {
                resetStorageLocation()
            }
        }

        CategoryTitleTextSmall(stringResource(Res.string.other))

        Preference(
            title = stringResource(Res.string.settings_export_logs),
            summary = stringResource(Res.string.settings_export_logs_text),
            icon = painterResource(Res.drawable.export_notes),
            onClick = {
                exportLogsLauncher.launch(CreateFileParams("text/plain", "kdeconnect-log.txt"))
            }
        )

        NavigatePreference(
            title = stringResource(Res.string.about),
            icon = painterResource(Res.drawable.info),
            onClick = { navigator.goTo(AboutKey) }
        )
    }
}

@KdeThemePreviews
@Composable
fun SettingsPreview() {
    SettingsScreen(
        uiState = SettingsUiState(),
        exportLogs = { _, _ -> },
        setBluetoothEnabled = {},
        setDeviceName = {},
        setTheme = {},
        saveStorageLocation = { _, _ -> },
        resetStorageLocation = {},
        getDisplayPath = { _, _ -> ""},
        navigator = Navigator()
    )
}
