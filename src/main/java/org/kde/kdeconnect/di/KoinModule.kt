@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.content.Intent
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
import org.kde.kdeconnect.plugins.digitizer.DigitizerSettingsScreen
import org.kde.kdeconnect.plugins.digitizer.DigitizerSettingsViewModel
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhoneSettingsScreen
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhoneSettingsViewModel
import org.kde.kdeconnect.plugins.mousepad.MousePadSettingsScreen
import org.kde.kdeconnect.plugins.mousepad.MousePadSettingsViewModel
import org.kde.kdeconnect.plugins.mpris.MprisSettingsScreen
import org.kde.kdeconnect.plugins.mpris.MprisSettingsViewModel
import org.kde.kdeconnect.plugins.notifications.NotificationFilterScreen
import org.kde.kdeconnect.plugins.notifications.NotificationFilterViewModel
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsScreen
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsViewModel
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardSettingsScreen
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardSettingsViewModel
import org.kde.kdeconnect.plugins.runcommand.RunCommandSettingsScreen
import org.kde.kdeconnect.plugins.runcommand.RunCommandSettingsViewModel
import org.kde.kdeconnect.plugins.sftp.SftpSettingsScreen
import org.kde.kdeconnect.plugins.sftp.SftpSettingsViewModel
import org.kde.kdeconnect.plugins.share.ShareSettingsScreen
import org.kde.kdeconnect.plugins.share.ShareSettingsViewModel
import org.kde.kdeconnect.plugins.sms.SmsSettingsScreen
import org.kde.kdeconnect.plugins.sms.SmsSettingsViewModel
import org.kde.kdeconnect.plugins.telephony.TelephonySettingsScreen
import org.kde.kdeconnect.plugins.telephony.TelephonySettingsViewModel
import org.kde.kdeconnect.ui.about.getApplicationAboutData
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
import org.kde.kdeconnect.ui.navigation.DigitizerPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.FindMyPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.LicensesKey
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.MprisPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.PresenterPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RemoteKeyboardPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RunCommandPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SMSPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SharePluginSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey
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

        DisposableEffect(Unit) {
            viewModel.onStart(context)
            onDispose { viewModel.onStop() }
        }
        PairingScreen(
            uiState = state,
            onClick = { deviceId -> navigator.goTo(DeviceKey(deviceId, true)) },
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
    viewModel<MousePadSettingsViewModel>()
    viewModel<MprisSettingsViewModel>()
    viewModel<SftpSettingsViewModel>()
    viewModel<SmsSettingsViewModel>()
    viewModel<TelephonySettingsViewModel>()
    viewModel<ShareSettingsViewModel>()
    viewModel<PresenterSettingsViewModel>()
    viewModel<RemoteKeyboardSettingsViewModel>()
    viewModel<RunCommandSettingsViewModel>()
    viewModel<NotificationFilterViewModel>()
    navigation<PluginSettingsKey> { key ->
        PluginSettingsScreen(key.deviceId)
    }
    navigation<DigitizerPluginSettingsKey> { DigitizerSettingsScreen() }
    navigation<FindMyPluginSettingsKey> { FindMyPhoneSettingsScreen() }
    navigation<MousePadPluginSettingsKey> { MousePadSettingsScreen() }
    navigation<MprisPluginSettingsKey> { MprisSettingsScreen() }
    navigation<SftpPluginSettingsKey> { SftpSettingsScreen() }
    navigation<SMSPluginSettingsKey> { SmsSettingsScreen() }
    navigation<TelephonyPluginSettingsKey> { TelephonySettingsScreen() }
    navigation<SharePluginSettingsKey> { ShareSettingsScreen() }
    navigation<PresenterPluginSettingsKey> { PresenterSettingsScreen() }
    navigation<RemoteKeyboardPluginSettingsKey> { RemoteKeyboardSettingsScreen() }
    navigation<RunCommandPluginSettingsKey> { RunCommandSettingsScreen() }
    navigation<NotificationPluginSettingsKey> { NotificationFilterScreen() }
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
