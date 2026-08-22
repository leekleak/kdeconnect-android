package org.kde.kdeconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.close

@Composable
fun FancyDialog(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter,
    content: @Composable (ColumnScope.() -> Unit),
    actionButton: @Composable (() -> Unit),
    onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .card(colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val font = googleSans(weight = 600f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    modifier = Modifier.size(32.dp),
                    painter = icon,
                    contentDescription = null,
                    tint = colorScheme.onSurface
                )
                Text(
                    text = title,
                    fontFamily = font,
                    fontSize = 20.sp,
                    color = colorScheme.onSurface
                )
            }
            content()
            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismissRequest
                ) {
                    Text(stringResource(Res.string.close))
                }
                actionButton()
            }
        }
    }
}