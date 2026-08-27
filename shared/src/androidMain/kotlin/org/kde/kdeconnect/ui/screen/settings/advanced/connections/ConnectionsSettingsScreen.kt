package org.kde.kdeconnect.ui.screen.settings.advanced.connections

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.DeviceHost
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.add
import org.kde.kdeconnect.generated.resources.add_device_hint
import org.kde.kdeconnect.generated.resources.add_trusted_network
import org.kde.kdeconnect.generated.resources.close
import org.kde.kdeconnect.generated.resources.connections
import org.kde.kdeconnect.generated.resources.current_ssid
import org.kde.kdeconnect.generated.resources.delete
import org.kde.kdeconnect.generated.resources.manually_added_devices
import org.kde.kdeconnect.generated.resources.network_whitelist
import org.kde.kdeconnect.generated.resources.network_whitelist_summary
import org.kde.kdeconnect.generated.resources.networks
import org.kde.kdeconnect.generated.resources.ping_failed
import org.kde.kdeconnect.generated.resources.ping_in_progress
import org.kde.kdeconnect.generated.resources.ping_result
import org.kde.kdeconnect.generated.resources.remove_trusted_network
import org.kde.kdeconnect.generated.resources.verified
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.Preference
import org.kde.kdeconnect.ui.components.SettingsSearchBar
import org.kde.kdeconnect.ui.components.SwitchPreference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectionsSettingsScreen(
    viewModel: ConnectionsSettingsViewModel = koinViewModel(),
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(Res.string.connections),
        backAction = BackAction.Normal(navigator),
    ) {
        CategoryTitleTextSmall(stringResource(Res.string.networks))
        WhitelistComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(Res.string.manually_added_devices))
        ManualDeviceComponent(viewModel, uiState)
    }
}

@Composable
private fun ManualDeviceComponent(
    viewModel: ConnectionsSettingsViewModel,
    uiState: ConnectionsSettingsUiState
) {
    val textFieldState = rememberTextFieldState()
    SettingsSearchBar(
        state = textFieldState,
        placeholder = stringResource(Res.string.add_device_hint),
        actionButton = {
            IconButton(
                onClick = {
                    viewModel.addCustomDevice(textFieldState.text.toString())
                    textFieldState.clearText()
                }
            ) {
                Icon(
                    painter = painterResource(Res.drawable.add),
                    contentDescription = stringResource(Res.string.add)
                )
            }
        },
    )
    uiState.customDevices.forEach { device ->
        CustomDeviceItem(
            device = device,
            onDelete = { viewModel.deleteCustomDevice(device) }
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
        title = stringResource(Res.string.network_whitelist),
        summary = stringResource(Res.string.network_whitelist_summary),
        icon = painterResource(Res.drawable.verified),
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
                placeholder = stringResource(Res.string.current_ssid),
                actionButton = {
                    val currentSSID = uiState.currentSSID
                    AnimatedContent(currentSSID != null && currentSSID !in uiState.trustedNetworks && textFieldState.text.isEmpty()) {
                        if (it && currentSSID != null) {
                            Button(
                                onClick = { viewModel.addTrustedNetwork(currentSSID) }
                            ) {
                                Text(stringResource(Res.string.add_trusted_network, currentSSID))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.addTrustedNetwork(textFieldState.text.toString())
                                    textFieldState.clearText()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.add),
                                    contentDescription = stringResource(Res.string.add)
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
                                painter = painterResource(Res.drawable.close),
                                contentDescription = stringResource(Res.string.remove_trusted_network),
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
        pingResult == null -> stringResource(Res.string.ping_in_progress)
        pingResult.latency != null -> stringResource(Res.string.ping_result, pingResult.latency)
        else -> stringResource(Res.string.ping_failed)
    }

    Preference(
        title = device.toString(),
        summary = summary,
        controls = {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(Res.drawable.delete),
                    contentDescription = stringResource(Res.string.delete)
                )
            }
        }
    )
}