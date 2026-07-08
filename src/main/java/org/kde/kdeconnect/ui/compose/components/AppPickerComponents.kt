package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import org.kde.kdeconnect.helpers.AppIcon
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.notifications.AppInfo
import org.kde.kdeconnect_tp.R

@Composable
fun SearchField(textFieldState: TextFieldState) {
    Row(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .background(colorScheme.surfaceContainerHigh, shapes.extraLarge)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search_24),
            contentDescription = null
        )
        BasicTextField(
            modifier = Modifier.fillMaxWidth(),
            state = textFieldState,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = colorScheme.onSurface),
            cursorBrush = SolidColor(colorScheme.onSurface)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppSelector(
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
    onLongClick: ((packageName: String) -> Unit)? = null,
    onClick: (packageName: String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item ("holder") {  }
        items(apps, { it.packageName }) { app ->
            TooltipBox(
                modifier = Modifier
                    .clip(shapes.medium)
                    .combinedClickable(
                        onClick = {
                            onClick(app.packageName)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onLongClick = onLongClick?.let {
                            {
                                it(app.packageName)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                    .padding(4.dp),
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above,
                    4.dp
                ),
                tooltip = {
                    PlainTooltip { Text(app.name) }
                },
                state = rememberTooltipState(),
            ) {
                Column(
                    Modifier.width(64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(AppIcon(app.packageName)),
                        contentDescription = app.name,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = app.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (apps.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.fillParentMaxWidth(),
                    text = stringResource(R.string.empty),
                    fontFamily = googleSans(weight = 600f),
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
