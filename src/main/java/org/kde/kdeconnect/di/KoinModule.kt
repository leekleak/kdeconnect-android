@file:OptIn(KoinExperimentalAPI::class)

package org.kde.kdeconnect.di

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.room3.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.backends.bluetooth.BluetoothLinkProvider
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.backends.loopback.LoopbackLinkProvider
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.kde.kdeconnect.datastore.MousePadSettingsDataStore
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.helpers.AppIconFetcher
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.helpers.DeviceDao
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.DevicesRoomDatabase
import org.kde.kdeconnect.helpers.PermissionHelper
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.helpers.VideoUrlsHelper
import org.kde.kdeconnect.helpers.security.SslHelper
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
import org.kde.kdeconnect.plugins.mpris.MprisAlbumArtFetcher
import org.kde.kdeconnect.plugins.mpris.MprisMediaSession
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.mpris.MprisScreen
import org.kde.kdeconnect.plugins.mpris.MprisViewModel
import org.kde.kdeconnect.plugins.mpris.SinkSelector
import org.kde.kdeconnect.plugins.mpris.SourceSelector
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPlugin
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterScreen
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsScreen
import org.kde.kdeconnect.plugins.presenter.PresenterSettingsViewModel
import org.kde.kdeconnect.plugins.presenter.PresenterViewModel
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
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect.ui.about.AboutData
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.BigscreenKey
import org.kde.kdeconnect.ui.navigation.ConnectionsSettingsKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.DeviceSettingsKey
import org.kde.kdeconnect.ui.navigation.DeviceShortcutSettingsKey
import org.kde.kdeconnect.ui.navigation.DigitizerKey
import org.kde.kdeconnect.ui.navigation.HomeKey
import org.kde.kdeconnect.ui.navigation.LicensesKey
import org.kde.kdeconnect.ui.navigation.MousePadKey
import org.kde.kdeconnect.ui.navigation.MousePadPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.MprisKey
import org.kde.kdeconnect.ui.navigation.MprisSinkKey
import org.kde.kdeconnect.ui.navigation.MprisSourceKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.NotificationSettingsKey
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PermissionsScreenKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.PresenterPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.RunCommandKey
import org.kde.kdeconnect.ui.navigation.SavedDevicesKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect.ui.navigation.SftpPluginSettingsKey
import org.kde.kdeconnect.ui.navigation.TelephonyPluginSettingsKey
import org.kde.kdeconnect.ui.screen.about.AboutScreen
import org.kde.kdeconnect.ui.screen.device.DeviceScreen
import org.kde.kdeconnect.ui.screen.device.DeviceViewModel
import org.kde.kdeconnect.ui.screen.device.settings.DeviceSettingsScreen
import org.kde.kdeconnect.ui.screen.device.settings.DeviceSettingsViewModel
import org.kde.kdeconnect.ui.screen.device.settings.DeviceShortcutSettingsScreen
import org.kde.kdeconnect.ui.screen.device.settings.DeviceShortcutSettingsViewModel
import org.kde.kdeconnect.ui.screen.home.HomeScreen
import org.kde.kdeconnect.ui.screen.home.HomeViewModel
import org.kde.kdeconnect.ui.screen.licenses.LicensesScreen
import org.kde.kdeconnect.ui.screen.pairing.PairingScreen
import org.kde.kdeconnect.ui.screen.pairing.PairingViewModel
import org.kde.kdeconnect.ui.screen.permissions.PermissionsScreen
import org.kde.kdeconnect.ui.screen.settings.SettingsScreen
import org.kde.kdeconnect.ui.screen.settings.SettingsViewModel
import org.kde.kdeconnect.ui.screen.settings.advanced.calls_and_messages.TelephonySettingsScreen
import org.kde.kdeconnect.ui.screen.settings.advanced.calls_and_messages.TelephonySettingsViewModel
import org.kde.kdeconnect.ui.screen.settings.advanced.connections.ConnectionsSettingsScreen
import org.kde.kdeconnect.ui.screen.settings.advanced.connections.ConnectionsSettingsViewModel
import org.kde.kdeconnect.ui.screen.settings.advanced.filesystem.SftpSettingsScreen
import org.kde.kdeconnect.ui.screen.settings.advanced.filesystem.SftpSettingsViewModel
import org.kde.kdeconnect.ui.screen.settings.advanced.notifications.NotificationSettings
import org.kde.kdeconnect.ui.screen.settings.advanced.notifications.NotificationSettingsViewModel
import org.kde.kdeconnect.ui.screen.settings.advanced.paired.SavedDevices
import org.kde.kdeconnect.ui.screen.settings.advanced.paired.SavedDevicesViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel


