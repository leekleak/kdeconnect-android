package org.kde.kdeconnect.plugins.findmyphone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.googleSans
import org.kde.kdeconnect_tp.R

@Composable
fun FindMyPhoneScreen(
    deviceId: String,
    onFinish: () -> Unit
) {
    DisposableEffect(deviceId) {
        val plugin = KdeConnect.getInstance().getDevicePlugin(deviceId, FindMyPhonePlugin::class.java)
        plugin?.let {
            it.startPlaying()
            it.startFlashing()
            it.hideNotification()
        }

        onDispose {
            val stopPlugin = KdeConnect.getInstance().getDevicePlugin(deviceId, FindMyPhonePlugin::class.java)
            stopPlugin?.let {
                it.stopPlaying()
                it.stopFlashing()
            }
        }
    }

    HazeScaffold(
        title = stringResource(R.string.findmyphone_title),
        scrollState = null
    ) {
        val font = remember { googleSans(weight = 600f) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onFinish() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            Icon(
                modifier = Modifier.size(92.dp),
                painter = painterResource(R.drawable.check_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = stringResource(R.string.findmyphone_found),
                fontSize = 64.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
