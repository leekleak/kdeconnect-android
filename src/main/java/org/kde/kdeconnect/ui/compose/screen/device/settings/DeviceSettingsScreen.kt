package org.kde.kdeconnect.ui.compose.screen.device.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.kde.kdeconnect.plugins.clipboard.ClipboardPluginInfo
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityReportPluginInfo
import org.kde.kdeconnect.plugins.contacts.ContactsPluginInfo
import org.kde.kdeconnect.plugins.notifications.NotificationsPluginInfo
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPluginInfo
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.compose.components.BackAction
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.NavigatePreference
import org.kde.kdeconnect.ui.compose.components.NotificationTogglePreference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


@Composable
fun DeviceSettingsScreen(
    deviceId: String,
    viewModel: DeviceSettingsViewModel = koinViewModel(key = "DeviceSettingsViewModel_$deviceId") { parametersOf(deviceId) },
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onPermissionResult(result.resultCode)
    }

    fun onPluginToggled(pluginKey: String, isEnabled: Boolean) {
        scope.launch {
            if (!viewModel.setPluginEnabled(pluginKey, isEnabled, context)) {
                val intent = Intent(context, PermissionExplanationActivity::class.java).apply {
                    putExtra("pluginKey", pluginKey)
                }
                launcher.launch(intent)
            }
        }
    }

    val notificationSend = uiState.plugins[NotificationsPluginInfo.pluginKey]
    val notificationReceive = uiState.plugins[ReceiveNotificationsPluginInfo.pluginKey]
    val contacts = uiState.plugins[ContactsPluginInfo.pluginKey]
    val clipboard = uiState.plugins[ClipboardPluginInfo.pluginKey]
    val connectivity = uiState.plugins[ConnectivityReportPluginInfo.pluginKey]


    HazeScaffold(
        title = uiState.deviceName,
        backAction = BackAction.Normal(navigator),
        actions = {
            IconButton (viewModel::unpair) { // Todo: Confirmation dialog would be nice
                Icon(
                    painter = painterResource(R.drawable.link_off),
                    contentDescription = stringResource(R.string.device_menu_unpair),
                )
            }
        }
    ) {
        CategoryTitleTextSmall(stringResource(R.string.notifications))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NotificationTogglePreference(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.send),
                icon = painterResource(R.drawable.arrow_upward),
                value = notificationSend == true,
                onValueChanged = { onPluginToggled(NotificationsPluginInfo.pluginKey, it) }
            )
            NotificationTogglePreference(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.receive),
                icon = painterResource(R.drawable.arrow_downward),
                value = notificationReceive == true,
                onValueChanged = { onPluginToggled(ReceiveNotificationsPluginInfo.pluginKey, it) }
            )
        }
        CategoryTitleTextSmall(stringResource(R.string.synchronization))
        SwitchPreference(
            title = stringResource(ClipboardPluginInfo.displayNameRes),
            summary = stringResource(ClipboardPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.assignment),
            value = clipboard == true,
            onValueChanged = { onPluginToggled(ClipboardPluginInfo.pluginKey, it) }
        )
        SwitchPreference(
            title = stringResource(ContactsPluginInfo.displayNameRes),
            summary = stringResource(ContactsPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.contacts),
            value = contacts == true,
            onValueChanged = { onPluginToggled(ContactsPluginInfo.pluginKey, it) }
        )
        SwitchPreference(
            title = stringResource(ConnectivityReportPluginInfo.displayNameRes),
            summary = stringResource(ConnectivityReportPluginInfo.descriptionRes),
            icon = painterResource(R.drawable.query_stats),
            value = connectivity == true,
            onValueChanged = { onPluginToggled(ConnectivityReportPluginInfo.pluginKey, it) }
        )

        CategoryTitleTextSmall(stringResource(R.string.global_settings))
        NavigatePreference(
            title = stringResource(R.string.calls_messages),
            icon = painterResource(R.drawable.perm_phone_msg),
            onClick = { navigator.goTo(TelephonyPluginSettingsKey) }
        )
        NavigatePreference(
            title = stringResource(R.string.notifications_media),
            icon = painterResource(R.drawable.notifications),
            onClick = { navigator.goTo(NotificationSettingsKey) }
        )
    }
}