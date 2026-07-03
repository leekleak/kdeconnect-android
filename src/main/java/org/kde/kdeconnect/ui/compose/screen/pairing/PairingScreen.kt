/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.pairing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeBodyMediumText
import org.kde.kdeconnect.ui.compose.components.KdeBodySmallText
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect.ui.compose.components.SectionHeader
import org.kde.kdeconnect.ui.compose.components.googleSans
import org.kde.kdeconnect.ui.compose.components.px
import org.kde.kdeconnect.ui.compose.components.roundedShapes
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    uiState: PairingUiState,
    onClick: (String) -> Unit,
    onWifiSettingsClick: () -> Unit = {},
    onNotificationSettingsClick: () -> Unit = {},
    onDuplicateNamesClick: () -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val lazyListState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    val navigator: Navigator = koinInject()
    var showPairedSheet by remember { mutableStateOf(false) }
    val pairedDevices by remember { derivedStateOf { uiState.available.size + uiState.remembered.size } }

    HazeScaffold(
        title = stringResource(R.string.kde_connect_short),
        scrollState = null,
        actions = {
            IconButton(
                onClick = { navigator.goTo(SettingsKey) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_24dp),
                    contentDescription = stringResource(id = R.string.open)
                )
            }
        }
    ) {paddingValues ->
        if (showPairedSheet) {
            ModalBottomSheet(onDismissRequest = {showPairedSheet = false}) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                    state = lazyListState
                ) {
                    // Available devices
                    if (uiState.available.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(id = R.string.category_not_paired_devices))
                        }
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        itemsIndexed(
                            items = uiState.available,
                            key = { _, availableDevice -> availableDevice.id }) { _, availableDevice ->
                            DeviceCard (
                                device = availableDevice,
                                onClick = onClick
                            )
                        }
                    }

                    // Remembered devices
                    if (uiState.remembered.isNotEmpty()) {
                        item {
                            SectionHeader(title = stringResource(id = R.string.category_remembered_devices))
                        }
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        itemsIndexed(
                            items = uiState.remembered,
                            key = { _, rememberedDevice -> rememberedDevice.id }) { _, rememberedDevice ->
                            DeviceCard (
                                device = rememberedDevice,
                                onClick = onClick
                            )
                        }
                    }
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                )
            }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
                state = lazyListState
            ) {
                // Explanations
                item {
                    PairingExplanations(
                        uiState = uiState,
                        onWifiSettingsClick = onWifiSettingsClick,
                        onNotificationSettingsClick = onNotificationSettingsClick,
                        onDuplicateNamesClick = onDuplicateNamesClick
                    )
                }

                // Connected devices
                item {
                    SectionHeader(stringResource(R.string.category_connected_devices))
                }
                if (uiState.connected.isEmpty()) {
                    item {
                        EmptyPlaceholder()
                    }
                } else {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    itemsIndexed(
                        items = uiState.connected,
                        key = { _, connectedDevice -> connectedDevice.id }) { _, connectedDevice ->
                        DeviceCard (
                            device = connectedDevice,
                            onClick = onClick
                        )
                    }
                }
            }
            if (pairedDevices != 0) {
                Button(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    onClick = { showPairedSheet = true }
                ) {
                    Text(text = "$pairedDevices Paired devices")
                }
            }
        }
    }
}

@Composable
private fun PairingExplanations(
    uiState: PairingUiState,
    onWifiSettingsClick: () -> Unit,
    onNotificationSettingsClick: () -> Unit,
    onDuplicateNamesClick: () -> Unit
) {
    Column {
        if (uiState.hasDuplicateNames) {
            DuplicateNamesWarning(onClick = onDuplicateNamesClick)
        }

        val someDevicesReachable = uiState.available.isNotEmpty() || uiState.connected.isNotEmpty()

        if (someDevicesReachable || uiState.isWifiAvailable) {
            if (!uiState.hasNotificationsPermission) {
                PairingExplanationRow(
                    text = stringResource(R.string.no_notifications),
                    icon = R.drawable.ic_warning,
                    onClick = onNotificationSettingsClick
                )
            } else if (uiState.isTrustedNetwork) {
                PairingExplanationRow(text = stringResource(R.string.pairing_description))
            } else {
                PairingExplanationRow(
                    text = stringResource(R.string.on_non_trusted_message),
                    icon = R.drawable.ic_warning
                )
            }
        } else {
            PairingExplanationRow(
                text = stringResource(R.string.no_wifi),
                icon = R.drawable.ic_wifi,
                onClick = onWifiSettingsClick
            )
        }
    }
}

