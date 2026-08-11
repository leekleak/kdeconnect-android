/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.PairState
import org.kde.kdeconnect.ui.compose.components.DeviceCard
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.PairingExplanations
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectScreen(
    devices: List<DeviceState>,
    pageTitle: String,
    actionIcon: Painter,
    actionDescription: String,
    isRefreshing: Boolean,
    wifiAvailable: Boolean,
    trustedNetwork: Boolean,
    onDeviceClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    HazeScaffold(
        title = pageTitle,
        scrollState = null,
        backButton = false
    ) { paddingValues ->
        DeviceSelectScreenContent(
            paddingValues = paddingValues,
            actionIcon = actionIcon,
            actionDescription = actionDescription,
            isRefreshing = isRefreshing,
            devices = devices,
            onDeviceClick = onDeviceClick,
            onRefresh = onRefresh,
            wifiAvailable = wifiAvailable,
            trustedNetwork = trustedNetwork
        )
    }
}

@Composable
private fun DeviceSelectScreenContent(
    paddingValues: PaddingValues,
    actionIcon: Painter,
    actionDescription: String,
    isRefreshing: Boolean,
    devices: List<DeviceState>,
    wifiAvailable: Boolean,
    trustedNetwork: Boolean,
    onDeviceClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    val state = rememberLazyListState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                maxDistance = PullToRefreshDefaults.IndicatorMaxDistance + paddingValues.calculateTopPadding()
            )
        }
    ) {
        if (devices.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = state
            ) {
                items(
                    items = devices,
                    key = { device -> device.deviceInfo.id }
                ) { device ->
                    DeviceCard(
                        device = device,
                        actionIcon = actionIcon,
                        actionDescription = actionDescription,
                        actionDescriptionVisible = true,
                        onClick = { onDeviceClick(device.deviceInfo.id) }
                    )
                }
            }
        } else {
            PairingExplanations(wifiAvailable = wifiAvailable, trustedNetwork = trustedNetwork)
        }

    }
}

@KdeThemePreviews
@Composable
private fun ShareScreenPreview() {
    DeviceSelectScreenContent(
        paddingValues = PaddingValues(),
        actionIcon = painterResource(R.drawable.share),
        actionDescription = stringResource(R.string.share),
        isRefreshing = false,
        devices = listOf(
            DeviceState(
                deviceInfo = DeviceInfo(
                    id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd23_",
                    certificate = ByteArray(0),
                    name = "Device 1",
                    type = DeviceType.LAPTOP
                ),
                pairState = PairState.Paired,
            ),
            DeviceState(
                deviceInfo = DeviceInfo(
                    id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd24_",
                    certificate = ByteArray(0),
                    name = "Device 2",
                    type = DeviceType.PHONE
                ),
                pairState = PairState.Paired,
            ),
        ),
        onDeviceClick = { /* Do nothing */ },
        onRefresh = { /* Do nothing */ },
        wifiAvailable = true,
        trustedNetwork = true
    )
}
