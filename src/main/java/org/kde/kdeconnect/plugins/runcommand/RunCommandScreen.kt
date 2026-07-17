package org.kde.kdeconnect.plugins.runcommand

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.px
import org.kde.kdeconnect.ui.compose.components.smartDashBorder
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RunCommandScreen(
    deviceId: String,
    viewModel: RunCommandViewModel = koinViewModel(key = "RunCommandViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val plugin = viewModel.plugin ?: return
    val commandList = viewModel.commandList
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(plugin) {
        val callback = RunCommandPlugin.CommandsChangedCallback {
            scope.launch(Dispatchers.Main.immediate) {
                viewModel.updateList()
            }
        }
        plugin.addCommandsUpdatedCallback(callback)

        onDispose {
            plugin.removeCommandsUpdatedCallback(callback)
        }
    }

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_runcommand),
        backButton = true,
        actions = {
            if (plugin.canAddCommand()) {
                FloatingActionButton(
                    onClick = {
                        plugin.sendSetupPacket()
                        showDialog = true
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_action_image_edit_24dp),
                        stringResource(R.string.add_command),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) {
        if (showDialog) {
            AlertDialog(
                title = {
                    Text(
                        stringResource(R.string.add_command),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Text(stringResource(R.string.add_command_description))
                },
                onDismissRequest = {
                    showDialog = false
                    viewModel.updateList()
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        viewModel.updateList()
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                dismissButton = {},
            )
        }

        CategoryTitleTextSmall(stringResource(R.string.terminal))
        OutputCard(plugin.output, plugin)

        CategoryTitleTextSmall(stringResource(R.string.commands))
        if (!commandList.isEmpty()) {
            commandList.forEach { command ->
                val clipboardManager = androidx.compose.ui.platform.LocalClipboard.current
                Preference(
                    title = command.name,
                    summary = command.command,
                    onClick = { plugin.runCommand(command.key) },
                    onLongClick = {
                        viewModel.copyCommandToClipboard(command, clipboardManager)
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var text = stringResource(R.string.addcommand_explanation)
                if (!(plugin.canAddCommand())) {
                    text += "\n" + stringResource(R.string.addcommand_explanation2)
                }
                Text(text)
            }
        }
    }
}

@Composable
private fun OutputCard(
    outputList: SnapshotStateList<RunCommandOutput>,
    plugin: RunCommandPlugin
) {
    val state = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showStopButton by remember { plugin.commandRunning }

    val width = 2.dp.px
    val dashLength = 8.dp.px
    val cornerRadius = 16.dp.px
    val outlineColor = colorScheme.outline
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .drawBehind { smartDashBorder(cornerRadius, dashLength, width, outlineColor) }
    ) {
        if (outputList.isNotEmpty()) {
            Box {
                LazyColumn(
                    modifier = Modifier
                        .padding(horizontal = 15.dp, vertical = 5.dp)
                        .fillMaxWidth(),
                    state = state
                ) {
                    items(outputList) { text ->
                        Text(
                            modifier = Modifier
                                .fillMaxWidth(),
                            text = text.string,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (text.isCommand) FontWeight.ExtraBold else FontWeight.Normal
                        )
                    }
                }
                if (showStopButton) {
                    Column(
                        modifier = Modifier
                            .padding(5.dp)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        IconButton(onClick = { plugin.sendStop() }) {
                            CircularProgressIndicator()
                            Icon(
                                painterResource(R.drawable.ic_stop),
                                stringResource(R.string.runcommand_stop)
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.runcommand_output_no_output),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.runcommand_output_no_output_desc),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }

    LaunchedEffect(outputList.size) {
        if (outputList.isNotEmpty()) {
            coroutineScope.launch {
                state.animateScrollToItem(outputList.size - 1)
            }
        }
    }
}

