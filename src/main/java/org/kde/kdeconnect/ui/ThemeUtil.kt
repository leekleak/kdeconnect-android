/*
 * SPDX-FileCopyrightText: 2018 Philip Cohn-Cort <cliabhach@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.ui

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import org.kde.kdeconnect.datastore.SettingsDataStore

/**
 * Utilities for working with android [Themes][android.content.res.Resources.Theme].
 */
class ThemeUtil(private val dataStore: SettingsDataStore) {

    //Todo: Fix this as now the app thinks that the default theme is whatever theme was set when MainActivity was launched.
    fun applyTheme(themePref: AppTheme) {
        when (themePref) {
            AppTheme.Light -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            AppTheme.Dark -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }

            else -> {
                if (themePref == AppTheme.Default) {
                    Log.d("ThemeUtil", "Theme preference not set, using system default.")
                } else {
                    Log.w("ThemeUtil", "Unknown theme preference: $themePref, falling back to system default.")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                }
            }
        }
    }

    /**
     * Called when an activity is created for the first time to reliably load the correct theme.
     */
    fun setUserPreferredTheme(application: Application) {
        val appTheme = dataStore.getThemeBlocking()
        DynamicColors.applyToActivitiesIfAvailable(application)
        applyTheme(appTheme)
    }
}

enum class AppTheme {
    Default,
    Light,
    Dark,
}