@Composable
fun PairingExplanationRow(
    text: String, icon: Int? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = colorScheme.onSurfaceVariant)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        KdeBodyMediumText(
            text = text,
            onClick = { onClick?.invoke() }
        )
    }
}

@Composable
fun DuplicateNamesWarning(
    onClick: () -> Unit
) {
    PairingExplanationRow(
        text = stringResource(id = R.string.pairing_duplicate_names),
        icon = R.drawable.ic_warning,
        onClick = onClick
    )
}

@Composable
fun EmptyPlaceholder() {
    KdeBodySmallText(
        text = stringResource(R.string.device_list_empty),
        modifier = Modifier.padding(10.dp),
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceCard(
    device: DeviceUiModel,
    onClick: (String) -> Unit
) {
    val context = LocalContext.current
    val width = 2.dp.px
    val dashLength = 8.dp.px
    val cornerRadius = 16.dp.px
    val outlineColor = colorScheme.outline
    val backgroundShape = roundedShapes.random().toPath()
    val backgroundColor = colorScheme.primary
    val backgroundSize = 96.dp
    val backgroundSizePx = backgroundSize.px
    val backgroundShapeTransformed = remember(backgroundSizePx) {
        val matrix = Matrix().apply { scale(backgroundSizePx, backgroundSizePx) }
        backgroundShape.copy().apply { transform(matrix) }
    }
    val stroke = remember {
        Stroke(
            width = width,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength), 0f)
        )
    }
    val font = remember { googleSans(weight = 600f) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = outlineColor,
                    style = stroke,
                    cornerRadius = CornerRadius(cornerRadius),
                )
            }
    ) {
        Row(
            modifier = Modifier
                .height(128.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(backgroundSize)
                    .drawBehind {
                        drawPath(backgroundShapeTransformed, backgroundColor)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(54.dp),
                    painter = painterResource(device.icon),
                    contentDescription = null,
                    tint = colorScheme.onPrimary
                )
            }
            Column {
                val deviceReal = remember { KdeConnect.getInstance().getDevice(device.id) }
                Text(
                    fontSize = 42.sp,
                    text = device.name,
                    fontFamily = font
                )
                if (deviceReal != null) {
                    val batteryString = DeviceHelper.getBatterySubtitle(context, deviceReal)
                    if (batteryString != null) {
                        Text(
                            text = batteryString,
                            fontFamily = font
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton (
                modifier = Modifier
                    .fillMaxHeight()
                    .background(colorScheme.primary, shape = shapes.large)
                    .width(38.dp),
                onClick = { onClick(device.id) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = stringResource(R.string.open),
                    tint = colorScheme.onPrimary
                )
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun PreviewCompose() {
    KdeTheme(context = LocalContext.current) {
        PairingScreen(
            uiState = PairingUiState(
                isWifiAvailable = true,
                hasNotificationsPermission = true,
                isTrustedNetwork = true,
                hasDuplicateNames = true,
                connected = emptyList(),
                available = listOf(
                    DeviceUiModel(
                        id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd23_",
                        icon = R.drawable.ic_device_laptop_32dp,
                        name = "Device 1",
                        summaryRes = 0,
                        isReachable = true,
                        isPaired = false
                    ), DeviceUiModel(
                        id = "_2504584b_6aa2_3cd6_bd1b_5e958aa6cd24_",
                        icon = R.drawable.ic_device_desktop_32dp,
                        name = "Device 2",
                        summaryRes = R.string.protocol_version_newer,
                        isReachable = true,
                        isPaired = false
                    )
                ),
                remembered = emptyList(),
                isRefreshing = false
            ),
            onClick = { /* Do nothing */ },
            onWifiSettingsClick = { /* Do nothing */ },
            onNotificationSettingsClick = { /* Do nothing */ },
            onDuplicateNamesClick = { /* Do nothing */ },
            onRefresh = { /* Do nothing */ }
        )
    }
}
