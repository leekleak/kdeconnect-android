package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.components.DeviceCard
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.PairingExplanations
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    HazeScaffold(
        title = stringResource(R.string.pair_devices),
        scrollState = null,
        backButton = false
    ) { paddingValues ->
        val pullRefreshState = rememberPullToRefreshState()
        val state = rememberLazyListState()
        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            if (uiState.available.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    state = state
                ) {
                    items(
                        items = uiState.available,
                        key = { device -> device.deviceInfo.id }
                    ) { device ->
                        val actionIcon = painterResource(
                            if (device.pairStatus == PairingHandler.PairState.Requested) R.drawable.key
                            else R.drawable.link
                        )
                        val actionDescription =
                            if (device.pairStatus == PairingHandler.PairState.Requested) device.verificationKey ?: ""
                            else stringResource(R.string.pair)

                        DeviceCard(
                            device = device.toUiModel(),
                            actionIcon = actionIcon,
                            actionDescription = actionDescription,
                            actionDescriptionVisible = true,
                            onClick = { onClick(device.deviceInfo.id) }
                        )
                    }
                }
            } else {
                PairingExplanations(wifiAvailable = uiState.wifiAvailable, trustedNetwork = uiState.trustedNetwork)
            }
        }
    }
}