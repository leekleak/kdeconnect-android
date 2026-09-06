package org.kde.kdeconnect.ui.components

import android.net.Network
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.PairState
import org.kde.kdeconnect.backends.BaseLink
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.plugins.ButtonCategory
import org.kde.kdeconnect.plugins.PluginUiButton
import org.kde.kdeconnect.plugins.battery.DeviceBatteryInfo
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R

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
    actionIcon: Painter = painterResource(R.drawable.arrow_forward_ios),
    actionDescription: String = stringResource(R.string.open),
    actionDescriptionVisible: Boolean = false,
    onClick: (String) -> Unit
) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val font = remember { googleSans(weight = 600f) }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(device.deviceInfo.id) }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ){
                Icon(
                    modifier = Modifier.size(40.dp),
                    painter = painterResource(device.deviceInfo.type.toDrawableRes()),
                    contentDescription = null
                )
                Text(
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                    text = device.deviceInfo.name,
                    fontFamily = font
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (device.links.isNotEmpty()) {
                    Row(
                        modifier = Modifier.background(
                            colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.extraLarge
                        )
                            .widthIn(min = 40.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        device.links.forEach { link ->
                            Icon(
                                painter = painterResource(link.linkProvider.icon),
                                contentDescription = link.linkProvider.name,
                            )
                        }
                    }
                }
                device.batteryInfo?.let { battery ->
                    Box(
                        modifier = Modifier.background(
                            colorScheme.surfaceContainerHigh,
                            MaterialTheme.shapes.extraLarge
                        ).padding(horizontal = 8.dp),
                    ) {
                        BatteryComponent(battery)
                    }
                }
                Spacer(Modifier.weight(1f))
                action()
            }
        }

        if (shortcuts.isNotEmpty() && navigator != null) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 12.dp)) {
                HorizontalDivider(
                    Modifier.padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 8.dp)
                )
                CategoryTitleTextSmall(stringResource(R.string.shortcuts))
                Spacer(Modifier.height(8.dp))
                PluginButtonsGrid(shortcuts, fullName = true) { button -> activity?.let { scope.launch { button.onClick(it, navigator) } } }
            }
        }
    }
}

@Composable
fun BatteryComponent(battery: DeviceBatteryInfo) {
    val font = remember { googleSans(weight = 600f) }
    Row(
        modifier = Modifier.height(36.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val icon = if (battery.isCharging) {
            when (battery.currentCharge) {
                in 20..30 -> R.drawable.battery_charging_20
                in 30..50 -> R.drawable.battery_charging_30
                in 50..60 -> R.drawable.battery_charging_50
                in 60..80 -> R.drawable.battery_charging_60
                in 80..90 -> R.drawable.battery_charging_80
                in 90..99 -> R.drawable.battery_charging_90
                else -> R.drawable.battery_charging_full
            }
        } else {
            when (battery.currentCharge) {
                in 20..30 -> R.drawable.battery_1_bar
                in 30..50 -> R.drawable.battery_2_bar
                in 50..60 -> R.drawable.battery_3_bar
                in 60..80 -> R.drawable.battery_4_bar
                in 80..90 -> R.drawable.battery_5_bar
                in 90..99 -> R.drawable.battery_6_bar
                else -> R.drawable.battery_full
            }
        }
        Icon(
            painter = painterResource(icon),
            contentDescription = if (battery.isCharging) stringResource(R.string.charging) else null
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
    val deviceInfo = DeviceInfo(
        id = "",
        certificate = ByteArray(0),
        name = "Name",
        type = DeviceType.DESKTOP
    )
    DeviceCard(
        device = DeviceState(
            deviceInfo = deviceInfo,
            pairState = PairState.Paired,
            batteryInfo = DeviceBatteryInfo(70, true, 15),
            links = listOf(
                fakeProvider(deviceInfo, R.drawable.wifi),
                fakeProvider(deviceInfo, R.drawable.bluetooth)
            )
        ),
        navigator = Navigator(),
        shortcuts = listOf(
            PluginUiButton("", R.string.clipboard, R.string.send_clipboard, R.drawable.assignment, ButtonCategory.SEND) { _, _ -> },
            PluginUiButton("", R.string.open_mpris_controls, R.string.open_mpris_controls, R.drawable.music_cast, ButtonCategory.CONTROL) { _, _ -> }
        ),
        onClick = { }
    )
}

private fun fakeProvider(deviceInfo: DeviceInfo, icon: Int): BaseLink = object : BaseLink(
    object : BaseLinkProvider() {
        override suspend fun onStart() {}
        override fun onStop() {}
        override suspend fun onNetworkChange(network: Network?) {}
        override val name: String get() = "FakeProvider"
        override val icon: Int get() = icon
        override val priority: Int = 0
    }
) {
    override val name: String get() = "LanLink"
    override val deviceInfo: DeviceInfo get() = deviceInfo
    override suspend fun sendPacket(
        np: NetworkPacket,
        callback: Device.SendPacketStatusCallback
    ): Boolean = true
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