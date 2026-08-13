/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.content.pm.PackageManager
import co.touchlab.kermit.Logger
import org.kde.kdeconnect_tp.R

object AppsHelper {
    @JvmStatic
    fun appNameLookup(context: Context, packageName: String): String {
        return try {
            val manager = context.packageManager
            val info = manager.getApplicationInfo(packageName, 0)

            manager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            LoggerTagged.e(e) { "Could not resolve name $packageName" }
            context.getString(R.string.unknown_app)
        }
    }
}
