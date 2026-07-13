/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.NotificationTogglePreference
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf


@Composable
fun PluginSettingsScreen(
    deviceId: String,
    viewModel: PluginSettingsViewModel = koinViewModel(key = "PluginSettingsViewModel_$deviceId") { parametersOf(deviceId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationSend = uiState.plugins.find { it.key == "NotificationsPlugin" }
    val notificationReceive = uiState.plugins.find { it.key == "ReceiveNotificationsPlugin" }
    val contacts = uiState.plugins.find { it.key == "ContactsPlugin" }
    val clipboard = uiState.plugins.find { it.key == "ClipboardPlugin" }
    val multimedia = uiState.plugins.find { it.key == "MprisReceiverPlugin" }
    HazeScaffold(
        title = uiState.deviceName,
        backButton = true,
    ) {
        CategoryTitleTextSmall(stringResource(R.string.notifications))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            notificationSend?.let { plugin ->
                NotificationTogglePreference(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.send),
                    icon = painterResource(R.drawable.arrow_upward),
                    value = plugin.isEnabled,
                    onValueChanged = { viewModel.setPluginEnabled(context, plugin.key, it) }
                )
            }
            notificationReceive?.let { plugin ->
                NotificationTogglePreference(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.receive),
                    icon = painterResource(R.drawable.arrow_downward),
                    value = plugin.isEnabled,
                    onValueChanged = { viewModel.setPluginEnabled(context, plugin.key, it) }
                )
            }
        }
        CategoryTitleTextSmall(stringResource(R.string.synchronization))
        clipboard?.let { plugin ->
            SwitchPreference(
                title = plugin.name,
                summary = plugin.description,
                icon = painterResource(R.drawable.assignment),
                value = plugin.isEnabled,
                onValueChanged = { viewModel.setPluginEnabled(context, plugin.key, it) }
            )
        }
        contacts?.let { plugin ->
            SwitchPreference(
                title = plugin.name,
                summary = plugin.description,
                icon = painterResource(R.drawable.contacts),
                value = plugin.isEnabled,
                onValueChanged = { viewModel.setPluginEnabled(context, plugin.key, it) }
            )
        }
        multimedia?.let { plugin ->
            SwitchPreference(
                title = plugin.name,
                summary = plugin.description,
                icon = painterResource(R.drawable.contacts),
                value = plugin.isEnabled,
                onValueChanged = { viewModel.setPluginEnabled(context, plugin.key, it) }
            )
        }
    }
}