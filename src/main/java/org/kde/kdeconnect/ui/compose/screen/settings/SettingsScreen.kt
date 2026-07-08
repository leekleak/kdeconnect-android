package org.kde.kdeconnect.ui.compose.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.helpers.CreateFileParams
import org.kde.kdeconnect.helpers.CreateFileResultContract
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.sftp.SftpSettingsScreen
import org.kde.kdeconnect.ui.CustomDevicesActivity
import org.kde.kdeconnect.ui.PermissionsAlertDialogFragment
import org.kde.kdeconnect.ui.TrustedNetworksActivity
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.DialogItemSelectPreference
import org.kde.kdeconnect.ui.compose.components.DialogTextPreference
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.NavigatePreference
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.DigitizerPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.FindMyPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.MprisPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RemoteKeyboardPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RunCommandPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SMSPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SharePluginSettingsKey
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

    DisposableEffect(Unit) {
        viewModel.updateAll()
        onDispose {}
    }

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
        CategoryTitleTextSmall(stringResource(R.string.app))
        DialogTextPreference(
            title = stringResource(R.string.settings_rename),
            value = uiState.deviceName,
            filterInput = {
                DeviceHelper.filterInvalidCharactersFromDeviceName(it)
                    .take(DeviceHelper.MAX_DEVICE_NAME_LENGTH)
            },
            onValueChanged = {
                viewModel.setDeviceName(it)
            }
        )

        val themeEntries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            stringArrayResource(R.array.theme_list_v28)
        } else {
            stringArrayResource(R.array.theme_list)
        }
        val themeValues = stringArrayResource(R.array.theme_list_values)
        val themeOptions = themeValues.zip(themeEntries)

        DialogItemSelectPreference(
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

        CategoryTitleTextSmall(stringResource(R.string.advanced))

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

        SectionHeader(title = stringResource(R.string.plugins))


        NavigatePreference(
            title = "Drawing tablet",
            onClick = { navigator.goTo(DigitizerPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Find My",
            onClick = { navigator.goTo(FindMyPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Mouse pad settings",
            onClick = { navigator.goTo(MousePadPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Multimedia Controls",
            onClick = { navigator.goTo(MprisPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Filesystem settings",
            onClick = { navigator.goTo(SftpPluginSettingsKey) }
        )
        NavigatePreference(
            title = "SMS Settings",
            onClick = { navigator.goTo(SMSPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Contact settings",
            onClick = { navigator.goTo(TelephonyPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Share settings",
            onClick = { navigator.goTo(SharePluginSettingsKey) }
        )
        NavigatePreference(
            title = "Presenter settings",
            onClick = { navigator.goTo(PresenterPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Remote keyboard settings",
            onClick = { navigator.goTo(RemoteKeyboardPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Run command settings",
            onClick = { navigator.goTo(RunCommandPluginSettingsKey) }
        )
        NavigatePreference(
            title = "Notification Sync",
            onClick = { navigator.goTo(NotificationPluginSettingsKey) }
        )

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
