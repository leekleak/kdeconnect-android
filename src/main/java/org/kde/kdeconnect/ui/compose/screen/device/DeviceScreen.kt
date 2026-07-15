package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
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
    val scrollState = rememberScrollState()
    val device = KdeConnect.getInstance().getDevice(deviceId)

    HazeScaffold(
        title = uiState.deviceName,
        scrollState = null,
        backButton = true,
        actions = {
            IconButton(onNavigateToPluginsSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(R.string.settings)
                )
            }
        }
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
                                val dialog = if (uiState.pluginsNeedPermissions.contains(plugin)) {
                                    plugin.pluginInfo.let {
                                        if (plugin.preferences != null && device != null) {
                                            it.getPermissionExplanationDialog(plugin.preferences!!, context, device)
                                        } else {
                                            it.getPermissionExplanationDialog(context)
                                        }
                                    }
                                } else {
                                    plugin.pluginInfo.getOptionalPermissionExplanationDialog(context)
                                }
                                (context as? FragmentActivity)?.let {
                                    dialog.show(it.supportFragmentManager, "permission_explanation")
                                }
                            },
                            onUnpair = viewModel::unpair
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


