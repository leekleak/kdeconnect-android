/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.components

import android.annotation.SuppressLint
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    CategoryTitleTextSmall(title)
}

@KdePortraitThemePreviews
@Composable
private fun SectionHeaderPreview() {
    KdeTheme(context = LocalContext.current) {
        SectionHeader(title = stringResource(id = R.string.category_connected_devices))
    }
}

/**
 * Haze scaffold provides a top bar with a title and a back button.
 *
 * The default configuration automatically aligns items in a scrollable column meaning that for most
 * use cases it should be enough. In that case just provide the items, and they'll be arranged automatically.
 *
 * In case the content contains a LazyColumn though, it will crash due to the fact that the scrollable
 * internal column measures infinite height. In that case make sure to override scrollState to null.
 *
 * @param title The title of the page.
 * @param modifier The modifier to be applied to the scaffold.
 * @param scrollState The scroll state of the scaffold. IMPORTANT: Should be null if content contains LazyColumn
 * @param hazeState The haze state of the scaffold.
 * @param backButton Whether to show the back button.
 * @param verticalArrangement The vertical arrangement of the content.
 * @param actions The actions to be displayed in the top bar.
 * @param content The content of the scaffold.
 */

@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun HazeScaffold(
    title: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState? = rememberScrollState(),
    hazeState: HazeState = rememberHazeState(),
    backButton: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.(PaddingValues) -> Unit,
) {
    val paddingSide = 16.dp
    val paddingTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TOP_BAR_HEIGHT + 6.dp
    val paddingBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
    val paddingValues = if (scrollState != null) PaddingValues() else PaddingValues(paddingSide, paddingTop, paddingSide, paddingBottom)
    Scaffold(
        contentWindowInsets = WindowInsets()
    ) {
        Box(modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .background(colorScheme.surface)
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .padding(horizontal = if (scrollState != null) paddingSide else 0.dp)
                    .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier),
                verticalArrangement = verticalArrangement
            ) {
                if (scrollState != null) Spacer(Modifier.height((paddingTop).coerceAtLeast(0.dp)))
                content(paddingValues)
                if (scrollState != null) Spacer(Modifier.height((paddingBottom).coerceAtLeast(0.dp)))
            }
            PageTitle(backButton, hazeState, title, actions)
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun PageTitle(
    backButton: Boolean = false,
    hazeState: HazeState? = null,
    text: String,
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
            CategoryTitleText(text, backButton)
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
                    painter = painterResource(R.drawable.arrow_back),
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
        modifier = Modifier.padding(8.dp),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.tertiary
    )
}