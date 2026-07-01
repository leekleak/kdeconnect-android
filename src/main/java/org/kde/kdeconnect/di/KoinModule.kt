@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.PluginSettingsActivity
import org.kde.kdeconnect.ui.about.AboutKDEActivity
import org.kde.kdeconnect.ui.about.EasterEggActivity
import org.kde.kdeconnect.ui.about.LicensesActivity
import org.kde.kdeconnect.ui.about.getApplicationAboutData
import org.kde.kdeconnect.ui.compose.screen.about.AboutScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingViewModel
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.databinding.FragmentSettingsBinding
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

val pairingModule = module {
    viewModelOf(::PairingViewModel)
    navigation<PairingKey> {
        val viewModel: PairingViewModel = koinViewModel()
        val state by viewModel.pairingUiState.collectAsStateWithLifecycle()
        val navigator = koinInject<Navigator>()
        val context = LocalContext.current

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* result handled via KdeConnect's own permission callback if needed */ }

        DisposableEffect(Unit) {
            viewModel.onStart(context)
            onDispose { viewModel.onStop() }
        }
        PairingScreen(
            uiState = state,
            onClick = { deviceId -> navigator.goTo(DeviceKey(deviceId, true)) },
            onWifiSettingsClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            onNotificationSettingsClick = { /* Handle permission */ },
            onDuplicateNamesClick = { /* ... */ },
            onRefresh = { viewModel.onRefresh() }
        )
    }
}

val aboutModule = module {
    navigation<AboutKey> {
        val context = LocalContext.current
        val aboutData = getApplicationAboutData(context)
        AboutScreen(
            aboutData = aboutData,
            onEasterEggTriggered = {
                context.startActivity(
                    Intent(
                        context,
                        EasterEggActivity::class.java
                    )
                )
            },
            onReportBugClicked = {
                aboutData.bugURL?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            onDonateClicked = {
                aboutData.donateURL?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            onSourceCodeClicked = {
                aboutData.sourceCodeURL?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            onLicensesClicked = {
                context.startActivity(
                    Intent(
                        context,
                        LicensesActivity::class.java
                    )
                )
            },
            onAboutKdeClicked = {
                context.startActivity(
                    Intent(
                        context,
                        AboutKDEActivity::class.java
                    )
                )
            },
            onWebsiteClicked = {
                aboutData.websiteURL?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            }
        )
    }
}

val settingsModule = module {
    navigation<SettingsKey> {
        AndroidViewBinding({ inflater, parent, attachToParent ->
            FragmentSettingsBinding.inflate(inflater, parent, attachToParent)
        }) {
            // FragmentContainerView automatically hosts SettingsFragment
        }
    }
}

val deviceModule = module {
    viewModelOf(::DeviceViewModel)
    navigation<DeviceKey> { key ->
        val context = LocalContext.current
        DeviceScreen(
            deviceId = key.deviceId,
            viewModel = koinViewModel { parametersOf(key.deviceId) },
            onNavigateToPluginsSettings = {
                val intent = Intent(context, PluginSettingsActivity::class.java)
                intent.putExtra("deviceId", key.deviceId)
                context.startActivity(intent)
            }
        )
    }
}

val appModule = module {
    includes(pairingModule, deviceModule, settingsModule, aboutModule)

    single { Navigator(startDestination = PairingKey) }
}