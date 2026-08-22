/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.PairState
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.arrow_forward_ios
import org.kde.kdeconnect.generated.resources.assignment
import org.kde.kdeconnect.generated.resources.battery_1_bar
import org.kde.kdeconnect.generated.resources.battery_2_bar
import org.kde.kdeconnect.generated.resources.battery_3_bar
import org.kde.kdeconnect.generated.resources.battery_4_bar
import org.kde.kdeconnect.generated.resources.battery_5_bar
import org.kde.kdeconnect.generated.resources.battery_6_bar
import org.kde.kdeconnect.generated.resources.battery_charging_20
import org.kde.kdeconnect.generated.resources.battery_charging_30
import org.kde.kdeconnect.generated.resources.battery_charging_50
import org.kde.kdeconnect.generated.resources.battery_charging_60
import org.kde.kdeconnect.generated.resources.battery_charging_80
import org.kde.kdeconnect.generated.resources.battery_charging_90
import org.kde.kdeconnect.generated.resources.battery_charging_full
import org.kde.kdeconnect.generated.resources.battery_full
import org.kde.kdeconnect.generated.resources.charging
import org.kde.kdeconnect.generated.resources.clipboard
import org.kde.kdeconnect.generated.resources.music_cast
import org.kde.kdeconnect.generated.resources.open
import org.kde.kdeconnect.generated.resources.open_mpris_controls
import org.kde.kdeconnect.generated.resources.send_clipboard
import org.kde.kdeconnect.generated.resources.shortcuts
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.kde.kdeconnect.ui.navigation.Navigator

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
    device: DeviceState,
    navigator: Navigator?,
    shortcuts: List<PluginUiButton> = emptyList(),
    actionIcon: Painter = painterResource(Res.drawable.arrow_forward_ios),
    actionDescription: String = stringResource(Res.string.open),
    actionDescriptionVisible: Boolean = false,
    onClick: (String) -> Unit
) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val font = googleSans(weight = 600f)

    @Composable
    fun action() {
        Icon(
            painter = actionIcon,
            contentDescription = if (actionDescriptionVisible) null else actionDescription,
        )
        if (actionDescriptionVisible) {
            Text(
                text = actionDescription,
                fontFamily = font
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colorScheme.surfaceContainerLowest)
            .border(BorderStroke(1.dp, colorScheme.outline), MaterialTheme.shapes.large)
    ) {
        Box {
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                device.links.forEach { link ->
                    Icon(painterResource(link.linkProvider.icon), link.linkProvider.name)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(device.deviceInfo.id) }
                    .padding(16.dp)
            ) {
                Icon(
                    modifier = Modifier.size(40.dp),
                    painter = painterResource(device.deviceInfo.type.toDrawableRes()),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.fillMaxWidth(0.7f),
                    fontSize = 42.sp,
                    lineHeight = 42.sp,
                    text = device.deviceInfo.name,
                    fontFamily = font
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    device.batteryInfo?.let { battery ->
                        BatteryComponent(battery)
                    }
                    Spacer(Modifier.weight(1f))
                    action()
                }
            }
        }

        if (shortcuts.isNotEmpty() && navigator != null) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 12.dp)) {
                HorizontalDivider(
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 8.dp)
                )
                CategoryTitleTextSmall(stringResource(Res.string.shortcuts))
                Spacer(Modifier.height(8.dp))
                PluginButtonsGrid(shortcuts, fullName = true) { button -> activity?.let { scope.launch { button.onClick(it, navigator) } } }
            }
        }
    }
}

@Composable
fun BatteryComponent(battery: DeviceBatteryInfo) {
    val font = googleSans(weight = 600f)
    Row(
        modifier = Modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val icon = if (battery.isCharging) {
            when (battery.currentCharge) {
                in 20..30 -> Res.drawable.battery_charging_20
                in 30..50 -> Res.drawable.battery_charging_30
                in 50..60 -> Res.drawable.battery_charging_50
                in 60..80 -> Res.drawable.battery_charging_60
                in 80..90 -> Res.drawable.battery_charging_80
                in 90..99 -> Res.drawable.battery_charging_90
                else -> Res.drawable.battery_charging_full
            }
        } else {
            when (battery.currentCharge) {
                in 20..30 -> Res.drawable.battery_1_bar
                in 30..50 -> Res.drawable.battery_2_bar
                in 50..60 -> Res.drawable.battery_3_bar
                in 60..80 -> Res.drawable.battery_4_bar
                in 80..90 -> Res.drawable.battery_5_bar
                in 90..99 -> Res.drawable.battery_6_bar
                else -> Res.drawable.battery_full
            }
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = if (battery.isCharging) stringResource(Res.string.charging) else null
        )
        Text(
            text = "${battery.currentCharge}%",
            fontFamily = font
        )
    }
}

@Composable
@Preview
fun DeviceCardPreview() {
    DeviceCard(
        device = DeviceState(
            deviceInfo = DeviceInfo(
                    id = "",
                    certificate = ByteArray(0
                ),
                name = "Name",
                type = DeviceType.DESKTOP
            ),
            pairState = PairState.Paired,
            batteryInfo = DeviceBatteryInfo(70, true, 15),
        ),
        navigator = Navigator(),
        shortcuts = listOf(
            PluginUiButton("", Res.string.clipboard, Res.string.send_clipboard, Res.drawable.assignment, ButtonCategory.SEND) { _, _ -> },
            PluginUiButton("", Res.string.open_mpris_controls, Res.string.open_mpris_controls, Res.drawable.music_cast, ButtonCategory.CONTROL) { _, _ -> }
        ),
        onClick = { }
    )
}

@Composable
@Preview
fun DeviceCardPreviewEmpty() {
    DeviceCard(
        device = DeviceState(
            deviceInfo = DeviceInfo(
                id = "",
                certificate = ByteArray(0
                ),
                name = "Name",
                type = DeviceType.DESKTOP
            ),
            pairState = PairState.Paired,
            batteryInfo = null,
        ),
        navigator = Navigator(),
        onClick = { }
    )
}