/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation3.ui.NavDisplay
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.ArrayUtils
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.mousepad.MousePadViewModel
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.share.ShareSettingsViewModel
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.ui.navigation.MousePadKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.android.ext.android.inject
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.scope.activityRetainedScope
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope

private const val STORAGE_LOCATION_CONFIGURED = 2020

class MainActivity : AppCompatActivity(), AndroidScopeComponent {
    override val scope: Scope by activityRetainedScope()
    val settingsDataStore: SettingsDataStore by inject()
    private val deviceHelper: DeviceHelper by inject()
    private val deviceManager: DeviceManager by inject()

    private val mNavigator: Navigator by inject()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val currentKey = mNavigator.backStack.lastOrNull()
        if (currentKey is MousePadKey) {
            val viewModel: MousePadViewModel = scope.get(MousePadViewModel::class, null) { parametersOf(currentKey.deviceId) }
            if (viewModel.onKeyEvent(event)) return true
        }
        if (currentKey is PresenterKey) {
            val offScreenControlsSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
            if (!offScreenControlsSupported) {
                val keyCode = event.keyCode
                val action = event.action
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val volumeKeysEnabled = prefs.getBoolean(getString(R.string.pref_presenter_enable_volume_keys), true)

                if (volumeKeysEnabled) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_UP) {
                        deviceManager.getDevicePlugin(currentKey.deviceId, PresenterPlugin::class.java)?.sendPrevious()
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_UP) {
                        deviceManager.getDevicePlugin(currentKey.deviceId, PresenterPlugin::class.java)?.sendNext()
                        return true
                    }
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deviceHelper.initializeDeviceId()

        setContent {
            val imageLoader: ImageLoader = koinInject()
            setSingletonImageLoaderFactory { imageLoader }

            MainActivityContent()
        }

        val missingPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionResult = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), RESULT_NOTIFICATIONS_ENABLED)
        }
    }

    @OptIn(KoinExperimentalAPI::class)
    @Composable
    private fun MainActivityContent() {
        val entryProvider = koinEntryProvider<Any>()
        val navigator: Navigator = koinInject()

        LaunchedEffect(intent) {
            val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
            val pluginKey = intent.getStringExtra(KdeConnectKeyConstants.EXTRA_PLUGIN_KEY)
            if (deviceId != null) {
                when (pluginKey) {
                    "RunCommandPlugin" -> navigator.goTo(RunCommandKey(deviceId))
                    "DigitizerPlugin" -> navigator.goTo(DigitizerKey(deviceId))
                    "MousePadPlugin" -> navigator.goTo(MousePadKey(deviceId))
                    else -> navigator.goTo(DeviceKey(deviceId))
                }
            }
        }

        KdeTheme(this) {
            NavDisplay(
                backStack = navigator.backStack,
                entryProvider = entryProvider,
                onBack = { navigator.goBack() },
                transitionSpec = {
                    if (navigator.backStack.size == 1) fadeIn(tween()) togetherWith fadeOut(tween())
                    else {
                        slideInHorizontally { it } togetherWith
                                slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                    }
                },
                popTransitionSpec = {
                    slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn(tween()) togetherWith
                            slideOutHorizontally { it }
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally { -it/2 } + scaleIn(initialScale = 0.7f) + fadeIn(tween()) togetherWith
                            slideOutHorizontally { it }
                }
            )
        }
    }

    private fun onPairResultFromNotification(deviceId: String?, pairStatus: String): String? {
        // TODO: Reimplement this
        assert(deviceId != null)
        if (pairStatus != PAIRING_PENDING) {
            val device = deviceManager.getDevice(deviceId) ?: return null
            when (pairStatus) {
                PAIRING_ACCEPTED -> device.acceptPairing()
                PAIRING_REJECTED -> device.cancelPairing()
            }
        }
        return if (pairStatus == PAIRING_ACCEPTED || pairStatus == PAIRING_PENDING) deviceId else null
    }

    override fun onStart() {
        super.onStart()
        BackgroundService.start(applicationContext)
    }

    override fun onStop() {
        deviceManager.removeDeviceListChangedCallback(this::class.simpleName!!)
        super.onStop()
    }

    @JvmOverloads
    fun onDeviceSelected(deviceId: String?, fromDeviceList: Boolean = false) {
        if (deviceId != null) {
            mNavigator.goTo(DeviceKey(deviceId, fromDeviceList))
        } else {
            mNavigator.goTo(PairingKey)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            RESULT_NEEDS_RELOAD -> {
                CoroutineScope(Dispatchers.IO).launch {
                    deviceManager.devices.values.forEach(Device::reloadPluginsFromSettings)
                }
            }
            STORAGE_LOCATION_CONFIGURED if resultCode == RESULT_OK && data != null -> {
                val uri = data.data
                ShareSettingsViewModel.saveStorageLocationPreference(this, uri!!)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun isPermissionGranted(permissions: Array<String>, grantResults: IntArray, permission : String) : Boolean {
        val index = ArrayUtils.indexOf(permissions, permission)
        return index != ArrayUtils.INDEX_NOT_FOUND && grantResults[index] == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsGranted = ArrayUtils.contains(grantResults, PackageManager.PERMISSION_GRANTED)
        if (permissionsGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isPermissionGranted(permissions, grantResults, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, STORAGE_LOCATION_CONFIGURED)
            }

            if (isPermissionGranted(permissions, grantResults, Manifest.permission.BLUETOOTH_CONNECT) &&
                isPermissionGranted(permissions, grantResults, Manifest.permission.BLUETOOTH_SCAN)) {
                CoroutineScope(Dispatchers.IO).launch {
                    settingsDataStore.setBluetoothEnabled(true)
                }
                mNavigator.goTo(SettingsKey)
            }

            CoroutineScope(Dispatchers.IO).launch {
                deviceManager.devices.values.forEach(Device::reloadPluginsFromSettings)
            }
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = KdeConnectKeyConstants.EXTRA_DEVICE_ID
        const val PAIR_REQUEST_STATUS = "pair_req_status"
        const val PAIRING_ACCEPTED = "accepted"
        const val PAIRING_REJECTED = "rejected"
        const val PAIRING_PENDING = "pending"
        const val RESULT_NEEDS_RELOAD = RESULT_FIRST_USER
        const val RESULT_NOTIFICATIONS_ENABLED = RESULT_FIRST_USER+1
        const val FLAG_FORCE_OVERVIEW = "forceOverview"
    }
}
