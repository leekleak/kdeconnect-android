@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.content.Context
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
import coil3.ImageLoader
import coil3.request.crossfade
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.helpers.AppIconFetcher
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityReportPlugin
import org.kde.kdeconnect.plugins.contacts.ContactsPlugin
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin
import org.kde.kdeconnect.plugins.digitizer.DigitizerScreen
import org.kde.kdeconnect.plugins.digitizer.DigitizerViewModel
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin
import org.kde.kdeconnect.plugins.findremotedevice.FindRemoteDevicePlugin
import org.kde.kdeconnect.plugins.inputdevicesreceiver.InputDevicesReceiverPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadScreen
import org.kde.kdeconnect.plugins.mousepad.MousePadSettingsScreen
import org.kde.kdeconnect.plugins.mousepad.MousePadSettingsViewModel
import org.kde.kdeconnect.plugins.mousepad.MousePadViewModel
import org.kde.kdeconnect.plugins.mousereceiver.MouseReceiverPlugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPlugin
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.plugins.ping.PingPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsScreen
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsViewModel
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPlugin
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardSettingsScreen
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardSettingsViewModel
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandScreen
import org.kde.kdeconnect.plugins.runcommand.RunCommandViewModel
import org.kde.kdeconnect.plugins.sftp.SftpPlugin
import org.kde.kdeconnect.plugins.share.SharePlugin
import org.kde.kdeconnect.plugins.share.ShareSettingsScreen
import org.kde.kdeconnect.plugins.share.ShareSettingsViewModel
import org.kde.kdeconnect.plugins.sms.SMSPlugin
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect.plugins.telephony.TelephonyPlugin
import org.kde.kdeconnect.ui.about.getApplicationAboutData
import org.kde.kdeconnect.ui.compose.screen.about.AboutScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceScreen
import org.kde.kdeconnect.ui.compose.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesEvent
import org.kde.kdeconnect.ui.compose.screen.licenses.LicensesScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingScreen
import org.kde.kdeconnect.ui.compose.screen.pairing.PairingViewModel
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.plugin.PluginSettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.presenter.PresenterScreen
import org.kde.kdeconnect.ui.compose.screen.presenter.PresenterViewModel
import org.kde.kdeconnect.ui.compose.screen.settings.SettingsScreen
import org.kde.kdeconnect.ui.compose.screen.settings.SettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.calls_and_messages.TelephonySettingsScreen
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.calls_and_messages.TelephonySettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections.ConnectionsSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.connections.ConnectionsSettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.filesystem.SftpSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.filesystem.SftpSettingsViewModel
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.notifications.NotificationSettings
import org.kde.kdeconnect.ui.compose.screen.settings.advanced.notifications.NotificationSettingsViewModel
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.ConnectionsSettingsKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.ui.navigation.LicensesKey
import org.kde.kdeconnect.ui.navigation.MousePadKey
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationSettingsKey
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.PresenterPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RemoteKeyboardPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.SharePluginSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.create
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
    single<TelephonySettingsDataStore>()
    single<SettingsDataStore>()
    single<DeviceHelper> { DeviceHelper(get()) }
    single<NotificationSettingsDataStore>()
    single<SftpSettingsDataStore>()
    single<ConnectionsSettingsDataStore>()
    viewModel<SettingsViewModel>()
    viewModel<ConnectionsSettingsViewModel>()
    navigation<SettingsKey> {
        SettingsScreen()
    }
    navigation<ConnectionsSettingsKey> {
        ConnectionsSettingsScreen()
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
    viewModel<MousePadSettingsViewModel>()
    viewModel<SftpSettingsViewModel>()
    viewModel<TelephonySettingsViewModel>()
    viewModel<ShareSettingsViewModel>()
    viewModel<PresenterSettingsViewModel>()
    viewModel<RemoteKeyboardSettingsViewModel>()
    viewModel<NotificationSettingsViewModel>()
    navigation<PluginSettingsKey> { key ->
        PluginSettingsScreen(key.deviceId)
    }
    navigation<MousePadPluginSettingsKey> { MousePadSettingsScreen() }
    navigation<SftpPluginSettingsKey> { SftpSettingsScreen() }
    navigation<TelephonyPluginSettingsKey> { TelephonySettingsScreen() }
    navigation<SharePluginSettingsKey> { ShareSettingsScreen() }
    navigation<PresenterPluginSettingsKey> { PresenterSettingsScreen() }
    navigation<RemoteKeyboardPluginSettingsKey> { RemoteKeyboardSettingsScreen() }
    navigation<NotificationSettingsKey> { NotificationSettings() }
}

val presenterModule = module {
    viewModel<PresenterViewModel>()
    navigation<PresenterKey> { key ->
        PresenterScreen(deviceId = key.deviceId)
    }
}

val mousePadModule = module {
    viewModel<MousePadViewModel>()
    navigation<MousePadKey> { key ->
        MousePadScreen(deviceId = key.deviceId)
    }
}

val runCommandModule = module {
    viewModel<RunCommandViewModel>()
    navigation<RunCommandKey> { key ->
        RunCommandScreen(deviceId = key.deviceId)
    }
}

val digitizerModule = module {
    viewModel<DigitizerViewModel>()
    navigation<DigitizerKey> { key ->
        DigitizerScreen(deviceId = key.deviceId)
    }
}

fun buildImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .components { add(AppIconFetcher.Factory(context)) }
        .crossfade(true)
        .build()

val appModule = module {
    includes(pairingModule, deviceModule, pluginSettingsModule, presenterModule, mousePadModule, runCommandModule, digitizerModule, settingsModule, aboutModule)

    single<Navigator>()
    single<ImageLoader> { create(::buildImageLoader) }

    scope<Device> {
        scoped { SftpPlugin(get(), get(), get()) }
        scoped { BatteryPlugin(get(), get()) }
        scoped { ClipboardPlugin(get(), get()) }
        scoped { ConnectivityReportPlugin(get(), get()) }
        scoped { ContactsPlugin(get(), get()) }
        scoped { FindMyPhonePlugin(get(), get(), get(), get()) }
        scoped { FindRemoteDevicePlugin(get(), get()) }
        scoped { InputDevicesReceiverPlugin(get(), get()) }
        scoped { MousePadPlugin(get(), get()) }
        scoped { MouseReceiverPlugin(get(), get()) }
        scoped { MprisPlugin(get(), get(), get()) }
        scoped { MprisReceiverPlugin(get(), get()) }
        scoped { NotificationsPlugin(get(), get(), get()) }
        scoped { PingPlugin(get(), get()) }
        scoped { PresenterPlugin(get(), get()) }
        scoped { ReceiveNotificationsPlugin(get(), get()) }
        scoped { RemoteKeyboardPlugin(get(), get()) }
        scoped { RunCommandPlugin(get(), get()) }
        scoped { SharePlugin(get(), get()) }
        scoped { SMSPlugin(get(), get(), get()) }
        scoped { SystemVolumePlugin(get(), get()) }
        scoped { TelephonyPlugin(get(), get(), get()) }
        scoped { DigitizerPlugin(get(), get()) }
    }
}
