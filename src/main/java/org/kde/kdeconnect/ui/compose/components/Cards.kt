/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject

@Composable
fun KdeCard(
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit),
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(),
        elevation = CardDefaults.cardElevation(),
        content = content
    )
}

@PreviewLightDark
@Composable
private fun KdeCardPreview() {
    KdeCard(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Text(
                text = "A very long device name that might wrap into multiple lines",
                modifier = Modifier.padding(all = 16.dp),
                style = MaterialTheme.typography.bodyLarge, // textAppearanceMedium
                color = colorScheme.onSurfaceVariant
            )
        },
        onClick = { /* Do nothing */ }
    )
}

@Composable
fun Modifier.card(backgroundColor: Color = colorScheme.surfaceContainer): Modifier {
    return this
        .clip(MaterialTheme.shapes.large)
        .background(backgroundColor)
}

@Composable
fun DeviceCard(
    device: DeviceUiModel,
    actionIcon: Painter = painterResource(R.drawable.arrow_forward_ios),
    actionDescription: String = stringResource(R.string.open),
    onClick: (String) -> Unit
) {
    val deviceManager = koinInject<DeviceManager>()
    val context = LocalContext.current
    val font = remember { googleSans(weight = 600f) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colorScheme.surfaceContainerLowest)
            .clickable { onClick(device.id) }
            .border(BorderStroke(2.dp,colorScheme.outline), MaterialTheme.shapes.large)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconHero(
                backgroundSize = 96.dp,
                iconSize = 54.dp,
                icon = device.icon
            )
            Column(Modifier.weight(1f)) {
                val deviceReal = remember { deviceManager.getDevice(device.id) }
                Text(
                    fontSize = 42.sp,
                    lineHeight = 42.sp,
                    text = device.name,
                    fontFamily = font
                )
                if (deviceReal != null) {
                    val deviceHelper: DeviceHelper = koinInject()
                    val batteryString = deviceHelper.getBatterySubtitle(context, deviceReal)
                    if (batteryString != null) {
                        Text(
                            text = batteryString,
                            fontFamily = font
                        )
                    }
                }
            }
            Icon(
                modifier = Modifier.size(36.dp),
                painter = actionIcon,
                contentDescription = actionDescription,
                tint = colorScheme.primary
            )
        }
    }
}