package org.kde.kdeconnect.ui.compose.screen.device

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.compose.components.BatteryComponent
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.IconHero
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.googleSans
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceScreen(
    deviceId: String,
    viewModel: DeviceViewModel = koinViewModel(key = "DeviceViewModel_$deviceId") { parametersOf(deviceId) },
    onNavigateToPluginsSettings: () -> Unit,
    onNavigateToPairingScreen: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    HazeScaffold(
        backButton = true,
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
                onButtonClick = { button -> activity?.let { button.onClick(it) } },
            )
        } else {
            onNavigateToPairingScreen()
        }
    }
}

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
) {
    PluginsScreenContent(
        buttons = pluginsWithButtons,
        onButtonClick = onButtonClick,
    )
}

@Composable
private fun PluginsScreenContent(
    buttons: List<Plugin.PluginUiButton>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit
) {
    val (sendButtons, controlButtons) = buttons.partition {
        it.category == Plugin.ButtonCategory.SEND
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sendButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(R.string.category_send))
            PluginButtonsGrid(sendButtons, onButtonClick)
        }
        if (controlButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(R.string.category_control))
            PluginButtonsGrid(controlButtons, onButtonClick)
        }
    }
}

@Composable
private fun PluginButtonsGrid(
    buttons: List<Plugin.PluginUiButton>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit
) {
    BoxWithConstraints {
        val minWidth = 178.dp
        val spacing = 8.dp
        val columns = ((maxWidth + spacing) / (minWidth + spacing)).toInt().coerceAtLeast(1)

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            buttons.chunked(columns).forEach { rowButtons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowButtons.forEach { button ->
                        PluginButton(
                            modifier = Modifier.weight(1f),
                            button = button,
                            onClick = { onButtonClick(button) }
                        )
                    }
                    repeat(columns - rowButtons.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginButton(
    modifier: Modifier = Modifier,
    button: Plugin.PluginUiButton,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .widthIn(min = 152.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = button.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = button.name,
            maxLines = 2,
            fontSize = 16.sp,
            fontWeight = FontWeight(500),
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@KdeThemePreviews
@Composable
private fun PluginsScreenPreview() {
    PluginsScreenContent(
        buttons = buildList {
            repeat(3) {
                add(
                    Plugin.PluginUiButton(
                        name = "Send Stuff",
                        iconRes = R.drawable.music_cast,
                        category = Plugin.ButtonCategory.SEND,
                        onClick = { }
                    )
                )
            }
            repeat(5) {
                add(
                    Plugin.PluginUiButton(
                        name = "Presentation Remote",
                        iconRes = R.drawable.play_arrow,
                        category = Plugin.ButtonCategory.CONTROL,
                        onClick = { }
                    )
                )
            }
        },
        onButtonClick = { },
    )
}



