/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.extensions.device

import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect_tp.R

fun Device.toUiModel() = DeviceUiModel(
    id = deviceId,
    icon = iconDrawable,
    name = name,
    summaryRes = if (compareProtocolVersion() > 0) R.string.protocol_version_newer else 0,
    isReachable = isReachable,
    pairState = pairingHandler.state.value,
    batteryInfo = state.value.batteryInfo
)

fun DeviceState.toUiModel() = DeviceUiModel(
    id = deviceInfo.id,
    icon = deviceInfo.type.toDrawableId(),
    name = deviceInfo.name,
    summaryRes = 0,
    isReachable = isReachable,
    pairState = pairStatus,
    batteryInfo = batteryInfo
)