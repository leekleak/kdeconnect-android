package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceScreen(
    deviceId: String,
    viewModel: DeviceViewModel = koinViewModel { parametersOf(deviceId) },
    onNavigateToPluginsSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()

    HazeScaffold(
        title = uiState.deviceName,
        scrollState = null,
        backButton = true
    ) {paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(paddingValues)
        ) {
            uiState.batterySubtitle?.let {
                CategoryTitleTextSmall(text = it)
            }
            when (uiState.pairStatus) {
                PairingHandler.PairState.NotPaired,
                PairingHandler.PairState.Requested,
                PairingHandler.PairState.RequestedByPeer -> {
                    DevicePairingScreen(
                        pairStatus = uiState.pairStatus,
                        verificationKey = uiState.verificationKey ?: "",
                        onRequestPairing = { viewModel.requestPairing() },
                        onAcceptPairing = { viewModel.acceptPairing() },
                        onRejectPairing = { viewModel.cancelPairing() }
                    )
                }
                PairingHandler.PairState.Paired -> {
                    if (uiState.isReachable) {
                        PluginsScreen(
                            pluginsWithButtons = uiState.pluginsWithButtons,
                            pluginsNeedPermissions = uiState.pluginsNeedPermissions,
                            pluginsNeedOptionalPermissions = uiState.pluginsNeedOptionalPermissions,
                            onButtonClick = { button -> button.onClick(context as android.app.Activity) },
                            action = { plugin -> 
                                // Omit optional permission dialog for now or handle via bridge
                            }
                        )
                    } else {
                        DeviceErrorScreen(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { viewModel.refreshDevicesAction() }
                        )
                    }
                }
            }
        }
    }
}


