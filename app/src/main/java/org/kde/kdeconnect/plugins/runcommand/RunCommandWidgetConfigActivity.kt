/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.runcommand

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.DeviceState
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.ui.KdeTheme
import org.kde.kdeconnect.ui.components.DeviceSelectScreen
import org.kde.kdeconnect_tp.R
import org.koin.android.ext.android.inject
import kotlin.time.Duration.Companion.milliseconds

class RunCommandWidgetConfigActivity : AppCompatActivity() {
    private val deviceManager: DeviceManager by inject()
    private val runCommandSettingsDataStore: RunCommandSettingsDataStore by inject()
    private val backgroundServiceData: BackgroundServiceData by inject()
    private val trustedNetworkHelper: TrustedNetworkHelper by inject()

    private var isRefreshing by mutableStateOf(value = false)
    private var deviceStates: StateFlow<List<DeviceState>> = deviceManager.allDeviceStatesMap.map { it.values.toList() }
    .stateIn(
        scope = lifecycleScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val wifiToTrusted: StateFlow<Pair<Boolean, Boolean>> =
        combine(
            backgroundServiceData.isConnectedToNonCellularNetwork,
            trustedNetworkHelper.isTrustedNetwork
        ) { nonCellular, trusted ->
            nonCellular to trusted
        }.stateIn(
        scope = lifecycleScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true to true
    )

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        setResult(RESULT_CANCELED) // Default result

        appWidgetId = intent.extras?.getInt(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)

        setContent {
            KdeTheme {
                val scope = rememberCoroutineScope()
                val devices by deviceStates.collectAsStateWithLifecycle()
                val wifiToTrustedValue by wifiToTrusted.collectAsStateWithLifecycle()
                DeviceSelectScreen(
                    devices = devices,
                    pageTitle = stringResource(R.string.select_device),
                    actionIcon = painterResource(R.drawable.check_circle),
                    actionDescription = stringResource(R.string.select),
                    isRefreshing = isRefreshing,
                    onDeviceClick = { deviceId ->
                        val device =
                            deviceManager.getDevice(id = deviceId) ?: return@DeviceSelectScreen
                        deviceClicked(device = device)
                    },
                    wifiAvailable = wifiToTrustedValue.first,
                    trustedNetwork = wifiToTrustedValue.second,
                    onRefresh = {
                        scope.launch {
                            refreshDevicesAction()
                        }
                    },
                )
            }
        }
    }

    fun deviceClicked(device: Device) {
        val deviceId = device.deviceId
        lifecycleScope.launch {
            runCommandSettingsDataStore.setWidgetDeviceId(appWidgetId, deviceId)

            val appWidgetManager = AppWidgetManager.getInstance(this@RunCommandWidgetConfigActivity)
            updateAppWidget(this@RunCommandWidgetConfigActivity, appWidgetManager, appWidgetId, runCommandSettingsDataStore, deviceManager)

            val resultValue = Intent()
            resultValue.putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }

    private suspend fun refreshDevicesAction() {
        isRefreshing = true

        BackgroundService.forceRefreshConnections(context = this)
        delay(1500.milliseconds)
        isRefreshing = false
    }
}

