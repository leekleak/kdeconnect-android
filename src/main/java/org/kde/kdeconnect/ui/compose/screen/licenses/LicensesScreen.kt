/*
 * SPDX-FileCopyrightText: 2026 Tanish Ranjan <tanishranjan4@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.licenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
fun LicensesScreen(
    eventFlow: SharedFlow<LicensesEvent>,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val resources = LocalResources.current
    var licenseChunks by remember { mutableStateOf<List<String>>(emptyList()) }

    val listState = rememberLazyListState()

    LaunchedEffect(resources) {
        withContext(Dispatchers.IO) {
            val chunks = resources
                .openRawResource(R.raw.license)
                .bufferedReader()
                .use { it.readText() }
                .split("-".repeat(80))
                .filter { it.isNotBlank() }
            licenseChunks = chunks
        }
    }

    LaunchedEffect(Unit) {
        eventFlow.collect { event ->
            when (event) {
                is LicensesEvent.ScrollToTop -> listState.animateScrollToItem(0)
                is LicensesEvent.ScrollToBottom -> {
                    if (licenseChunks.isNotEmpty()) {
                        listState.animateScrollToItem(licenseChunks.size - 1)
                    }
                }
            }
        }
    }

    HazeScaffold(
        title = stringResource(id = R.string.licenses),
        scrollState = null,
        backButton = true,
        actions = actions
    ) { paddingValues ->
        if (licenseChunks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues
            ) {
                itemsIndexed(
                    items = licenseChunks,
                    key = { index, _ -> index }
                ) { index, chunk ->
                    Text(
                        text = chunk.trim(),
                        color = MaterialTheme.colorScheme.onBackground,
                    )

                    if (index < licenseChunks.size - 1) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@KdeThemePreviews
@Composable
private fun LicensesScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        LicensesScreen(eventFlow = remember { MutableSharedFlow() })
    }
}
