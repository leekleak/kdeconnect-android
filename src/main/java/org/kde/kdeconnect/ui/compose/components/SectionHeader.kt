/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject

@Composable
fun SectionHeader(title: String) {
    CategoryTitleTextSmall(title)
}

@KdePortraitThemePreviews
@Composable
private fun SectionHeaderPreview() {
    SectionHeader(title = stringResource(id = R.string.category_connected_devices))
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun PageTitle(
    backButton: Boolean = false,
    hazeState: HazeState? = null,
    text: String?,
    customElement: @Composable (RowScope.() -> Unit)? = null,
){
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                hazeState?.let {
                    Modifier.hazeEffect(state = it, style = HazeMaterials.ultraThin()) {
                        progressive =
                            HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
                    }
                } ?: Modifier
            )
    ) {
        Row(Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .fillMaxWidth()
        ) {
            text?.let { CategoryTitleText(it, backButton) }
            Spacer(Modifier.weight(1f))
            customElement?.let { it() }
        }
    }
}

val TOP_BAR_HEIGHT: Dp = 52.dp
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryTitleText(text: String, backButton: Boolean = false) {
    val navigator: Navigator = koinInject()
    Row (modifier = Modifier.height(TOP_BAR_HEIGHT), verticalAlignment = Alignment.CenterVertically){
        if (backButton) {
            IconButton(onClick = { navigator.goBack() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back_ios_new),
                    contentDescription = stringResource(R.string.bigscreen_back),
                )
            }
        }
        Text(
            modifier = Modifier.padding(8.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            overflow = TextOverflow.Ellipsis,
            text = text
        )
    }
}

@Composable
fun CategoryTitleTextSmall(text: String) {
    Text(
        modifier = Modifier.padding(horizontal = 8.dp),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.tertiary
    )
}