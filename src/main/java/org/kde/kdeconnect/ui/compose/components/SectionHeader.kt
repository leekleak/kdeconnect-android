/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.viewpager2.widget.ViewPager2
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject

@Composable
fun SectionHeader(title: String) {
    KdeTitleMediumText(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            top = 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
    )
}

@KdePortraitThemePreviews
@Composable
private fun SectionHeaderPreview() {
    KdeTheme(context = LocalContext.current) {
        SectionHeader(title = stringResource(id = R.string.category_connected_devices))
    }
}

@Composable
fun HazeScaffold(
    title: String,
    paddingValues: PaddingValues?,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    hazeState: HazeState = rememberHazeState(),
    backButton: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    actions: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val paddingSide = paddingValues?.calculateLeftPadding(LayoutDirection.Ltr)
    val paddingTop = paddingValues?.calculateTopPadding()
    val paddingBottom = paddingValues?.calculateBottomPadding()

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .background(colorScheme.surface)
                .fillMaxSize()
                .hazeSource(hazeState)
                .padding(horizontal = paddingSide ?: 0.dp)
                .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier),
            verticalArrangement = verticalArrangement
        ) {
            paddingTop?.let { Spacer(Modifier.height((it - 8.dp).coerceAtLeast(0.dp))) }
            content()
            paddingBottom?.let { Spacer(Modifier.height((it - 8.dp).coerceAtLeast(0.dp))) }
        }
        PageTitle(backButton, hazeState, title, actions)
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun PageTitle(
    backButton: Boolean = false,
    hazeState: HazeState? = null,
    text: String,
    customElement: @Composable (BoxScope.() -> Unit)? = null,
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
        Box(Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 6.dp)
            .fillMaxWidth()) {
            CategoryTitleText(text, backButton)
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
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.bigscreen_back),
                )
            }
        }
        Text(
            modifier = Modifier.padding(8.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            text = text
        )
    }
}

@Composable
fun CategoryTitleSmallText(text: String) {
    Text(
        modifier = Modifier.padding(8.dp),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.tertiary
    )
}