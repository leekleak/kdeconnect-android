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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.*
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardService.KeyboardAction

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
                    painter = painterResource(Res.drawable.keyboard_hide),
                    contentDescription = stringResource(Res.string.hide),
                    tint = tint
                )
            }
            IconButton({ onKeyPress(KeyboardAction.SETTINGS) }) {
                Icon(
                    painter = painterResource(Res.drawable.settings),
                    contentDescription = stringResource(Res.string.settings),
                    tint = tint
                )
            }
            IconButton({ onKeyPress(KeyboardAction.SELECT_KEYBOARD) }) {
                Icon(
                    painter = painterResource(Res.drawable.keyboard_keys),
                    contentDescription = stringResource(Res.string.select_keyboard),
                    tint = tint
                )
            }
            IconButton({ onKeyPress(KeyboardAction.CHECK_CONNECTION) }) {
                Icon(
                    painter = painterResource(
                        if (isConnected) Res.drawable.link
                        else Res.drawable.link_off
                    ),
                    contentDescription = stringResource(Res.string.check_connection),
                    tint = tint
                )
            }
        }
    }
}
