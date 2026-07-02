/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.plugin

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.IconPreference
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


//Todo: Maybe don't hardcode strings
val validSettings = listOf(
    "NotificationsPlugin",
    "ReceiveNotificationsPlugin",
    "ContactsPlugin",
    "ClipboardPlugin"
)
@Composable
fun PluginSettingsScreen(
    deviceId: String,
    viewModel: PluginSettingsViewModel = koinViewModel { parametersOf(deviceId) },
    onNavigateToPluginIndividualSettings: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = uiState.deviceName,
        backButton = true,
    ) {
        uiState.plugins.filter { validSettings.contains(it.key) }.forEach { plugin ->
            if (plugin.hasSettings) {
                SplitPluginPreference(
                    plugin = plugin,
                    onPluginToggled = {  },
                    onSettingsClicked = { onNavigateToPluginIndividualSettings(plugin.key) }
                )
            } else {
                SwitchPreference(
                    title = plugin.name,
                    summary = plugin.description,
                    value = true,
                    onValueChanged = { }
                )
            }
        }
    }
}

@Composable
private fun SplitPluginPreference(
    plugin: PluginSettingsItem,
    onPluginToggled: (Boolean) -> Unit,
    onSettingsClicked: () -> Unit
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Preference(
            modifier = Modifier.weight(1f),
            title = plugin.name,
            summary = plugin.description,
            controls = {
                Switch(
                    checked = true,
                    onCheckedChange = onPluginToggled
                )
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconPreference(
            title = stringResource(R.string.settings),
            painter = painterResource(R.drawable.ic_settings_24dp),
            onClick = onSettingsClicked
        )
    }
}