val homeModule = module {
    viewModel<HomeViewModel>()
    navigation<HomeKey> {
        val viewModel: HomeViewModel = koinViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val navigator: Navigator = get()
        HomeScreen(
            uiState = state,
            deviceManager = get(),
            onClick = { deviceId -> navigator.goTo(DeviceKey(deviceId, true)) },
            onRefresh = { viewModel.onRefresh(get()) },
            onNavigateToPairingScreen = { navigator.goTo(PairingKey) },
            onNavigateToSettingsScreen = { navigator.goTo(SettingsKey) }
        )
    }
    viewModel<PairingViewModel>()
    navigation<PairingKey> {
        val viewModel: PairingViewModel = koinViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PairingScreen(
            uiState = state,
            onClick = { viewModel.pair(it) },
            onRefresh = { viewModel.onRefresh(get()) },
            navigator = get(),
        )
    }
    navigation<PermissionsScreenKey> {
        PermissionsScreen(navigator = get())
    }
}

val aboutModule = module {
    navigation<AboutKey> {
        val context: Context = get()
        val aboutData = AboutData()
        val navigator: Navigator = get()

        AboutScreen(
            aboutData = aboutData,
            onReportBugClicked = {
                aboutData.bugURL.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            onDonateClicked = {
                aboutData.donateURL.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            onSourceCodeClicked = {
                aboutData.sourceCodeURL.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            onLicensesClicked = {
                navigator.goTo(LicensesKey)
            },
            onWebsiteClicked = {
                aboutData.websiteURL.let {
                    context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                }
            },
            navigator = navigator
        )
    }
    navigation<LicensesKey> {
        LicensesScreen(navigator = get())
    }
}

val settingsModule = module {
    single<TelephonySettingsDataStore>()
    single<SettingsDataStore>()
    single<RunCommandSettingsDataStore>()
    single<MousePadSettingsDataStore>()
    single<DeviceSettings>()
    single<DeviceHelper>()
    single<ThemeUtil>()
    single<SslHelper>()
    single<CustomDevicesHelper>()
    single<VideoUrlsHelper>()
    single<DeviceManager> {
        DeviceManager(get()) { deviceId, link ->
            get<Device> { parametersOf(deviceId, link) }
        }
    }
    single<NotificationSettingsDataStore>()
    single<SftpSettingsDataStore>()
    single<ConnectionsSettingsDataStore>()
    viewModel<SettingsViewModel>()
    viewModel<ConnectionsSettingsViewModel>()
    navigation<SettingsKey> {
        val viewModel: SettingsViewModel = koinViewModel()

        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        SettingsScreen(
            uiState = uiState,
            exportLogs = viewModel::exportLogs,
            setBluetoothEnabled = viewModel::setBluetoothEnabled,
            setDeviceName = viewModel::setDeviceName,
            setTheme = viewModel::setTheme,
            saveStorageLocation = viewModel::saveStorageLocation,
            resetStorageLocation = viewModel::resetStorageLocation,
            getDisplayPath = viewModel::getDisplayPath,
            navigator = get()
        )
    }
    navigation<ConnectionsSettingsKey> { ConnectionsSettingsScreen(navigator = get()) }
    viewModel<SavedDevicesViewModel>()
    navigation<SavedDevicesKey> { SavedDevices(navigator = get()) }
}

val deviceModule = module {
    viewModel<DeviceViewModel>()
    navigation<DeviceKey> { key ->
        val navigator: Navigator = get()
        DeviceScreen(
            deviceId = key.deviceId,
            onNavigateToPluginsSettings = { navigator.goTo(DeviceSettingsKey(key.deviceId)) },
            onNavigateToPairingScreen = { navigator.setTo(HomeKey) },
            navigator = navigator
        )
    }

    viewModel<DeviceSettingsViewModel>()
    navigation<DeviceSettingsKey> { key -> DeviceSettingsScreen(deviceId = key.deviceId, navigator = get()) }

    viewModel<DeviceShortcutSettingsViewModel>()
    navigation<DeviceShortcutSettingsKey> { key ->
        DeviceShortcutSettingsScreen(
            deviceId = key.deviceId,
            navigator = get()
        )
    }
}

val pluginSettingsModule = module {
    viewModel<MousePadSettingsViewModel>()
    viewModel<SftpSettingsViewModel>()
    viewModel<TelephonySettingsViewModel>()
    viewModel<PresenterSettingsViewModel>()
    viewModel<NotificationSettingsViewModel>()
    navigation<MousePadPluginSettingsKey> { MousePadSettingsScreen(navigator = get()) }
    navigation<SftpPluginSettingsKey> { SftpSettingsScreen(navigator = get()) }
    navigation<TelephonyPluginSettingsKey> { TelephonySettingsScreen(navigator = get()) }
    navigation<PresenterPluginSettingsKey> { PresenterSettingsScreen(navigator = get()) }
    navigation<NotificationSettingsKey> { NotificationSettings(navigator = get()) }
}

val presenterModule = module {
    viewModel<PresenterViewModel>()
    navigation<PresenterKey> { key ->
        PresenterScreen(deviceId = key.deviceId, navigator = get())
    }
}

val mprisModule = module {
    viewModel<MprisViewModel>()
    navigation<MprisKey>(metadata = DialogSceneStrategy.dialog(
        dialogProperties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    )) { key ->
        MprisScreen(deviceId = key.deviceId, navigator = get())
    }
    navigation<MprisSinkKey>(metadata = DialogSceneStrategy.dialog()) { key ->
        SinkSelector(deviceId = key.deviceId, navigator = get())
    }
    navigation<MprisSourceKey>(metadata = DialogSceneStrategy.dialog()) { key ->
        SourceSelector(deviceId = key.deviceId, navigator = get())
    }
}

val mousePadModule = module {
    viewModel<MousePadViewModel>()
    viewModel<BigscreenViewModel>()
    navigation<MousePadKey> { key ->
        val navigator: Navigator = koinInject()
        MousePadScreen(
            deviceId = key.deviceId,
            navigator = navigator
        )
    }
    navigation<BigscreenKey> { key ->
        BigscreenScreen(deviceId = key.deviceId, navigator = get())
    }
}

val runCommandModule = module {
    viewModel<RunCommandViewModel>()
    navigation<RunCommandKey> { key ->
        RunCommandScreen(deviceId = key.deviceId, navigator = get())
    }
}

val digitizerModule = module {
    viewModel<DigitizerViewModel>()
    navigation<DigitizerKey> { key ->
        val navigator: Navigator = koinInject()
        DigitizerScreen(deviceId = key.deviceId, navigator = navigator)
    }
}

fun buildImageLoader(context: Context, deviceManager: DeviceManager): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(AppIconFetcher.Factory(context))
            add(MprisAlbumArtFetcher.Factory(deviceManager))
            add(MprisAlbumArtFetcher.Keyer())
        }
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("album_art"))
                .maxSizeBytes(5 * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

val appModule = module {
    single<KdeConnect> { get<Context>() as KdeConnect }
    single<BackgroundServiceData>()
    includes(homeModule, deviceModule, pluginSettingsModule, presenterModule, mprisModule,
        mousePadModule, runCommandModule, digitizerModule, settingsModule, aboutModule)

    single {
        val startDestination = if (PermissionHelper.hasRequiredPermissions(get())) {
            HomeKey
        } else {
            PermissionsScreenKey
        }
        Navigator(startDestination)
    }
    single<ImageLoader> { buildImageLoader(get(), get()) }

    single<TrustedNetworkHelper>()
    single<PermissionRequestHelper>()
    single<AppDatabase>()

    single<DevicesRoomDatabase> {
        Room.databaseBuilder(
            androidContext(),
            DevicesRoomDatabase::class.java,
            "Devices"
        ).build()
    }
    single<DeviceDao> { get<DevicesRoomDatabase>().deviceDao() }

    factory<Device>()

    single<MprisMediaSession>()
    factory<BluetoothLinkProvider>()
    factory<LanLinkProvider>()
    factory<LoopbackLinkProvider>()

    scope<Device> {
        scoped { SftpPlugin(get(), get(), get(), get()) }
        scoped { BatteryPlugin(get(), get()) }
        scoped { ClipboardPlugin(get(), get()) }
        scoped { ConnectivityReportPlugin(get(), get()) }
        scoped { ContactsPlugin(get(), get()) }
        scoped { FindMyPhonePlugin(get(), get(), get(), get()) }
        scoped { FindRemoteDevicePlugin(get(), get()) }
        scoped { InputDevicesReceiverPlugin(get(), get(), get()) }
        scoped { MousePadPlugin(get(), get()) }
        scoped { MouseReceiverPlugin(get(), get(), get()) }
        scoped { MprisPlugin(get(), get(), get(), get(), get()) }
        scoped { MprisReceiverPlugin(get(), get()) }
        scoped { NotificationsPlugin(get(), get(), get(), get()) }
        scoped { PresenterPlugin(get(), get()) }
        scoped { ReceiveNotificationsPlugin(get(), get()) }
        scoped { RemoteKeyboardPlugin(get(), get(), get()) }
        scoped { RunCommandPlugin(get(), get(), get()) }
        scoped { SharePlugin(get(), get(), get()) }
        scoped { SMSPlugin(get(), get(), get(), get()) }
        scoped { SystemVolumePlugin(get(), get()) }
        scoped { TelephonyPlugin(get(), get(), get()) }
        scoped { DigitizerPlugin(get(), get()) }
    }
}
