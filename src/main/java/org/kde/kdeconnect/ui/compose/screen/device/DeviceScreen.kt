package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.kde.kdeconnect.PairingHandler

@Composable
fun DeviceScreen(
    deviceId: String,
    viewModel: DeviceViewModel = viewModel(factory = DeviceViewModelFactory(deviceId)),
    onNavigateToPluginsSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface {
        Column(modifier = Modifier.fillMaxSize()) {
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

// Simple Factory for ViewModel with arguments
class DeviceViewModelFactory(private val deviceId: String) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceViewModel(android.app.Application(), deviceId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
