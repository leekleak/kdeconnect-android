package org.kde.kdeconnect.plugins.mousepad

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.arrow_back_ios_new
import org.kde.kdeconnect.generated.resources.bigscreen_back
import org.kde.kdeconnect.generated.resources.bigscreen_down
import org.kde.kdeconnect.generated.resources.bigscreen_home
import org.kde.kdeconnect.generated.resources.bigscreen_left
import org.kde.kdeconnect.generated.resources.bigscreen_mic
import org.kde.kdeconnect.generated.resources.bigscreen_right
import org.kde.kdeconnect.generated.resources.bigscreen_select
import org.kde.kdeconnect.generated.resources.bigscreen_speech_extra_prompt
import org.kde.kdeconnect.generated.resources.bigscreen_up
import org.kde.kdeconnect.generated.resources.circle
import org.kde.kdeconnect.generated.resources.grant
import org.kde.kdeconnect.generated.resources.home
import org.kde.kdeconnect.generated.resources.kde_connect
import org.kde.kdeconnect.generated.resources.keyboard_arrow_down
import org.kde.kdeconnect.generated.resources.keyboard_arrow_left
import org.kde.kdeconnect.generated.resources.keyboard_arrow_right
import org.kde.kdeconnect.generated.resources.keyboard_arrow_up
import org.kde.kdeconnect.generated.resources.mic
import org.kde.kdeconnect.generated.resources.pref_plugin_bigscreen
import org.kde.kdeconnect.generated.resources.speech_to_text_provider_not_found
import org.kde.kdeconnect.generated.resources.unreachable_description
import org.kde.kdeconnect.ui.PermissionExplanationActivity
import org.kde.kdeconnect.ui.PermissionRequest
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.KdeThemePreviews
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun BigscreenScreen(
    deviceId: String,
    navigator: Navigator
) {
    val viewModel: BigscreenViewModel = koinViewModel(parameters = { parametersOf(deviceId) })
    val deviceManager = koinInject<DeviceManager>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sttLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val firstResult = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (firstResult != null) {
                viewModel.sendText(firstResult)
            }
        }
    }


    val extraPrompt = stringResource(Res.string.bigscreen_speech_extra_prompt)

    val sttPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            launchStt(extraPrompt, sttLauncher, context)
        }
    }

    BigscreenContent(
        onHomeClick = viewModel::sendHome,
        onUpClick = viewModel::sendUp,
        onMicClick = {
            scope.launch {
                val plugin = deviceManager.getDevicePlugin(deviceId, MousePadPlugin::class.java)
                if (plugin != null) {
                    val missingPermissionRequests = arrayOf(Manifest.permission.RECORD_AUDIO).filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }.map { permission ->
                        PermissionRequest(
                            title = getString(Res.string.kde_connect),
                            description = getString(Res.string.unreachable_description),
                            intentAction = permission,
                            positiveButton = getString(Res.string.grant)
                        )
                    }
                    if (missingPermissionRequests.isEmpty()) {
                        launchStt(extraPrompt, sttLauncher, context)
                    } else {
                        sttPermissionLauncher.launch(Intent(context, PermissionExplanationActivity::class.java).apply {
                            putExtra("permissionRequests", Json.encodeToString(missingPermissionRequests.take(1)))
                        })
                    }
                }
            }
        },
        onLeftClick = viewModel::sendLeft,
        onSelectClick = viewModel::sendSelect,
        onRightClick = viewModel::sendRight,
        onBackClick = viewModel::sendBack,
        onDownClick = viewModel::sendDown,
        navigator = navigator
    )
}

private fun launchStt(
    extraPrompt: String,
    sttLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    context: Context
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, extraPrompt)
        }
        sttLauncher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            runBlocking { getString(Res.string.speech_to_text_provider_not_found) },
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun BigscreenContent(
    onHomeClick: () -> Unit,
    onUpClick: () -> Unit,
    onMicClick: () -> Unit,
    onLeftClick: () -> Unit,
    onSelectClick: () -> Unit,
    onRightClick: () -> Unit,
    onBackClick: () -> Unit,
    onDownClick: () -> Unit,
    navigator: Navigator,
) {
    HazeScaffold(
        title = stringResource(Res.string.pref_plugin_bigscreen),
        backAction = BackAction.Normal(navigator),
        scrollState = null,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigscreenButton(
                onClick = onUpClick,
                shape = RoundedCornerShape(16.dp, 16.dp, 8.dp, 8.dp),
                iconRes = Res.drawable.keyboard_arrow_up,
                contentDescription = Res.string.bigscreen_up,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigscreenButton(
                    onClick = onLeftClick,
                    shape = RoundedCornerShape(16.dp, 8.dp, 8.dp, 16.dp),
                    iconRes = Res.drawable.keyboard_arrow_left,
                    contentDescription = Res.string.bigscreen_left,
                )
                BigscreenButton(
                    onClick = onSelectClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                    iconRes = Res.drawable.circle,
                    contentDescription = Res.string.bigscreen_select,
                )
                BigscreenButton(
                    onClick = onRightClick,
                    shape = RoundedCornerShape(8.dp, 16.dp, 16.dp, 8.dp),
                    iconRes = Res.drawable.keyboard_arrow_right,
                    contentDescription = Res.string.bigscreen_right,
                )
            }

            BigscreenButton(
                onClick = onDownClick,
                shape = RoundedCornerShape(8.dp, 8.dp, 16.dp, 16.dp),
                iconRes = Res.drawable.keyboard_arrow_down,
                contentDescription = Res.string.bigscreen_down,
            )

            Spacer(Modifier.height(54.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigscreenButton(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(16.dp, 8.dp, 8.dp, 16.dp),
                    iconRes = Res.drawable.arrow_back_ios_new,
                    contentDescription = Res.string.bigscreen_back,
                )
                BigscreenButton(
                    onClick = onHomeClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                    iconRes = Res.drawable.home,
                    contentDescription = Res.string.bigscreen_home,
                )
                BigscreenButton(
                    onClick = onMicClick,
                    shape = RoundedCornerShape(8.dp, 16.dp, 16.dp, 8.dp),
                    iconRes = Res.drawable.mic,
                    contentDescription = Res.string.bigscreen_mic,
                )
            }
        }
    }
}

@Composable
private fun BigscreenButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    iconRes: DrawableResource,
    contentDescription: StringResource,
) {
    FilledIconButton(
        modifier = modifier.size(72.dp),
        shape = shape,
        colors = colors,
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.size(32.dp),
            painter = painterResource(iconRes),
            contentDescription = stringResource(contentDescription),
        )
    }
}

@KdeThemePreviews
@Composable
private fun BigscreenPreview() {
    BigscreenContent(
        onHomeClick = {},
        onUpClick = {},
        onMicClick = {},
        onLeftClick = {},
        onSelectClick = {},
        onRightClick = {},
        onBackClick = {},
        onDownClick = {},
        navigator = Navigator()
    )
}
