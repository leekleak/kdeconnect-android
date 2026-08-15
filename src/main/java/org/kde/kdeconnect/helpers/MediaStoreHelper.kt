/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri

object MediaStoreHelper {
    @JvmStatic
    fun indexFile(context: Context, path: Uri?) {
        val uriPath = path?.path ?: return
        MediaScannerConnection.scanFile(context, arrayOf(uriPath), null, null)
    }
}
