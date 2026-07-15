/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.share

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.compose.extensions.device.toUiModel
import org.kde.kdeconnect.ui.compose.model.device.DeviceUiModel
import org.kde.kdeconnect.ui.compose.screen.share.ShareScreen
import org.kde.kdeconnect_tp.R
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit

class ShareActivity : AppCompatActivity() {

    private lateinit var mSharedPrefs: SharedPreferences

    private var isRefreshing by mutableStateOf(value = false)
    private var uiDevices by mutableStateOf<List<DeviceUiModel>>(value = emptyList())
    private var intentHasUrl by mutableStateOf(value = false)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.refresh, menu)
        return true
    }

    private suspend fun refreshDevicesAction() {
        isRefreshing = true

        BackgroundService.forceRefreshConnections(context = this)
        delay(1500.milliseconds)
        isRefreshing = false
    }

    private fun updateDeviceList() {
        val intent = intent
        val action = intent.action
        if (Intent.ACTION_SEND != action && Intent.ACTION_SEND_MULTIPLE != action) {
            finish()
            return
        }
        val devices = KdeConnect.getInstance().devices.values
        this.intentHasUrl = doesIntentContainUrl(intent)
        this.uiDevices = devices
            .filter { device -> device.isPaired && (intentHasUrl || device.isReachable) }
            .map { it.toUiModel() }
    }

    private fun deviceClicked(
        device: Device,
        intentHasUrl: Boolean,
        intent: Intent
    ) {
        val plugin: SharePlugin? =
            KdeConnect.getInstance().getDevicePlugin(
                deviceId = device.deviceId,
                pluginClass = SharePlugin::class.java
            )
        if (intentHasUrl && !device.isReachable) {
            // Store the URL to be delivered once device becomes online
            storeUrlForFutureDelivery(
                device = device,
                url = intent.getStringExtra(Intent.EXTRA_TEXT)
            )
        } else {
            plugin?.share(intent)
        }
        finish()
    }

    private fun doesIntentContainUrl(intent: Intent?): Boolean {
        intent?.extras?.let { extras ->
            val url = extras.getString(Intent.EXTRA_TEXT)
            return URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)
        }
        return false
    }

    private fun storeUrlForFutureDelivery(
        device: Device,
        url: String?
    ) {
        val key = KEY_UNREACHABLE_URL_LIST + device.deviceId
        val oldUrlSet = mSharedPrefs.getStringSet(key, null)
        // According to the API docs, we should not directly modify the set returned above
        val newUrlSet = mutableSetOf<String>()
        url?.let { urlSet -> newUrlSet.add(urlSet) }
        if (oldUrlSet != null) {
            newUrlSet.addAll(oldUrlSet)
        }

        mSharedPrefs.edit { putStringSet(key, newUrlSet) }
        Toast.makeText(this, getString(R.string.unreachable_share_toast), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        setContent {
            KdeTheme(this) {
                val scope = rememberCoroutineScope()
                ShareScreen(
                    devices = uiDevices,
                    intentHasUrl = intentHasUrl,
                    isRefreshing = isRefreshing,
                    onDeviceClick = { deviceId ->
                        val device = KdeConnect.getInstance().getDevice(id = deviceId)
                            ?: return@ShareScreen
                        deviceClicked(
                            device = device,
                            intentHasUrl = intentHasUrl,
                            intent = intent
                        )
                    },
                    onRefresh = {
                        scope.launch {
                            refreshDevicesAction()
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val intent = intent
        var deviceId = intent.getStringExtra("deviceId")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID)
        }

        if (deviceId != null) {
            val plugin: SharePlugin? =
                KdeConnect.getInstance().getDevicePlugin(deviceId, SharePlugin::class.java)
            if (plugin != null) {
                plugin.share(intent)
            } else {
                val extras = intent.extras
                if (extras != null && extras.containsKey(Intent.EXTRA_TEXT)) {
                    val device = KdeConnect.getInstance().getDevice(id = deviceId)
                    if (doesIntentContainUrl(intent) && device != null && !device.isReachable) {
                        val text = extras.getString(Intent.EXTRA_TEXT)
                        storeUrlForFutureDelivery(
                            device = device,
                            url = text
                        )
                    }
                }
            }
            finish()
        } else {
            KdeConnect.getInstance().addDeviceListChangedCallback(key = "ShareActivity") {
                runOnUiThread { updateDeviceList() }
            }
            BackgroundService.forceRefreshConnections(context = this) // force a network re-discover
            updateDeviceList()
        }
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback(key = "ShareActivity")
        super.onStop()
    }

    companion object {
        private const val KEY_UNREACHABLE_URL_LIST = "key_unreachable_url_list"
    }
}