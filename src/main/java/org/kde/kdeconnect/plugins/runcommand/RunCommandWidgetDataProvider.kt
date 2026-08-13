/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.plugins.runcommand

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect_tp.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class RunCommandWidgetDataProvider(private val context: Context, val intent: Intent?) : RemoteViewsFactory, KoinComponent {
    private val runCommandSettingsDataStore: RunCommandSettingsDataStore by inject()
    private val deviceManager: DeviceManager by inject()
    private var deviceId : String? = null
    private var widgetId : Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate() {
        widgetId = intent?.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            LoggerTagged.e { "RunCommandWidgetDataProvider: No widget id extra was set" }
            return
        }
        deviceId = runBlocking { runCommandSettingsDataStore.getWidgetDeviceId(widgetId).first() }
    }

    override fun onDataSetChanged() {
        deviceId = runBlocking { runCommandSettingsDataStore.getWidgetDeviceId(widgetId).first() }
    }

    override fun onDestroy() {}

    private fun getPlugin(): RunCommandPlugin? {
        return deviceManager.getDevicePlugin(deviceId, RunCommandPlugin::class.java)
    }

    override fun getCount(): Int {
        return getPlugin()?.commandList?.value?.size ?: 0
    }

    override fun getViewAt(i: Int): RemoteViews {
        val remoteView = RemoteViews(context.packageName, R.layout.list_item_entry)

        val plugin : RunCommandPlugin? = getPlugin()
        if (plugin == null) {
            // Either the deviceId was null, or the plugin is not available.
            if (deviceId != null) {
                LoggerTagged.e { "RunCommandWidgetDataProvider: Plugin not found" }
            }
            // Return a new, not-configured layout as a fallback
            return remoteView
        }

        val listItem = plugin.commandList.value.getOrNull(i) ?: return remoteView

        remoteView.setTextViewText(R.id.list_item_entry_title, listItem.name)
        remoteView.setTextViewText(R.id.list_item_entry_summary, listItem.command)
        remoteView.setViewVisibility(R.id.list_item_entry_summary, View.VISIBLE)

        val runCommandIntent = Intent(context, RunCommandWidgetProvider::class.java)
        runCommandIntent.action = RUN_COMMAND_ACTION
        runCommandIntent.putExtra(EXTRA_APPWIDGET_ID, widgetId)
        runCommandIntent.putExtra(TARGET_COMMAND, listItem.key)
        runCommandIntent.putExtra(TARGET_DEVICE, deviceId)
        remoteView.setOnClickFillInIntent(R.id.list_item_entry, runCommandIntent)

        return remoteView
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(i: Int): Long {
        return getPlugin()?.commandList?.value?.getOrNull(i)?.key?.hashCode()?.toLong() ?: 0
    }

    override fun hasStableIds(): Boolean {
        return false
    }
}

class CommandsRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return RunCommandWidgetDataProvider(this.applicationContext, intent)
    }
}

