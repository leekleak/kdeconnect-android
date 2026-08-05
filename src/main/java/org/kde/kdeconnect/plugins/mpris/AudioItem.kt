package org.kde.kdeconnect.plugins.mpris

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.plugins.systemvolume.Sink
import org.kde.kdeconnect.ui.compose.components.googleSans


@Composable
fun SinkItem(
    sink: Sink,
    setSinkEnabled: (String) -> Unit,
    setSinkVolume: (String, Int) -> Unit,
) {
    val isSelected = sink.isDefault
    val font = remember { googleSans(weight = 600f) }
    val textColor by animateColorAsState(if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface)
    val backgroundColor by animateColorAsState(if (isSelected) colorScheme.primary else colorScheme.primaryContainer)
    val iconColor by animateColorAsState(if (isSelected) colorScheme.onPrimary else colorScheme.onPrimaryContainer)
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { if (!sink.isDefault) setSinkEnabled(sink.name) }
            .background(if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerLowest)
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.background(backgroundColor, RoundedCornerShape(999.dp))
                .padding(6.dp)
        ) {
            Icon(
                painter = painterResource(org.kde.kdeconnect_tp.R.drawable.volume_up),
                contentDescription = null,
                tint = iconColor
            )
        }
        Column {
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = sink.description,
                color = textColor,
                fontFamily = font,
                maxLines = 1,
            )
            AnimatedVisibility(visible = isSelected) {
                val interactionSource = remember { MutableInteractionSource() }
                Slider(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(24.dp),
                    value = sink.volume.toFloat(),
                    onValueChange = { setSinkVolume(sink.name, it.toInt()) },
                    valueRange = 0f..sink.maxVolume.toFloat(),
                    colors = SliderDefaults.colors(inactiveTrackColor = colorScheme.surfaceContainerLowest),
                    interactionSource = interactionSource,
                    thumb = {
                        SliderDefaults.Thumb(
                            interactionSource = interactionSource,
                            thumbSize = DpSize(4.dp, 28.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun SourceItem(
    source: String,
    isSelected: Boolean,
    setSourceEnabled: () -> Unit,
) {
    val font = remember { googleSans(weight = 600f) }
    val textColor by animateColorAsState(if (isSelected) colorScheme.onPrimaryContainer else colorScheme.onSurface)
    val backgroundColor by animateColorAsState(if (isSelected) colorScheme.primary else colorScheme.primaryContainer)
    val iconColor by animateColorAsState(if (isSelected) colorScheme.onPrimary else colorScheme.onPrimaryContainer)
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable { if (!isSelected) setSourceEnabled() }
            .background(if (isSelected) colorScheme.primaryContainer else colorScheme.surfaceContainerLowest)
            .padding(16.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.background(backgroundColor, RoundedCornerShape(999.dp))
                .padding(6.dp)
        ) {
            Icon(
                painter = painterResource(org.kde.kdeconnect_tp.R.drawable.volume_up),
                contentDescription = null,
                tint = iconColor
            )
        }
        Column {
            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = source,
                color = textColor,
                fontFamily = font,
                maxLines = 1,
            )
        }
    }
}

@Composable
@Preview
fun SinkItemPreviewSelected() {
    SinkItem(
        Sink(
            name = "",
            description = "Headphones",
            volume = 70,
            maxVolume = 100,
            isMuted = false,
            isDefault = true
        ),
        setSinkVolume = { _, _ -> },
        setSinkEnabled = {},
    )
}

@Composable
@Preview
fun SinkItemPreview() {
    SinkItem(
        Sink(
            name = "",
            description = "Headphones",
            volume = 70,
            maxVolume = 100,
            isMuted = false,
            isDefault = false
        ),
        setSinkVolume = { _, _ -> },
        setSinkEnabled = {},
    )
}
