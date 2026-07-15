/*
 * SPDX-FileCopyrightText: 2026 Saul Cintero Chocarro <scintero@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose.screen.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect.plugins.Plugin
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.KdeThemePreviews
import org.kde.kdeconnect_tp.R

@Composable
fun PluginsScreen(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    onUnpair: () -> Unit
) {
    PluginsScreenContent(
        pluginsWithButtons = pluginsWithButtons,
        onButtonClick = onButtonClick,
        onUnpair = onUnpair
    )
}

@Composable
private fun PluginsScreenContent(
    pluginsWithButtons: List<Plugin.PluginUiButton>,
    onButtonClick: (Plugin.PluginUiButton) -> Unit,
    onUnpair: () -> Unit
) {
    Column {
        val numColumns = LocalResources.current.getInteger(R.integer.plugins_columns)
        PluginButtons(
            buttons = pluginsWithButtons,
            numColumns = numColumns,
            onButtonClick = onButtonClick
        )
        Spacer(modifier = Modifier.padding(vertical = 6.dp))
        TextButton(onClick = onUnpair) {
            Text(stringResource(R.string.device_menu_unpair))
        }
    }
}

@Composable
private fun PluginButtons(
    buttons: List<Plugin.PluginUiButton>,
    numColumns: Int,
    onButtonClick: (Plugin.PluginUiButton) -> Unit
) {
    val (sendButtons, controlButtons) = buttons.partition {
        it.category == Plugin.ButtonCategory.SEND
    }

    Column {
        if (sendButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(R.string.category_send))
            PluginButtonsGrid(sendButtons, numColumns, onButtonClick)
        }
        if (controlButtons.isNotEmpty()) {
            CategoryTitleTextSmall(text = stringResource(R.string.category_control))
            PluginButtonsGrid(controlButtons, numColumns, onButtonClick)
        }
    }
}

@Composable
private fun PluginButtonsGrid(
    buttons: List<Plugin.PluginUiButton>,
    numColumns: Int,
    onButtonClick: (Plugin.PluginUiButton) -> Unit
) {
    Column {
        buttons.chunked(numColumns).forEach { rowButtons ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowButtons.forEach { button ->
                    PluginButton(
                        button = button,
                        modifier = Modifier.weight(1f),
                        onClick = { onButtonClick(button) }
                    )
                }
                repeat(numColumns - rowButtons.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginButton(
    modifier: Modifier,
    button: Plugin.PluginUiButton,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .clickable {onClick()},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = button.iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary
        )
        Text(
            text = button.name,
            maxLines = 2,
            fontSize = 16.sp,
            lineHeight = 18.sp,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@KdeThemePreviews
@Composable
private fun PluginsScreenPreview() {
    KdeTheme(context = LocalContext.current) {
        PluginsScreenContent(
            pluginsWithButtons = emptyList(),
            onButtonClick = { /* Do nothing */ },
            onUnpair = { /* Do nothing */ }
        )
    }
}
