package org.kde.kdeconnect.plugins.remotekeyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardService.KeyboardAction
import org.kde.kdeconnect_tp.R

@Composable
fun RemoteKeyboardContent(
    isConnected: Boolean,
    onKeyPress: (KeyboardAction) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp, 8.dp, 0.dp, 0.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().systemBarsPadding(),
            horizontalArrangement = Arrangement.Center
        ) {
            val tint = MaterialTheme.colorScheme.onSurface
            IconButton({ onKeyPress(KeyboardAction.HIDE) }) {
                Icon(
                    painter = painterResource(R.drawable.keyboard_hide),
                    contentDescription = stringResource(R.string.hide),
                    tint = tint
                )
            }
            IconButton({ onKeyPress(KeyboardAction.SETTINGS) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(R.string.settings),
                    tint = tint
                )
            }
            IconButton({ onKeyPress(KeyboardAction.SELECT_KEYBOARD) }) {
                Icon(
                    painter = painterResource(R.drawable.keyboard_keys),
                    contentDescription = stringResource(R.string.select_keyboard),
                    tint = tint
                )
            }
            IconButton({ onKeyPress(KeyboardAction.CHECK_CONNECTION) }) {
                Icon(
                    painter = painterResource(
                        id = if (isConnected) R.drawable.link
                        else R.drawable.link_off
                    ),
                    contentDescription = stringResource(R.string.check_connection),
                    tint = tint
                )
            }
        }
    }
}
