package org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.compose.components.card
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectionsSettingsScreen(
    viewModel: ConnectionsSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setAllNetworksAllowed(false)
        }
        viewModel.updateUiState()
    }

    HazeScaffold(
        title = stringResource(R.string.connections),
        backButton = true,
    ) {
        SwitchPreference(
            title = stringResource(R.string.network_whitelist),
            summary = stringResource(R.string.network_whitelist_summary),
            value = uiState.allNetworksAllowed,
            onValueChanged = { newValue ->
                if (!newValue && !uiState.hasLocationPermission) {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    viewModel.setAllNetworksAllowed(newValue)
                }
            }
        )

        AnimatedVisibility(uiState.allNetworksAllowed) {
            val textFieldState = rememberTextFieldState()
            var isFocused by remember { mutableStateOf(false) }

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .card()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                            .onFocusChanged { isFocused = it.isFocused },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        state = textFieldState,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        decorator = { innerTextField ->
                            Box {
                                if (textFieldState.text.isEmpty() && !isFocused) {
                                    Text(
                                        text = stringResource(R.string.current_ssid),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    val currentSSID = uiState.currentSSID
                    AnimatedContent(currentSSID != null && currentSSID !in uiState.trustedNetworks) {
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
                }
                if (uiState.trustedNetworks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_trusted_networks_list_text),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
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
    }
}
