package org.kde.kdeconnect.plugins.notifications

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationFilterScreen(
    viewModel: NotificationFilterViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showPrivacyDialogForApp by remember { mutableStateOf<AppInfo?>(null) }

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_notifications),
        scrollState = null,
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SwitchPreference(
                title = stringResource(R.string.show_notification_if_screen_off),
                value = uiState.screenOffNotification,
                onValueChanged = viewModel::setScreenOffNotification
            )

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text(stringResource(android.R.string.search_go)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search_24), contentDescription = null) },
                singleLine = true
            )

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAllEnabled(!uiState.allEnabled) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.all),
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
                            )
                            Checkbox(
                                checked = uiState.allEnabled,
                                onCheckedChange = { viewModel.setAllEnabled(it) }
                            )
                        }
                    }

                    items(uiState.apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setAppEnabled(app.packageName, !app.isEnabled) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap(48.dp.value.toInt(), 48.dp.value.toInt()).asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = app.name, fontWeight = FontWeight.Medium)
                                Text(
                                    text = stringResource(R.string.extra_options),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable { showPrivacyDialogForApp = app }
                                )
                            }
                            Checkbox(
                                checked = app.isEnabled,
                                onCheckedChange = { viewModel.setAppEnabled(app.packageName, it) }
                            )
                        }
                    }
                }
            }
        }

        showPrivacyDialogForApp?.let { app ->
            AlertDialog(
                onDismissRequest = { showPrivacyDialogForApp = null },
                title = { Text(stringResource(R.string.privacy_options) + ": " + app.name) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.blockContents,
                                onCheckedChange = { viewModel.setAppPrivacy(app.packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS, it) }
                            )
                            Text(stringResource(R.string.block_notification_contents))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = app.blockImages,
                                onCheckedChange = { viewModel.setAppPrivacy(app.packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES, it) }
                            )
                            Text(stringResource(R.string.block_notification_images))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPrivacyDialogForApp = null }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}
