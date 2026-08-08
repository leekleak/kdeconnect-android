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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.ui.list.DeviceItem
import org.kde.kdeconnect.ui.list.ListAdapter
import org.kde.kdeconnect_tp.databinding.WidgetRemoteCommandPluginDialogBinding
import org.koin.android.ext.android.inject

class RunCommandWidgetConfigActivity : AppCompatActivity() {
    private val deviceManager: DeviceManager by inject()
    private val runCommandSettingsDataStore: RunCommandSettingsDataStore by inject()

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

        val binding = WidgetRemoteCommandPluginDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pairedDevices = deviceManager.devices.value.values.filter(Device::isPaired)

        val list = ListAdapter(this, pairedDevices.map { DeviceItem(it, ::deviceClicked) })
        binding.runCommandsDeviceList.adapter = list
        binding.runCommandsDeviceList.emptyView = binding.noDevices
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
}

