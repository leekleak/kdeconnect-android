/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.mousepad.MousePadViewModel
import org.kde.kdeconnect.plugins.mpris.MprisViewModel
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.ui.navigation.HomeKey
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.kde.kdeconnect.ui.navigation.MousePadKey
import org.kde.kdeconnect.ui.navigation.MprisKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.koin.android.ext.android.inject
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.scope.activityRetainedScope
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope

class MainActivity : AppCompatActivity(), AndroidScopeComponent {
    override val scope: Scope by activityRetainedScope()
    private val settingsDataStore: SettingsDataStore by inject()
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
                val volumeKeysEnabled = runBlocking { settingsDataStore.presenterVolumeKeysEnabled.first() }

                if (volumeKeysEnabled) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_UP) {
                        lifecycleScope.launch {
                            deviceManager.getDevicePlugin(currentKey.deviceId, PresenterPlugin::class.java)?.sendPrevious()
                        }
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_UP) {
                        lifecycleScope.launch {
                            deviceManager.getDevicePlugin(currentKey.deviceId, PresenterPlugin::class.java)?.sendNext()
                        }
                        return true
                    }
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        return true
                    }
                }
            }
        }
        if (currentKey is MprisKey) {
            val keyCode = event.keyCode
            val action = event.action
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (action == KeyEvent.ACTION_UP) {
                    val viewModel: MprisViewModel = scope.get(MprisViewModel::class, null) { parametersOf(currentKey.deviceId) }
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        viewModel.onVolumeUp()
                    } else {
                        viewModel.onVolumeDown()
                    }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        runBlocking { deviceHelper.initializeDeviceId() }

        setContent {
            val imageLoader: ImageLoader = koinInject()
            setSingletonImageLoaderFactory { imageLoader }

            MainActivityContent()
        }
    }

    @OptIn(KoinExperimentalAPI::class)
    @Composable
    private fun MainActivityContent() {
        val entryProvider = koinEntryProvider<Any>()
        val navigator: Navigator = koinInject()
        val hazeState: HazeState = koinInject()

        LaunchedEffect(intent) {
            val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
            val pluginKey = intent.getStringExtra(KdeConnectKeyConstants.EXTRA_PLUGIN_KEY)
            if (deviceId != null) {
                when (pluginKey) {
                    "RunCommandPlugin" -> navigator.goTo(RunCommandKey(deviceId))
                    "DigitizerPlugin" -> navigator.goTo(DigitizerKey(deviceId))
                    "MousePadPlugin" -> navigator.goTo(MousePadKey(deviceId))
                    "MprisPlugin" -> navigator.goTo(MprisKey(deviceId))
                    else -> navigator.setTo(listOf(HomeKey, DeviceKey(deviceId)))
                }
            }
        }

        KdeTheme {
            NavDisplay(
                modifier = Modifier.hazeSource(hazeState),
                backStack = navigator.backStack,
                entryProvider = entryProvider,
                onBack = { navigator.goBack() },
                sceneStrategies = listOf(DialogSceneStrategy()),
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

    override fun onStart() {
        super.onStart()
        BackgroundService.start(applicationContext)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    var shareGetResultCallback: ((List<Uri>) -> Unit)? = null
    val shareGetResult = registerForActivityResult(GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isEmpty()) {
                Log.w("SendFileActivity", "No files to send?")
            } else {
                shareGetResultCallback?.invoke(uris)
            }
            shareGetResultCallback = null
        }

    companion object {
        const val EXTRA_DEVICE_ID = KdeConnectKeyConstants.EXTRA_DEVICE_ID
        const val FLAG_FORCE_OVERVIEW = "forceOverview"
    }
}
