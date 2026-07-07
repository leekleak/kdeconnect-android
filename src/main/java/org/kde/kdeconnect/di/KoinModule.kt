@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.about.getApplicationAboutData
import org.kde.kdeconnect.plugins.digitizer.DigitizerSettingsScreen
import org.kde.kdeconnect.plugins.digitizer.DigitizerSettingsViewModel
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhoneSettingsScreen
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhoneSettingsViewModel
import org.kde.kdeconnect.plugins.mpris.MprisSettingsScreen
import org.kde.kdeconnect.plugins.mpris.MprisSettingsViewModel
import org.kde.kdeconnect.plugins.notifications.NotificationFilterScreen
import org.kde.kdeconnect.plugins.notifications.NotificationFilterViewModel
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsScreen
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.about.AboutScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesEvent
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingViewModel
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginIndividualSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginSettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.presenter.PresenterScreen
import org.kde.kdeconnect.ui.compose.screen.presenter.PresenterViewModel
import org.kde.kdeconnect.ui.compose.screen.settings.SettingsScreen
import org.kde.kdeconnect.ui.compose.screen.settings.SettingsViewModel
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.LicensesKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PluginIndividualSettingsKey
import org.kde.kdeconnect.ui.navigation.PluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.R
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
        val navigator = koinInject<Navigator>()

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
                navigator.goTo(LicensesKey)
            },
            onWebsiteClicked = {
                aboutData.websiteURL?.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            }
        )
    }
    navigation<LicensesKey> {
        val scrollEvents = MutableSharedFlow<LicensesEvent>()
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
                            contentDescription = stringResource(R.string.scroll_to_top)
                        )
                    }
                    IconButton(onClick = {
                        scope.launch {
                            scrollEvents.emit(LicensesEvent.ScrollToBottom)
                        }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_downward_black_24dp),
                            contentDescription = stringResource(R.string.scroll_to_bottom)
                        )
                    }
                }
            }
        )
    }
}

val settingsModule = module {
    viewModel<SettingsViewModel>()
    navigation<SettingsKey> {
        SettingsScreen()
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
    viewModel<DigitizerSettingsViewModel>()
    viewModel<FindMyPhoneSettingsViewModel>()
    viewModel<MprisSettingsViewModel>()
    viewModel<PresenterSettingsViewModel>()
    viewModel<NotificationFilterViewModel>()
    navigation<PluginSettingsKey> { key ->
        PluginSettingsScreen(key.deviceId)
    }
    navigation<PluginIndividualSettingsKey> { key ->
        when (key.pluginKey) {
            "DigitizerPlugin" -> DigitizerSettingsScreen()
            "FindMyPhonePlugin" -> FindMyPhoneSettingsScreen()
            "MprisPlugin" -> MprisSettingsScreen()
            "PresenterPlugin" -> PresenterSettingsScreen()
            "NotificationsPlugin" -> NotificationFilterScreen()
            else -> PluginIndividualSettingsScreen(
                pluginKey = key.pluginKey
            )
        }
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
