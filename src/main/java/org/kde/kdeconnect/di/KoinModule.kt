@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.about.LicensesActivity
import org.kde.kdeconnect.ui.about.getApplicationAboutData
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.screen.about.AboutScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingViewModel
import org.kde.kdeconnect.ui.compose.screen.presenter.PresenterScreen
import org.kde.kdeconnect.ui.compose.screen.presenter.PresenterViewModel
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginIndividualSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginSettingsViewModel
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PluginIndividualSettingsKey
import org.kde.kdeconnect.ui.navigation.PluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.FragmentSettingsBinding
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val pairingModule = module {
    viewModel<PairingViewModel>()
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
        HazeScaffold(
            title = stringResource(id = R.string.settings),
            backButton = true
        ) { paddingValues ->
            AndroidViewBinding({ inflater, parent, attachToParent ->
                FragmentSettingsBinding.inflate(inflater, parent, attachToParent)
            }, modifier = Modifier.padding(paddingValues)) {
                // FragmentContainerView automatically hosts SettingsFragment
            }
        }
    }
}

val deviceModule = module {
    viewModel<DeviceViewModel>()
    navigation<DeviceKey> { key ->
        val navigator = koinInject<Navigator>()
        DeviceScreen(
            deviceId = key.deviceId,
            onNavigateToPluginsSettings = {
                navigator.goTo(PluginSettingsKey(key.deviceId))
            }
        )
    }
}

val pluginSettingsModule = module {
    viewModel<PluginSettingsViewModel>()
    navigation<PluginSettingsKey> { key ->
        val navigator = koinInject<Navigator>()
        PluginSettingsScreen(
            deviceId = key.deviceId,
            onNavigateToPluginIndividualSettings = { pluginKey ->
                navigator.goTo(PluginIndividualSettingsKey(key.deviceId, pluginKey))
            }
        )
    }
    navigation<PluginIndividualSettingsKey> { key ->
        PluginIndividualSettingsScreen(
            deviceId = key.deviceId,
            pluginKey = key.pluginKey
        )
    }
}

val presenterModule = module {
    viewModel<PresenterViewModel>()
    navigation<PresenterKey> { key ->
        PresenterScreen(deviceId = key.deviceId)
    }
}

val appModule = module {
    includes(pairingModule, deviceModule, pluginSettingsModule, presenterModule, settingsModule, aboutModule)

    single<Navigator>()
}
