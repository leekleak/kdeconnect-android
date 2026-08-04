package org.kde.kdeconnect.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.IconHero
import org.kde.kdeconnect.ui.compose.components.card
import org.kde.kdeconnect.ui.compose.components.googleSans
import org.kde.kdeconnect_tp.R

class PermissionExplanationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pluginKey = intent.getStringExtra("pluginKey")

        if (pluginKey == null) {
            finish()
            return
        }

        val pluginInfo = PluginFactory.getPluginInfo(pluginKey)

        setResult(RESULT_CANCELED)

        val requests = pluginInfo.getPermissionRequests()

        setContent {
            KdeTheme {
                var currentVisible by remember { mutableIntStateOf(0) }
                LaunchedEffect(currentVisible) {
                    if (currentVisible >= requests.size) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }
                requests.forEachIndexed { index, request ->
                    PermissionRequestSheet(index == currentVisible, request) { granted ->
                        if (!granted) finish()
                        currentVisible += 1
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionRequestSheet(visible: Boolean, request: PermissionRequest, onDone: (Boolean) -> Unit) {
    val activity = LocalActivity.current
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = {
                activity?.finish()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BottomSheetContent(request, onDone)
            }
        }
    }
}

@Composable
private fun BottomSheetContent(
    request: PermissionRequest,
    onDone: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        onDone(result)
    }

    val font = remember { googleSans(weight = 600f) }

    IconHero(
        backgroundSize = 164.dp,
        iconSize = 88.dp,
        /**
         * Want to make it really clear that it's KDE Connect requesting this permission
         * as this sheet may be overlaid on top of other apps.
         **/
        icon = R.drawable.ic_kde_24dp
    )
    Spacer(Modifier.height(16.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.grant),
            contentDescription = null
        )
        Text(
            fontFamily = font,
            fontSize = 22.sp,
            text = stringResource(R.string.permission_request)
        )
    }
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .card()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            fontFamily = font,
            fontSize = 18.sp,
            text = stringResource(request.title)
        )
        Text(
            text = stringResource(request.description)
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error
            ),
            onClick = { onDone(false) }
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                fontSize = 18.sp,
                text = stringResource(R.string.cancel)
            )
        }
        Button(
            onClick = {
                if (request.intentAction.startsWith("android.permission.")) {
                    permissionLauncher.launch(request.intentAction)
                } else {
                    try {
                        activity?.startActivity(Intent(request.intentAction))
                    } catch (_: ActivityNotFoundException) {
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        activity?.startActivity(intent)
                        onDone(false) // Really annoying to check if the user actually granted permission,
                        // so return with the assumption that they didn't.
                    }
                }
            }
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                fontSize = 18.sp,
                text = stringResource(request.positiveButton)
            )
        }
    }

}
