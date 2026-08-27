/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.components

import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Portrait - Light",
    showBackground = true,
)
@Preview(
    name = "Portrait - Dark",
    showBackground = true,
)
annotation class KdePortraitThemePreviews

@Preview(
    name = "Portrait - Light",
    showBackground = true,
)
@Preview(
    name = "Landscape - Light",
    showBackground = true,
    device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
@Preview(
    name = "Portrait - Dark",
    showBackground = true,
)
@Preview(
    name = "Landscape - Dark",
    showBackground = true,
    device = "spec:width=411dp,height=891dp,dpi=420,isRound=false,chinSize=0dp,orientation=landscape"
)
annotation class KdeThemePreviews