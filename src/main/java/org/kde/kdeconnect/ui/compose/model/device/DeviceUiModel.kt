/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.model.device

import org.kde.kdeconnect_tp.R

data class DeviceUiModel(
    val id: String = "",
    val icon: Int = R.drawable.desktop_windows,
    val name: String = "",
    val summaryRes: Int = 0,
    val isReachable: Boolean = false,
    val isPaired: Boolean = false
)