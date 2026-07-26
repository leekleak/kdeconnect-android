package org.kde.kdeconnect.plugins.mousepad

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

@Composable
fun BigscreenScreen(
    deviceId: String,
) { //Todo: Test all this with an actual TV when I have time to emulate one.
    val viewModel: BigscreenViewModel = koinViewModel(parameters = { parametersOf(deviceId) })
    val navigator = koinInject<Navigator>()
    val deviceManager = koinInject<DeviceManager>()
    val context = LocalContext.current

    var showRationale by remember { mutableStateOf(false) }

    val sttLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val firstResult = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (firstResult != null) {
                viewModel.sendText(firstResult)
            }
        }
    }

    val extraPrompt = stringResource(R.string.bigscreen_speech_extra_prompt)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, extraPrompt)
            }
            sttLauncher.launch(intent)
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.pref_plugin_bigscreen)) },
            text = { Text(stringResource(R.string.bigscreen_optional_permission_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    BigscreenContent(
        showHome = viewModel.showHome,
        showBack = viewModel.showBack,
        micEnabled = viewModel.micEnabled,
        onHomeClick = viewModel::sendHome,
        onUpClick = viewModel::sendUp,
        onMicClick = {
            val plugin = deviceManager.getDevicePlugin(deviceId, MousePadPlugin::class.java)
            if (plugin != null) {
                if (plugin.hasMicPermission(context)) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, extraPrompt)
                    }
                    sttLauncher.launch(intent)
                } else {
                    showRationale = true
                }
            }
        },
        onLeftClick = viewModel::sendLeft,
        onSelectClick = viewModel::sendSelect,
        onRightClick = viewModel::sendRight,
        onBackClick = viewModel::sendBack,
        onDownClick = viewModel::sendDown,
        onNavigateToSettings = {
            navigator.goTo(MousePadPluginSettingsKey)
        }
    )
}

@Composable
private fun BigscreenContent(
    showHome: Boolean,
    showBack: Boolean,
    micEnabled: Boolean,
    onHomeClick: () -> Unit,
    onUpClick: () -> Unit,
    onMicClick: () -> Unit,
    onLeftClick: () -> Unit,
    onSelectClick: () -> Unit,
    onRightClick: () -> Unit,
    onBackClick: () -> Unit,
    onDownClick: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    HazeScaffold(
        title = stringResource(R.string.pref_plugin_bigscreen),
        backButton = true,
        scrollState = null,
        actions = {
            IconButton(
                onClick = onNavigateToSettings
            ) {
                Icon(painterResource(R.drawable.ic_settings_24dp), stringResource(R.string.settings))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val buttonModifier = Modifier
                .weight(1f)
                .fillMaxSize()
            val iconSize = 42.dp
            val secondaryIconSize = 32.dp

            Row(modifier = Modifier.weight(1f)) {
                BigscreenButton(
                    modifier = buttonModifier,
                    visible = showHome,
                    onClick = onHomeClick,
                    iconRes = R.drawable.ic_home_black_24dp,
                    contentDescription = R.string.bigscreen_home,
                    iconSize = secondaryIconSize
                )
                BigscreenButton(
                    modifier = buttonModifier,
                    onClick = onUpClick,
                    iconRes = R.drawable.keyboard_arrow_up,
                    contentDescription = R.string.bigscreen_up,
                    iconSize = iconSize
                )
                BigscreenButton(
                    modifier = buttonModifier,
                    visible = micEnabled,
                    onClick = onMicClick,
                    iconRes = R.drawable.ic_mic_black,
                    contentDescription = R.string.bigscreen_mic,
                    iconSize = secondaryIconSize
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                BigscreenButton(
                    modifier = buttonModifier,
                    onClick = onLeftClick,
                    iconRes = R.drawable.keyboard_arrow_left,
                    contentDescription = R.string.bigscreen_left,
                    iconSize = iconSize
                )
                BigscreenButton(
                    modifier = buttonModifier,
                    onClick = onSelectClick,
                    iconRes = R.drawable.ic_keyboard_return_black_24dp,
                    contentDescription = R.string.bigscreen_select,
                    iconSize = secondaryIconSize
                )
                BigscreenButton(
                    modifier = buttonModifier,
                    onClick = onRightClick,
                    iconRes = R.drawable.keyboard_arrow_right,
                    contentDescription = R.string.bigscreen_right,
                    iconSize = iconSize
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                BigscreenButton(
                    modifier = buttonModifier,
                    visible = showBack,
                    onClick = onBackClick,
                    iconRes = R.drawable.ic_arrow_back_black_24dp,
                    contentDescription = R.string.bigscreen_back,
                    iconSize = secondaryIconSize
                )
                BigscreenButton(
                    modifier = buttonModifier,
                    onClick = onDownClick,
                    iconRes = R.drawable.keyboard_arrow_down,
                    contentDescription = R.string.bigscreen_down,
                    iconSize = iconSize
                )
                Box(modifier = buttonModifier) // Empty space
            }
        }
    }
}

@Composable
private fun BigscreenButton(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: Int,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (visible) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = stringResource(contentDescription),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun BigscreenPreview() {
    KoinApplication(configuration = koinConfiguration(declaration = {
        modules(module {
            single { Navigator() }
        })
    }), content = {
        KdeTheme(LocalContext.current) {
            BigscreenContent(
                showHome = true,
                showBack = true,
                micEnabled = true,
                onHomeClick = {},
                onUpClick = {},
                onMicClick = {},
                onLeftClick = {},
                onSelectClick = {},
                onRightClick = {},
                onBackClick = {},
                onDownClick = {},
                onNavigateToSettings = {}
            )
        }
    })
}
