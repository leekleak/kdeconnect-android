@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.request.crossfade
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.kde.kdeconnect.datastore.MousePadSettingsDataStore
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.helpers.AppIconFetcher
import org.kde.kdeconnect.helpers.DeviceHelper
import androidx.room.Room
import org.kde.kdeconnect.helpers.DeviceDao
import org.kde.kdeconnect.helpers.DevicesRoomDatabase
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.PermissionHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
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
import org.kde.kdeconnect.plugins.mousepad.BigscreenScreen
import org.kde.kdeconnect.plugins.mousepad.BigscreenViewModel
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadScreen
import org.kde.kdeconnect.plugins.mousepad.MousePadSettingsScreen
import org.kde.kdeconnect.plugins.mousepad.MousePadSettingsViewModel
import org.kde.kdeconnect.plugins.mousepad.MousePadViewModel
import org.kde.kdeconnect.plugins.mousereceiver.MouseReceiverPlugin
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPlugin
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.plugins.ping.PingPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsScreen
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsViewModel
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPlugin
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandScreen
import org.kde.kdeconnect.plugins.runcommand.RunCommandViewModel
import org.kde.kdeconnect.plugins.sftp.SftpPlugin
import org.kde.kdeconnect.plugins.share.SharePlugin
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
import org.kde.kdeconnect.ui.compose.screen.permissions.PermissionsScreen
import org.kde.kdeconnect.ui.compose.screen.device.settings.DeviceSettingsScreen
import org.kde.kdeconnect.ui.compose.screen.device.settings.DeviceSettingsViewModel
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
import org.kde.kdeconnect.ui.navigation.BigscreenKey
import org.kde.kdeconnect.ui.navigation.ConnectionsSettingsKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.ui.navigation.LicensesKey
import org.kde.kdeconnect.ui.navigation.MousePadKey
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationSettingsKey
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PermissionsScreenKey
import org.kde.kdeconnect.ui.navigation.PluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.PresenterPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey
import org.kde.kdeconnect_tp.R
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel


val pairingModule = module {
    viewModel<PairingViewModel>()
    navigation<PairingKey> {
        val viewModel: PairingViewModel = koinViewModel()
        val state by viewModel.pairingUiState.collectAsStateWithLifecycle()
        val navigator = koinInject<Navigator>()
        PairingScreen(
            uiState = state,
            onClick = { deviceId -> navigator.goTo(DeviceKey(deviceId, true)) },
            onRefresh = { viewModel.onRefresh(get()) }
        )
    }
    navigation<PermissionsScreenKey> {
        PermissionsScreen()
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
    single<RunCommandSettingsDataStore>()
    single<MousePadSettingsDataStore>()
    single<DeviceSettings> { DeviceSettings(get()) }
    single<DeviceHelper> { DeviceHelper(get()) }
    single<DeviceManager> { DeviceManager(get()) }
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
    viewModel<DeviceSettingsViewModel>()
    viewModel<MousePadSettingsViewModel>()
    viewModel<SftpSettingsViewModel>()
    viewModel<TelephonySettingsViewModel>()
    viewModel<PresenterSettingsViewModel>()
    viewModel<NotificationSettingsViewModel>()
    navigation<PluginSettingsKey> { key ->
        DeviceSettingsScreen(key.deviceId)
    }
    navigation<MousePadPluginSettingsKey> { MousePadSettingsScreen() }
    navigation<SftpPluginSettingsKey> { SftpSettingsScreen() }
    navigation<TelephonyPluginSettingsKey> { TelephonySettingsScreen() }
    navigation<PresenterPluginSettingsKey> { PresenterSettingsScreen() }
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
    viewModel<BigscreenViewModel>()
    navigation<MousePadKey> { key ->
        MousePadScreen(deviceId = key.deviceId)
    }
    navigation<BigscreenKey> { key ->
        BigscreenScreen(deviceId = key.deviceId)
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
    single<KdeConnect> { get<Context>() as KdeConnect }
    single<BackgroundServiceData>()
    includes(pairingModule, deviceModule, pluginSettingsModule, presenterModule, mousePadModule, runCommandModule, digitizerModule, settingsModule, aboutModule)

    single {
        val startDestination = if (PermissionHelper.hasRequiredPermissions(get())) {
            PairingKey
        } else {
            PermissionsScreenKey
        }
        Navigator(startDestination)
    }
    single<ImageLoader> { create(::buildImageLoader) }

    single<TrustedNetworkHelper>()
    single<AppDatabase>()

    single<DevicesRoomDatabase> {
        Room.databaseBuilder(
            get(),
            DevicesRoomDatabase::class.java,
            "Devices"
        ).build()
    }
    single<DeviceDao> { get<DevicesRoomDatabase>().deviceDao() }

    factory<Device>()

    factory { (context: Context) -> LanLinkProvider(context, get(), get(), get()) }

    scope<Device> {
        scoped { SftpPlugin(get(), get(), get()) }
        scoped { BatteryPlugin(get(), get()) }
        scoped { ClipboardPlugin(get(), get()) }
        scoped { ConnectivityReportPlugin(get(), get()) }
        scoped { ContactsPlugin(get(), get()) }
        scoped { FindMyPhonePlugin(get(), get(), get()) }
        scoped { FindRemoteDevicePlugin(get(), get()) }
        scoped { InputDevicesReceiverPlugin(get(), get()) }
        scoped { MousePadPlugin(get(), get(), get()) }
        scoped { MouseReceiverPlugin(get(), get()) }
        scoped { MprisPlugin(get(), get(), get()) }
        scoped { MprisReceiverPlugin(get(), get()) }
        scoped { NotificationsPlugin(get(), get(), get(), get()) }
        scoped { PingPlugin(get(), get()) }
        scoped { PresenterPlugin(get(), get()) }
        scoped { ReceiveNotificationsPlugin(get(), get()) }
        scoped { RemoteKeyboardPlugin(get(), get()) }
        scoped { RunCommandPlugin(get(), get(), get()) }
        scoped { SharePlugin(get(), get()) }
        scoped { SMSPlugin(get(), get(), get()) }
        scoped { SystemVolumePlugin(get(), get()) }
        scoped { TelephonyPlugin(get(), get(), get()) }
        scoped { DigitizerPlugin(get(), get()) }
    }
}
