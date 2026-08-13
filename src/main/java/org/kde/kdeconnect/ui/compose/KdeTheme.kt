/*
 * SPDX-FileCopyrightText: 2024 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.compose

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.ui.AppTheme.*
import org.koin.compose.koinInject

val LocalHazeState = compositionLocalOf { HazeState() }
@Composable
fun KdeTheme(dataStore: SettingsDataStore = koinInject(), content: @Composable () -> Unit) {
    val context = LocalContext.current

    val theme by dataStore.theme.collectAsState(runBlocking { dataStore.theme.first() })

    val colorScheme = when (theme) {
        Light -> getColorScheme(context, false)
        Dark -> getColorScheme(context, true)
        Default -> getColorScheme(context, isSystemInDarkTheme())
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

fun getColorScheme(context: Context, dark: Boolean): ColorScheme {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (dark) {
            true -> dynamicDarkColorScheme(context)
            false -> dynamicLightColorScheme(context)
        }
    } else {
        when (dark) {
            true -> darkColorScheme()
            false -> lightColorScheme()
        }
    }
}