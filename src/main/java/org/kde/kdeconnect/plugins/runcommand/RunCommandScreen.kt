package org.kde.kdeconnect.plugins.runcommand

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.FancyDialog
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun RunCommandScreen(
    deviceId: String,
    viewModel: RunCommandViewModel = koinViewModel(key = "RunCommandViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val plugin = viewModel.plugin
    val uiState by viewModel.uiState.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_runcommand),
        backButton = true,
        actions = {
            if (uiState.canAddCommands) {
                IconButton (
                    onClick = {
                        viewModel.sendSetupPacket()
                        showDialog = true
                    },
                ) {
                    Icon(
                        painterResource(R.drawable.edit),
                        stringResource(R.string.add_command),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) {
        if (showDialog) {
            FancyDialog(
                title = stringResource(R.string.add_command),
                icon = painterResource(R.drawable.edit),
                content = {
                    Text(stringResource(R.string.add_command_description))
                },
                actionButton = {
                    TextButton(onClick = {
                        showDialog = false
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                },
                onDismissRequest = {
                    showDialog = false
                }
            )
        }

        CategoryTitleTextSmall(stringResource(R.string.terminal))
        OutputCard(plugin.output, plugin, onStopClick = { viewModel.sendStop() })

        CategoryTitleTextSmall(stringResource(R.string.commands))
        if (!uiState.commandList.isEmpty()) {
            uiState.commandList.forEach { command ->
                val clipboardManager = LocalClipboard.current
                Preference(
                    title = command.name,
                    summary = command.command,
                    onClick = { viewModel.runCommand(command.key) },
                    onLongClick = {
                        viewModel.copyCommandToClipboard(context, command, clipboardManager)
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
    plugin: RunCommandPlugin,
    onStopClick: () -> Unit
) {
    val state = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showStopButton by remember { plugin.commandRunning }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(BorderStroke(1.5.dp, colorScheme.outline), MaterialTheme.shapes.medium)
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
                        IconButton(onClick = onStopClick) {
                            CircularProgressIndicator()
                            Icon(
                                painterResource(R.drawable.stop),
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

