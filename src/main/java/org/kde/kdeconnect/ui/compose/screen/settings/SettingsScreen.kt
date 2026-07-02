package org.kde.kdeconnect.ui.compose.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.helpers.CreateFileParams
import org.kde.kdeconnect.helpers.CreateFileResultContract
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.ui.CustomDevicesActivity
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect.ui.TrustedNetworksActivity
import org.kde.kdeconnect.ui.compose.components.DialogPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.NavigatePreference
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.Navigator
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

    DisposableEffect(Unit) {
        viewModel.updateAll()
        onDispose {}
    }

    var showRenameDialog by remember { mutableStateOf(false) }

    val devicesByIpLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.updateCustomDevicesCount()
    }

    val trustedNetworksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // No-op or update something if needed
    }

    val exportLogsLauncher = rememberLauncherForActivityResult(
        contract = CreateFileResultContract()
    ) { uri ->
        uri?.let { viewModel.exportLogs(it) }
    }

    HazeScaffold(
        title = stringResource(R.string.settings),
        backButton = true,
    ) {
        Preference(
            title = stringResource(R.string.settings_rename),
            summary = uiState.deviceName,
            onClick = { showRenameDialog = true }
        )

        val themeEntries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stringArrayResource(R.array.theme_list_v28)
        } else {
            stringArrayResource(R.array.theme_list)
        }
        val themeValues = stringArrayResource(R.array.theme_list_values)
        val themeOptions = themeValues.zip(themeEntries)

        DialogPreference(
            title = stringResource(R.string.theme_dialog_title),
            value = uiState.theme,
            values = themeOptions.toList(),
            onValueChanged = { viewModel.setTheme(it) }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Preference(
                title = stringResource(R.string.setting_persistent_notification_oreo),
                summary = stringResource(R.string.setting_persistent_notification_description),
                onClick = {
                    val intent = Intent().apply {
                        action = "android.settings.APP_NOTIFICATION_SETTINGS"
                        putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
        } else {
            SwitchPreference(
                title = stringResource(R.string.setting_persistent_notification),
                value = uiState.persistentNotificationEnabled,
                onValueChanged = { viewModel.setPersistentNotificationEnabled(it) }
            )
        }

        NavigatePreference(
            title = stringResource(R.string.trusted_networks),
            summary = stringResource(R.string.trusted_networks_desc),
            onClick = {
                trustedNetworksLauncher.launch(Intent(context, TrustedNetworksActivity::class.java))
            }
        )

        NavigatePreference(
            title = stringResource(R.string.custom_device_list),
            summary = stringResource(R.string.custom_devices_settings_summary, uiState.customDevicesCount),
            onClick = {
                devicesByIpLauncher.launch(Intent(context, CustomDevicesActivity::class.java))
            }
        )

        SwitchPreference(
            title = stringResource(R.string.enable_bluetooth),
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

        Preference(
            title = stringResource(R.string.settings_export_logs),
            summary = stringResource(R.string.settings_export_logs_text),
            onClick = {
                exportLogsLauncher.launch(CreateFileParams("text/plain", "kdeconnect-log.txt"))
            }
        )

        NavigatePreference(
            title = stringResource(R.string.about),
            onClick = { navigator.goTo(AboutKey) }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(uiState.deviceName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.device_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = DeviceHelper.filterInvalidCharactersFromDeviceName(it)
                            .take(DeviceHelper.MAX_DEVICE_NAME_LENGTH)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDeviceName(newName)
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text(stringResource(R.string.device_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
