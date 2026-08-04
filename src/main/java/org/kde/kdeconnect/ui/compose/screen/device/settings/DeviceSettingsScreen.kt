package org.kde.kdeconnect.ui.compose.screen.device.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardPluginInfo
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityReportPlugin
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityReportPluginInfo
import org.kde.kdeconnect.plugins.contacts.ContactsPlugin
import org.kde.kdeconnect.plugins.contacts.ContactsPluginInfo
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPlugin
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPluginInfo
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.plugins.notifications.NotificationsPluginInfo
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPlugin
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPluginInfo
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.NotificationTogglePreference
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


@Composable
fun DeviceSettingsScreen(
    deviceId: String,
    viewModel: DeviceSettingsViewModel = koinViewModel(key = "DeviceSettingsViewModel_$deviceId") { parametersOf(deviceId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onPermissionResult(result.resultCode)
    }

    fun onPluginToggled(pluginKey: String, isEnabled: Boolean) {
        if (viewModel.setPluginEnabled(pluginKey, isEnabled)) {
            val intent = Intent(context, PermissionExplanationActivity::class.java).apply {
                putExtra("pluginKey", pluginKey)
            }
            launcher.launch(intent)
        }
    }

    val notificationSend = uiState.plugins.find { it is NotificationsPlugin }
    val notificationReceive = uiState.plugins.find { it is ReceiveNotificationsPlugin }
    val contacts = uiState.plugins.find { it is ContactsPlugin }
    val clipboard = uiState.plugins.find { it is ClipboardPlugin }
    val multimedia = uiState.plugins.find { it is MprisReceiverPlugin }
    val connectivity = uiState.plugins.find { it is ConnectivityReportPlugin }


    HazeScaffold(
        title = uiState.deviceName,
        backButton = true,
    ) {
        CategoryTitleTextSmall(stringResource(R.string.notifications))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationTogglePreference(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.send),
                icon = painterResource(R.drawable.arrow_upward),
                value = notificationSend != null,
                onValueChanged = { onPluginToggled(NotificationsPluginInfo.pluginKey, it) }
            )
            NotificationTogglePreference(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.receive),
                icon = painterResource(R.drawable.arrow_downward),
                value = notificationReceive != null,
                onValueChanged = { onPluginToggled(ReceiveNotificationsPluginInfo.pluginKey, it) }
            )
        }
        CategoryTitleTextSmall(stringResource(R.string.synchronization))
        SwitchPreference(
            title = stringResource(ClipboardPluginInfo.displayNameRes),
            summary = stringResource(ClipboardPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.assignment),
            value = clipboard != null,
            onValueChanged = { onPluginToggled(ClipboardPluginInfo.pluginKey, it) }
        )
        SwitchPreference(
            title = stringResource(ContactsPluginInfo.displayNameRes),
            summary = stringResource(ContactsPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.contacts),
            value = contacts != null,
            onValueChanged = { onPluginToggled(ContactsPluginInfo.pluginKey, it) }
        )
        SwitchPreference(
            title = stringResource(MprisReceiverPluginInfo.displayNameRes),
            summary = stringResource(MprisReceiverPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.media_link),
            value = multimedia != null,
            onValueChanged = { onPluginToggled(MprisReceiverPluginInfo.pluginKey, it) }
        )
        SwitchPreference(
            title = stringResource(ConnectivityReportPluginInfo.displayNameRes),
            summary = stringResource(ConnectivityReportPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.query_stats),
            value = connectivity != null,
            onValueChanged = { onPluginToggled(ConnectivityReportPluginInfo.pluginKey, it) }
        )

        CategoryTitleTextSmall(stringResource(R.string.other))
        Preference(
            title = stringResource(R.string.device_menu_unpair),
            icon = painterResource(R.drawable.link_off),
            onClick = viewModel::unpair
        )
    }
}