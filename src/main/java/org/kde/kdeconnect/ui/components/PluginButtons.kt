package org.kde.kdeconnect.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.plugins.PluginUiButton

@Composable
fun PluginButtonsGrid(
    buttons: List<PluginUiButton>,
    fullName: Boolean = false,
    onClick: (PluginUiButton) -> Unit,
) {
    BoxWithConstraints {
        val minWidth = 152.dp
        val spacing = 8.dp
        val columns = ((maxWidth + spacing) / (minWidth + spacing)).toInt().coerceAtLeast(1)

        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            buttons.chunked(columns).forEach { rowButtons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowButtons.forEach { button ->
                        PluginButton(
                            modifier = Modifier.weight(1f),
                            button = button,
                            fullName = fullName,
                            onClick = { onClick(button) }
                        )
                    }
                    repeat(columns - rowButtons.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginButton(
    modifier: Modifier = Modifier,
    button: PluginUiButton,
    fullName: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .widthIn(min = 152.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = button.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = if (fullName) button.nameFull else button.name,
            maxLines = 2,
            fontSize = 16.sp,
            fontWeight = FontWeight(500),
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}