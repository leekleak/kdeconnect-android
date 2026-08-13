package org.kde.kdeconnect.ui.screen.device.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.PluginButton
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DeviceShortcutSettingsScreen(
    deviceId: String,
    viewModel: DeviceShortcutSettingsViewModel = koinViewModel(key = "DeviceShortcutSettingsViewModel_$deviceId") { parametersOf(deviceId) },
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(R.string.shortcut_settings),
        backAction = BackAction.Normal(navigator),
        scrollState = null,
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            contentPadding = paddingValues,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "text1") {
                CategoryTitleTextSmall(stringResource(R.string.shortcuts))
            }
            items(
                items = uiState.enabledShortcuts,
                key = { it.pluginKey + it.name }
            ) { button ->
                PluginButton(
                    modifier = Modifier.animateItem(),
                    button = button,
                    onClick = { viewModel.removeShortcut(button) }
                )
            }
//            item(span = { GridItemSpan(maxLineSpan) }, key = "divider") {
//                HorizontalDivider(Modifier.padding(vertical = 4.dp))
//            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "text2") {
                CategoryTitleTextSmall(stringResource(R.string.available_shortcuts))
            }
            items(
                items = uiState.disabledShortcuts,
                key = { it.pluginKey + it.name }
            ) { button ->
                PluginButton(
                    modifier = Modifier.animateItem(),
                    button = button,
                    onClick = { viewModel.addShortcut(button) }
                )
            }
        }
    }
}