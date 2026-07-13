package org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SettingsSearchBar
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectionsSettingsScreen(
    viewModel: ConnectionsSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(R.string.connections),
        backButton = true,
    ) {
        CategoryTitleTextSmall(stringResource(R.string.networks))
        WhitelistComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(R.string.manually_added_devices))
        ManualDeviceComponent(viewModel, uiState)
    }
}

@Composable
private fun ManualDeviceComponent(
    viewModel: ConnectionsSettingsViewModel,
    uiState: ConnectionsSettingsUiState
) {
    val textFieldState = rememberTextFieldState()
    val context = LocalContext.current
    SettingsSearchBar(
        state = textFieldState,
        placeholder = stringResource(R.string.add_device_hint),
        actionButton = {
            IconButton(
                onClick = {
                    viewModel.addCustomDevice(textFieldState.text.toString(), context)
                    textFieldState.clearText()
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.add)
                )
            }
        },
    )
    uiState.customDevices.forEach { device ->
        CustomDeviceItem(
            device = device,
            onDelete = { viewModel.deleteCustomDevice(device, context) }
        )
    }
}

@Composable
private fun ColumnScope.WhitelistComponent(
    viewModel: ConnectionsSettingsViewModel,
    uiState: ConnectionsSettingsUiState,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setAllNetworksAllowed(false)
        }
        viewModel.updateUiState()
    }

    SwitchPreference(
        title = stringResource(R.string.network_whitelist),
        summary = stringResource(R.string.network_whitelist_summary),
        value = !uiState.allNetworksAllowed,
        onValueChanged = { newValue ->
            if (newValue && !uiState.hasLocationPermission) {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                viewModel.setAllNetworksAllowed(!newValue)
            }
        }
    )

    AnimatedVisibility(!uiState.allNetworksAllowed) {
        val textFieldState = rememberTextFieldState()

        Column {
            SettingsSearchBar(
                state = textFieldState,
                placeholder = stringResource(R.string.current_ssid),
                actionButton = {
                    val currentSSID = uiState.currentSSID
                    AnimatedContent(currentSSID != null && currentSSID !in uiState.trustedNetworks && textFieldState.text.isEmpty()) {
                        if (it && currentSSID != null) {
                            Button(
                                onClick = { viewModel.addTrustedNetwork(currentSSID) }
                            ) {
                                Text(stringResource(R.string.add_trusted_network, currentSSID))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.addTrustedNetwork(textFieldState.text.toString())
                                    textFieldState.clearText()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = stringResource(R.string.add)
                                )
                            }
                        }
                    }
                },
            )
            FlowRow(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.trustedNetworks.forEach { ssid ->
                    InputChip(
                        modifier = Modifier.height(32.dp),
                        onClick = {
                            viewModel.removeTrustedNetwork(ssid)
                        },
                        label = { Text(ssid) },
                        selected = false,
                        trailingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.remove_trusted_network),
                                modifier = Modifier.size(InputChipDefaults.AvatarSize)
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomDeviceItem(
    device: DeviceHost,
    onDelete: () -> Unit
) {
    val pingResult = device.ping
    val summary = when {
        pingResult == null -> stringResource(R.string.ping_in_progress)
        pingResult.latency != null -> stringResource(R.string.ping_result, pingResult.latency)
        else -> stringResource(R.string.ping_failed)
    }

    Preference(
        title = device.toString(),
        summary = summary,
        controls = {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    )
}