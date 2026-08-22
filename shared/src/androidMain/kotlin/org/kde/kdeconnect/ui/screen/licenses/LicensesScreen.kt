package org.kde.kdeconnect.ui.screen.licenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.R
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.licenses
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.card
import org.kde.kdeconnect.ui.components.googleSans
import org.kde.kdeconnect.ui.navigation.Navigator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(navigator: Navigator) {
    val haptic = LocalHapticFeedback.current
    val libraries by produceLibraries(R.raw.aboutlibraries)

    var selectedLib: Library? by remember { mutableStateOf(null) }
    val font = googleSans()
    val fontHeavy = googleSans(weight = 600f)

    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    HazeScaffold(
        title = stringResource(Res.string.licenses),
        backAction = BackAction.Normal(navigator),
        scrollState = null,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = paddingValues
        ) {
            item { }
            libraries?.let { libs ->
                items(libs.libraries) { lib ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .card()
                            .clickable {
                                selectedLib = lib
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = lib.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = fontHeavy
                        )
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            lib.artifactVersion?.let {
                                Box(
                                    modifier = Modifier.fillMaxHeight()
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontFamily = googleSans(),
                                    )
                                }
                            }

                            lib.licenses.forEach { license ->
                                Text(
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.secondary)
                                        .padding(vertical = 4.dp, horizontal = 8.dp),
                                    text = license.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontFamily = googleSans(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (selectedLib != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedLib = null },
            contentWindowInsets = { WindowInsets() }
        ) {
            LazyColumn(
                contentPadding = remember {
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp + bottomPadding
                    )
                }
            ) {
                items(selectedLib?.licenses?.toList() ?: emptyList()) { license ->
                    license.licenseContent?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = font
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun LicensesScreenPreview() {
    LicensesScreen(Navigator())
}