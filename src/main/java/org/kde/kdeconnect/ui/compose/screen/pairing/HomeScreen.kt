package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.PairingHandler.PairState
import org.kde.kdeconnect.ui.compose.components.DeviceCard
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.PairingExplanations
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToPairingScreen: () -> Unit,
    onNavigateToSettingsScreen: () -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()

    HazeScaffold(
        title = stringResource(R.string.kde_connect_short),
        scrollState = null,
        actions = {
            IconButton(onNavigateToSettingsScreen) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(id = R.string.settings)
                )
            }
        }
    ) { paddingValues ->
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
            if (uiState.connected.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = uiState.connected,
                        key = { _, connectedDevice -> connectedDevice.deviceInfo.id }) { _, connectedDevice ->
                        Spacer(Modifier.height(4.dp))
                        DeviceCard(
                            device = connectedDevice,
                            onClick = { onClick(it) }
                        )
                    }
                }
            } else {
                PairingExplanations(wifiAvailable = uiState.wifiAvailable, trustedNetwork = uiState.trustedNetwork)
            }
            Box(modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(16.dp)) {
                MediumFloatingActionButton(
                    onClick = onNavigateToPairingScreen
                ) {
                    @Composable
                    fun icon() {
                        Icon(
                            modifier = Modifier.size(36.dp),
                            painter = painterResource(R.drawable.link),
                            contentDescription = stringResource(R.string.pair_devices)
                        )
                    }
                    if (uiState.availableCount > 0) {
                        BadgedBox({
                            Badge {
                                Text(uiState.availableCount.toString())
                            }
                        }) {
                            icon()
                        }
                    } else {
                        icon()
                    }
                }
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun PreviewCompose() {
    HomeScreen(
        uiState = HomeUiState(
            wifiAvailable = true,
            trustedNetwork = true,
            connected = listOf(
                DeviceState(
                    deviceInfo = DeviceInfo(
                        id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd23_",
                        certificate = ByteArray(0),
                        name = "Device 1",
                        type = DeviceType.LAPTOP
                    ),
                    pairStatus = PairState.Paired,
                    isReachable = true
                ),
                DeviceState(
                    deviceInfo = DeviceInfo(
                        id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd24_",
                        certificate = ByteArray(0),
                        name = "Device 2",
                        type = DeviceType.DESKTOP
                    ),
                    pairStatus = PairState.Paired,
                    isReachable = true
                ),
            ),
            refreshing = false
        ),
        onClick = { },
        onRefresh = { },
        onNavigateToPairingScreen = {},
        onNavigateToSettingsScreen = {}
    )
}
