package org.kde.kdeconnect.di

import androidx.room3.Room
import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
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
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.helpers.DeviceDao
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.DevicesRoomDatabase
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.helpers.VideoUrlsHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.battery.BatteryPlugin
import org.kde.kdeconnect.plugins.clipboard.ClipboardPlugin
import org.kde.kdeconnect.plugins.connectivityreport.ConnectivityReportPlugin
import org.kde.kdeconnect.plugins.contacts.ContactsPlugin
import org.kde.kdeconnect.plugins.digitizer.DigitizerPlugin
import org.kde.kdeconnect.plugins.findmyphone.FindMyPhonePlugin
import org.kde.kdeconnect.plugins.findremotedevice.FindRemoteDevicePlugin
import org.kde.kdeconnect.plugins.inputdevicesreceiver.InputDevicesReceiverPlugin
import org.kde.kdeconnect.plugins.mousepad.MousePadPlugin
import org.kde.kdeconnect.plugins.mousereceiver.MouseReceiverPlugin
import org.kde.kdeconnect.plugins.mpris.MprisMediaSession
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.mprisreceiver.MprisReceiverPlugin
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.kde.kdeconnect.plugins.notifications.NotificationsPlugin
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.receivenotifications.ReceiveNotificationsPlugin
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin
import org.kde.kdeconnect.plugins.runcommand.RunCommandPlugin
import org.kde.kdeconnect.plugins.sftp.SftpPlugin
import org.kde.kdeconnect.plugins.share.SharePlugin
import org.kde.kdeconnect.plugins.sms.SMSPlugin
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.kde.kdeconnect.plugins.telephony.TelephonyPlugin
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.single

val sharedModule = module {
    single<BackgroundServiceData>()
    single<TelephonySettingsDataStore>()
    single<SettingsDataStore>()
    single<RunCommandSettingsDataStore>()
    single<MousePadSettingsDataStore>()
    single<DeviceSettings>()
    single<DeviceHelper>()
    single<SslHelper>()
    single<CustomDevicesHelper>()
    single<VideoUrlsHelper>()
    single<DeviceManager> {
        DeviceManager(get()) { deviceInfo ->
            get<Device> { org.koin.core.parameter.parametersOf(deviceInfo) }
        }
    }
    single<NotificationSettingsDataStore>()
    single<AppDatabase>()
    single<SftpSettingsDataStore>()
    single<ConnectionsSettingsDataStore>()
    single<TrustedNetworkHelper>()
    single<PermissionRequestHelper>()
    single<MprisMediaSession>()

    single<DevicesRoomDatabase> {
        Room.databaseBuilder(
            androidContext(),
            DevicesRoomDatabase::class.java,
            "Devices"
        ).build()
    }
    single<DeviceDao> { get<DevicesRoomDatabase>().deviceDao() }

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
