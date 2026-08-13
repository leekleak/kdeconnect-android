package org.kde.kdeconnect.ui.screen.device

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.BatteryComponent
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.IconHero
import org.kde.kdeconnect.ui.components.KdeThemePreviews
import org.kde.kdeconnect.ui.components.PluginButtonsGrid
import org.kde.kdeconnect.ui.components.googleSans
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceScreen(
    deviceId: String,
    viewModel: DeviceViewModel = koinViewModel(key = "DeviceViewModel_$deviceId") { parametersOf(deviceId) },
    onNavigateToPluginsSettings: () -> Unit,
    onNavigateToPairingScreen: () -> Unit,
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        backAction = BackAction.Normal(navigator),
        actions = {
            IconButton(onNavigateToPluginsSettings) {
                Icon(
                    painter = painterResource(R.drawable.settings),
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
            IconHero(
                backgroundSize = 164.dp,
                iconSize = 88.dp,
                icon = uiState.deviceInfo.type.toDrawableId()
            )
            Text(
                text = uiState.deviceInfo.name,
                fontFamily = font,
                fontSize = 32.sp
            )
            uiState.batteryInfo?.let { BatteryComponent(it) }
        }
        if (uiState.isReachable) {
            PluginsScreen(
                pluginsWithButtons = uiState.loadedPlugins.values.flatMap { it.getUiButtons() },
            )
        } else {
            onNavigateToPairingScreen()
        }
    }
}

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<PluginUiButton>,
) {
    PluginsScreenContent(
        buttons = pluginsWithButtons,
    )
}

@Composable
private fun PluginsScreenContent(
    buttons: List<PluginUiButton>,
) {
    val activity = LocalActivity.current
    val (sendButtons, controlButtons) = buttons.partition {
        it.category == Plugin.ButtonCategory.SEND
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sendButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(R.string.category_send))
            PluginButtonsGrid(sendButtons) { button -> activity?.let { button.onClick(it) } }
        }
        if (controlButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(R.string.category_control))
            PluginButtonsGrid(controlButtons) { button -> activity?.let { button.onClick(it) } }
        }
    }
}

@KdeThemePreviews
@Composable
private fun PluginsScreenPreview() {
    PluginsScreenContent(
        buttons = buildList {
            repeat(3) {
                add(
                    PluginUiButton(
                        pluginKey = "",
                        name = "Send Stuff",
                        iconRes = R.drawable.music_cast,
                        category = Plugin.ButtonCategory.SEND,
                        onClick = { }
                    )
                )
            }
            repeat(5) {
                add(
                    PluginUiButton(
                        pluginKey = "",
                        name = "Presentation Remote",
                        iconRes = R.drawable.play_arrow,
                        category = Plugin.ButtonCategory.CONTROL,
                        onClick = { }
                    )
                )
            }
        },
    )
}



