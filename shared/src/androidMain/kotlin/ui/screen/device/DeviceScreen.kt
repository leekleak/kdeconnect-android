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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.category_control
import org.kde.kdeconnect.generated.resources.category_send
import org.kde.kdeconnect.generated.resources.music_cast
import org.kde.kdeconnect.generated.resources.play_arrow
import org.kde.kdeconnect.generated.resources.send_clipboard
import org.kde.kdeconnect.generated.resources.settings
import org.kde.kdeconnect.plugins.ButtonCategory
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
                    painter = painterResource(Res.drawable.settings),
                    contentDescription = stringResource(Res.string.settings)
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
            val font = googleSans(weight = 600f)
            IconHero(
                backgroundSize = 164.dp,
                iconSize = 88.dp,
                icon = uiState.deviceState.deviceInfo.type.toDrawableRes()
            )
            Text(
                text = uiState.deviceState.deviceInfo.name,
                fontFamily = font,
                fontSize = 32.sp
            )
            uiState.deviceState.batteryInfo?.let { BatteryComponent(it) }
        }
        if (uiState.deviceState.isReachable) {
            PluginsScreen(
                pluginsWithButtons = uiState.pluginButtons,
                navigator = navigator
            )
        } else {
            onNavigateToPairingScreen()
        }
    }
}

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<PluginUiButton>,
    navigator: Navigator,
) {
    PluginsScreenContent(
        buttons = pluginsWithButtons,
        navigator = navigator
    )
}

@Composable
private fun PluginsScreenContent(
    buttons: List<PluginUiButton>,
    navigator: Navigator,
) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val (sendButtons, controlButtons) = buttons.partition {
        it.category == ButtonCategory.SEND
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sendButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(Res.string.category_send))
            PluginButtonsGrid(sendButtons) { button -> activity?.let { scope.launch { button.onClick(it, navigator) } } }
        }
        if (controlButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(Res.string.category_control))
            PluginButtonsGrid(controlButtons) { button -> activity?.let { scope.launch { button.onClick(it, navigator) } } }
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
                        name = Res.string.send_clipboard,
                        iconRes = Res.drawable.music_cast,
                        category = ButtonCategory.SEND,
                        onClick = { _, _ -> }
                    )
                )
            }
            repeat(5) {
                add(
                    PluginUiButton(
                        pluginKey = "",
                        name = Res.string.send_clipboard,
                        iconRes = Res.drawable.play_arrow,
                        category = ButtonCategory.CONTROL,
                        onClick = { _, _ -> }
                    )
                )
            }
        },
        navigator = Navigator()
    )
}



