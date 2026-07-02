/*
 * SPDX-FileCopyrightText: 2021 Maxim Leshchenko <cnmaks90@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.ui.about

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesEvent
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesScreen
import org.kde.kdeconnect_tp.R

class LicensesActivity : AppCompatActivity() {

    private val scrollEvents = MutableSharedFlow<LicensesEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            KdeTheme(this) {
                val scope = rememberCoroutineScope()
                LicensesScreen(
                    eventFlow = scrollEvents,
                    actions = {
                        Row {
                            IconButton(onClick = {
                                scope.launch {
                                    scrollEvents.emit(LicensesEvent.ScrollToTop)
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_upward_black_24dp),
                                    contentDescription = "Scroll to top"
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    scrollEvents.emit(LicensesEvent.ScrollToBottom)
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_arrow_downward_black_24dp),
                                    contentDescription = "Scroll to bottom"
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
