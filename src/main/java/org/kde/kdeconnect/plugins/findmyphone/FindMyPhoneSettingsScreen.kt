package org.kde.kdeconnect.plugins.findmyphone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FindMyPhoneSettingsScreen(
    viewModel: FindMyPhoneSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val uri = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                uri?.let { viewModel.setRingtone(it.toString()) }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setFlashlightEnabled(true)
        }
    }

    HazeScaffold(
        title = stringResource(R.string.plugin_settings_with_name, stringResource(R.string.findmyphone_title)),
        backButton = true,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Preference(
                title = stringResource(R.string.select_ringtone),
                summary = uiState.ringtoneTitle,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                        
                        val existingUri = if (uiState.ringtoneUri.isNotEmpty()) {
                            Uri.parse(uiState.ringtoneUri)
                        } else {
                            Settings.System.DEFAULT_RINGTONE_URI
                        }
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                    }
                    ringtonePickerLauncher.launch(intent)
                }
            )

            SwitchPreference(
                title = stringResource(R.string.findmyphone_preference_title_flashlight),
                summary = stringResource(R.string.findmyphone_camera_explanation),
                value = uiState.flashlightEnabled,
                onValueChanged = { enabled ->
                    if (enabled) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    } else {
                        viewModel.setFlashlightEnabled(false)
                    }
                }
            )
        }
    }
}
