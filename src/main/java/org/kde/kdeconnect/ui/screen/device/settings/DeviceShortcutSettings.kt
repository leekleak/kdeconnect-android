package org.kde.kdeconnect.ui.screen.device.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.PluginButton
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

private const val KEY_HEADER_ENABLED = "text1"
private const val KEY_DIVIDER = "divider"
private const val KEY_HEADER_DISABLED = "text2"

private sealed interface ShortcutListItem {
    val key: String
}

private data class SectionHeader(val label: String, override val key: String) : ShortcutListItem
private data object SectionDivider : ShortcutListItem {
    override val key: String = KEY_DIVIDER
}
private data class ButtonEntry(val button: PluginUiButton) : ShortcutListItem {
    override val key: String get() = button.name.toString()
}

@Composable
fun DeviceShortcutSettingsScreen(
    deviceId: String,
    viewModel: DeviceShortcutSettingsViewModel = koinViewModel(key = "DeviceShortcutSettingsViewModel_$deviceId") { parametersOf(deviceId) },
    navigator: Navigator,
) {
    val flatItems = remember { mutableStateListOf<ShortcutListItem>() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val enabledHeaderLabel = stringResource(R.string.shortcuts)
    val disabledHeaderLabel = stringResource(R.string.available_shortcuts)

    LaunchedEffect(uiState) {
        flatItems.clear()
        flatItems += SectionHeader(enabledHeaderLabel, KEY_HEADER_ENABLED)
        flatItems += uiState.enabled.map { ButtonEntry(it) }
        flatItems += SectionDivider
        flatItems += SectionHeader(disabledHeaderLabel, KEY_HEADER_DISABLED)
        flatItems += uiState.disabled.map { ButtonEntry(it) }
    }

    val lazyGridState = rememberLazyGridState()
    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyGridState
        val toKey = to.key as? String ?: return@rememberReorderableLazyGridState
        
        if (fromKey == KEY_HEADER_ENABLED || fromKey == KEY_HEADER_DISABLED || fromKey == KEY_DIVIDER) return@rememberReorderableLazyGridState
        if (toKey == KEY_HEADER_ENABLED || toKey == KEY_HEADER_DISABLED || toKey == KEY_DIVIDER) return@rememberReorderableLazyGridState

        val fromIndex = flatItems.indexOfFirst { it.key == fromKey }
        val toIndex = flatItems.indexOfFirst { it.key == toKey }
        if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyGridState

        flatItems.add(toIndex, flatItems.removeAt(fromIndex))
    }

    fun persist() {
        val dividerIndex = flatItems.indexOfFirst { it.key == KEY_DIVIDER }
        if (dividerIndex == -1) return

        val enabledKeys = flatItems.subList(1, dividerIndex)
            .mapNotNull { (it as? ButtonEntry)?.button?.pluginKey }

        viewModel.updateShortcuts(enabledKeys)
    }

    HazeScaffold(
        title = stringResource(R.string.shortcut_settings),
        backAction = BackAction.Normal(navigator),
        scrollState = null,
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            contentPadding = paddingValues,
            state = lazyGridState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = flatItems,
                key = { it.key },
                span = { item ->
                    when (item) {
                        is SectionHeader, SectionDivider -> GridItemSpan(maxLineSpan)
                        is ButtonEntry -> GridItemSpan(1)
                    }
                }
            ) { item ->
                when (item) {
                    is SectionHeader -> CategoryTitleTextSmall(item.label)
                    SectionDivider -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    is ButtonEntry -> {
                        ReorderableItem(reorderableLazyGridState, key = item.key, modifier = Modifier.fillMaxWidth()) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.large,
                                shadowElevation = elevation,
                                tonalElevation = elevation
                            ) {
                                PluginButton(
                                    modifier = Modifier.draggableHandle(
                                        onDragStopped = { persist() },
                                    ),
                                    button = item.button,
                                    onClick = { }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
