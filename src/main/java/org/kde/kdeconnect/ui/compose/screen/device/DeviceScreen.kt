package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.util.TableInfo
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.googleSans
import org.kde.kdeconnect.ui.compose.screen.pairing.DeviceHero
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceScreen(
    deviceId: String,
    viewModel: DeviceViewModel = koinViewModel(key = "DeviceViewModel_$deviceId") { parametersOf(deviceId) },
    onNavigateToPluginsSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    HazeScaffold(
        title = "",
        backButton = true,
        actions = {
            IconButton(onNavigateToPluginsSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .height(300.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            val font = remember { googleSans(weight = 600f) }
            val batterySubtitle = uiState.batteryInfo?.let { " · ${it.currentCharge}%" }
            DeviceHero(164.dp, 88.dp, uiState.deviceUiModel)
            Text(
                text = uiState.deviceUiModel.name + (batterySubtitle ?: ""),
                fontFamily = font,
                fontSize = 32.sp
            )
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
                if (uiState.deviceUiModel.isReachable) {
                    PluginsScreen(
                        pluginsWithButtons = uiState.pluginsWithButtons,
                        onButtonClick = { button -> button.onClick(context as android.app.Activity) },
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


