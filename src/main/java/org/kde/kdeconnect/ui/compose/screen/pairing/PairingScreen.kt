/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.ui.compose.components.DeviceCard
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeBodyMediumText
import org.kde.kdeconnect.ui.compose.components.KdeBodySmallText
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val navigator: Navigator = koinInject()

    HazeScaffold(
        title = stringResource(R.string.kde_connect_short),
        scrollState = null,
        actions = {
            IconButton(
                onClick = { navigator.goTo(SettingsKey) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(id = R.string.open)
                )
            }
        }
    ) {paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Explanations
                item {
                    PairingExplanations(uiState = uiState)
                }

                // Connected devices
                item {
                    SectionHeader(stringResource(R.string.category_connected_devices))
                }
                if (uiState.connected.isEmpty()) {
                    item {
                        EmptyPlaceholder()
                    }
                } else {
                    itemsIndexed(
                        items = uiState.connected,
                        key = { _, connectedDevice -> connectedDevice.id }) { _, connectedDevice ->
                        Spacer(Modifier.height(4.dp))
                        DeviceCard (
                            device = connectedDevice,
                            onClick = { onClick(it) }
                        )
                    }
                }

                // Available devices
                if (uiState.available.isNotEmpty()) {
                    item {
                        SectionHeader(title = stringResource(id = R.string.category_not_paired_devices))
                    }
                    itemsIndexed(
                        items = uiState.available,
                        key = { _, availableDevice -> availableDevice.id }) { _, availableDevice ->
                        Spacer(Modifier.height(4.dp))
                        DeviceCard (
                            device = availableDevice,
                            onClick = { onClick(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingExplanations(uiState: PairingUiState) {
    Column {
        if (uiState.hasDuplicateNames) {
            DuplicateNamesWarning()
        }

        val someDevicesReachable = uiState.available.isNotEmpty() || uiState.connected.isNotEmpty()

        if (someDevicesReachable || uiState.isWifiAvailable) {
            if (uiState.isTrustedNetwork) {
                PairingExplanationRow(text = stringResource(R.string.pairing_description))
            } else {
                PairingExplanationRow(
                    text = stringResource(R.string.on_non_trusted_message),
                    icon = R.drawable.warning
                )
            }
        } else {
            PairingExplanationRow(
                text = stringResource(R.string.no_wifi),
                icon = R.drawable.wifi,
            )
        }
    }
}

@Composable
fun PairingExplanationRow(
    text: String, icon: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        KdeBodyMediumText(
            text = text,
        )
    }
}

@Composable
fun DuplicateNamesWarning() {
    PairingExplanationRow(
        text = stringResource(id = R.string.pairing_duplicate_names),
        icon = R.drawable.warning,
    )
}

@Composable
fun EmptyPlaceholder() {
    KdeBodySmallText(
        text = stringResource(R.string.device_list_empty),
        modifier = Modifier.padding(10.dp),
        textAlign = TextAlign.Center,
    )
}

@KdeThemePreviews
@Composable
private fun PreviewCompose() {
    PairingScreen(
        uiState = PairingUiState(
            isWifiAvailable = true,
            isTrustedNetwork = true,
            hasDuplicateNames = true,
            connected = emptyList(),
            available = listOf(
                DeviceUiModel(
                    id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd23_",
                    icon = R.drawable.laptop_windows,
                    name = "Device 1",
                    summaryRes = 0,
                    isReachable = true,
                    isPaired = false
                ), DeviceUiModel(
                    id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd24_",
                    icon = R.drawable.desktop_windows,
                    name = "Device 2",
                    summaryRes = R.string.protocol_version_newer,
                    isReachable = true,
                    isPaired = false
                )
            ),
            isRefreshing = false
        ),
        onClick = { /* Do nothing */ },
        onRefresh = { /* Do nothing */ }
    )
}
