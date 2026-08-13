package org.kde.kdeconnect.ui.screen.settings.advanced.paired

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.DeviceCard
import org.kde.kdeconnect.ui.components.FancyDialog
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.PairingExplanations
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SavedDevices(
    viewModel: SavedDevicesViewModel = koinViewModel(),
    navigator: Navigator,
) {
    val uiState by viewModel.pairingUiState.collectAsStateWithLifecycle()
    val deviceToUnpair by viewModel.deviceToUnpair

    HazeScaffold(
        title = stringResource(R.string.saved_devices),
        scrollState = null,
        backAction = BackAction.Normal(navigator),
    ) { padding ->
        if (uiState.saved.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.saved, { it.deviceInfo.id }) { device ->
                    Spacer(Modifier.height(4.dp))
                    DeviceCard(
                        device = device,
                        actionIcon = painterResource(R.drawable.link_off),
                        actionDescription = stringResource(R.string.device_menu_unpair),
                        actionDescriptionVisible = true,
                        onClick = { viewModel.queueUnpair(device) }
                    )
                }
            }
        } else {
            PairingExplanations(
                wifiAvailable = true,
                trustedNetwork = true
            )
        }
    }


    if (deviceToUnpair != null) {
        FancyDialog(
            title = stringResource(R.string.device_menu_unpair),
            icon = painterResource(R.drawable.link_off),
            content = {
                Text(
                    text = stringResource(R.string.are_you_sure_you_want_to_unpair_this_device),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            actionButton = {
                TextButton(onClick = {
                    deviceToUnpair?.let { viewModel.unpair(it) }
                }
                ) {
                    Text(stringResource(R.string.device_menu_unpair))
                }
            },
            onDismissRequest = { viewModel.queueUnpair(null) }
        )
    }
}