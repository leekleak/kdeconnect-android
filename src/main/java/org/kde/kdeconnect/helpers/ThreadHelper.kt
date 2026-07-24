/*
 * SPDX-FileCopyrightText: 2025 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.helpers

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ThreadHelper {

    private val executor: ExecutorService = Executors.newCachedThreadPool()

    @JvmStatic
    @Deprecated("Should use Kotlin Coroutines where possible")
    fun execute(command: Runnable) = executor.execute(command)
}
