package org.kde.kdeconnect.plugins.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.AppSelector
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.SearchField
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.compose.components.card
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
        val textFieldState = rememberTextFieldState(uiState.searchQuery)
        var addApps by remember { mutableStateOf(value = false) }

        LaunchedEffect(textFieldState.text) {
            viewModel.setSearchQuery(textFieldState.text.toString())
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.show_notification_if_screen_off),
                    value = uiState.screenOffNotification,
                    onValueChanged = viewModel::setScreenOffNotification
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .card()
                        .padding(vertical = 8.dp)
                ) {
                    // All apps toggle
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

                    // Enabled apps list (Horizontal)
                    AppSelector(
                        apps = uiState.enabledApps,
                        modifier = Modifier.fillMaxWidth(),
                        onLongClick = { packageName ->
                            showPrivacyDialogForApp = uiState.enabledApps.find { it.packageName == packageName }
                        }
                    ) { packageName ->
                        viewModel.setAppEnabled(packageName, false)
                    }

                    // Add Apps section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = { addApps = !addApps },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.pref_plugin_notifications)) // Reuse label
                        }
                    }

                    AnimatedVisibility(visible = addApps) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            AppSelector(
                                apps = uiState.disabledApps,
                                modifier = Modifier.fillMaxWidth()
                            ) { packageName ->
                                viewModel.setAppEnabled(packageName, true)
                            }
                            SearchField(textFieldState)
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
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
