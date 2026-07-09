package org.kde.kdeconnect.ui.compose.screen.settings.advanced.filesystem

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.helpers.StorageHelper
import org.kde.kdeconnect.plugins.sftp.SftpPlugin
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SftpSettingsScreen(
    viewModel: SftpSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var storageToEdit by remember { mutableStateOf<SftpPlugin.StorageInfo?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val error = viewModel.isUriAllowed(uri)
            if (error == null) {
                val displayName = StorageHelper.getDisplayName(uri)
                viewModel.addStorage(
                    SftpPlugin.StorageInfo(displayName, uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
    }

    HazeScaffold(
        title = stringResource(R.string.pref_plugin_sftp),
        backButton = true,
        scrollState = null
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryTitleTextSmall(text = stringResource(R.string.sftp_preference_configured_storage_locations))
            }

            items(uiState.storageInfoList) { storageInfo ->
                Preference(
                    title = storageInfo.displayName,
                    summary = DocumentsContract.getTreeDocumentId(storageInfo.uri),
                    onClick = {
                        storageToEdit = storageInfo
                    }
                )
            }

            item {
                Preference(
                    title = stringResource(R.string.sftp_preference_add_storage_location_title),
                    icon = painterResource(R.drawable.ic_add),
                    onClick = { launcher.launch(null) }
                )
            }
        }
    }

    storageToEdit?.let { storage ->
        EditStorageDialog(
            storageInfo = storage,
            onDismiss = { storageToEdit = null },
            onConfirm = { newName ->
                viewModel.updateStorage(storage.uri, newName)
                storageToEdit = null
            },
            onDelete = {
                viewModel.deleteStorages(setOf(storage.uri))
                storageToEdit = null
            },
            isNameAllowed = { name -> viewModel.isDisplayNameAllowed(name, storage.uri) }
        )
    }
}

@Composable
fun EditStorageDialog(
    storageInfo: SftpPlugin.StorageInfo,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onDelete: () -> Unit,
    isNameAllowed: (String) -> String?
) {
    var name by remember { mutableStateOf(storageInfo.displayName) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sftp_preference_edit_storage_location),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = isNameAllowed(it)
                },
                label = { Text(stringResource(R.string.sftp_storage_preference_display_name)) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.sftp_action_mode_menu_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = { onConfirm(name) },
                    enabled = error == null && name.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
