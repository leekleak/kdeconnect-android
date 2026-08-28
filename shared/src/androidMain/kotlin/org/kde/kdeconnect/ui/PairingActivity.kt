package org.kde.kdeconnect.ui

import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.cancel
import org.kde.kdeconnect.generated.resources.ic_kde_24dp
import org.kde.kdeconnect.generated.resources.key
import org.kde.kdeconnect.generated.resources.link
import org.kde.kdeconnect.generated.resources.pairing_accept
import org.kde.kdeconnect.generated.resources.pairing_request
import org.kde.kdeconnect.ui.components.IconHero
import org.kde.kdeconnect.ui.components.card
import org.kde.kdeconnect.ui.components.googleSans
import org.koin.android.ext.android.inject

class PairingActivity : AppCompatActivity() {
    private val deviceHelper: DeviceManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra("deviceId")
        val device = deviceHelper.getDevice(deviceId)

        if (device == null) {
            finish()
            return
        }

        setContent {
            KdeTheme {
                var currentVisible by remember { mutableStateOf(true) }
                PairingSheet(lifecycleScope, currentVisible, device) {
                    finish()
                }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingSheet(scope: CoroutineScope, visible: Boolean, device: Device, onDone: () -> Unit) {
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
                BottomSheetContent(scope, device, onDone)
            }
        }
    }
}

@Composable
private fun BottomSheetContent(
    scope: CoroutineScope,
    device: Device,
    onDone: () -> Unit
) {
    val font = googleSans(weight = 600f)

    IconHero(
        backgroundSize = 164.dp,
        iconSize = 88.dp,
        icon = Res.drawable.ic_kde_24dp
    )
    Spacer(Modifier.height(16.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painter = painterResource(Res.drawable.link),
            contentDescription = null
        )
        Text(
            fontFamily = font,
            fontSize = 22.sp,
            text = stringResource(Res.string.pairing_request)
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
        val state by device.state.collectAsStateWithLifecycle()
        Text(
            fontFamily = font,
            fontSize = 18.sp,
            text = state.deviceInfo.name
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painter = painterResource(Res.drawable.key),
                contentDescription = null
            )
            Text(
                text = state.verificationKey ?: ""
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.error
            ),
            onClick = {
                scope.launch {
                    device.cancelPairing()
                    onDone()
                }
            }
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                fontSize = 18.sp,
                text = stringResource(Res.string.cancel)
            )
        }
        Button(
            onClick = {
                scope.launch {
                    device.acceptPairing()
                    onDone()
                }
            }
        ) {
            Text(
                modifier = Modifier.padding(8.dp),
                fontSize = 18.sp,
                text = stringResource(Res.string.pairing_accept)
            )
        }
    }

}
