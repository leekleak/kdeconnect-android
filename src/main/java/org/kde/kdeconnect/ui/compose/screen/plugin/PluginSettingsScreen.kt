/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.plugin

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PluginIndividualSettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PluginSettingsScreen(
    deviceId: String,
    viewModel: PluginSettingsViewModel = koinViewModel(key = "PluginSettingsViewModel_$deviceId") { parametersOf(deviceId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator: Navigator = koinInject()

    HazeScaffold(
        title = uiState.deviceName,
        backButton = true,
    ) {
        uiState.plugins.forEach { plugin ->
            Preference(
                title = plugin.name,
                summary = plugin.description,
                onClick = { viewModel.setPluginEnabled(plugin.key, !plugin.isEnabled) },
                controls = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (plugin.hasSettings) {
                            IconButton(onClick = {
                                navigator.goTo(PluginIndividualSettingsKey(plugin.key))
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_settings_24dp),
                                    contentDescription = stringResource(R.string.settings)
                                )
                            }
                        }
                        Switch(
                            checked = plugin.isEnabled,
                            onCheckedChange = { viewModel.setPluginEnabled(plugin.key, it) }
                        )
                    }
                }
            )
        }
    }
}
