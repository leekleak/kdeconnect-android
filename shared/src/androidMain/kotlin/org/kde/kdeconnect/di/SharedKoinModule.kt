package org.kde.kdeconnect.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.kde.kdeconnect.backends.bluetooth.BluetoothLinkProvider
import org.kde.kdeconnect.backends.http.HttpLinkProvider
import org.kde.kdeconnect.backends.lan.LanLinkProvider
import org.kde.kdeconnect.backends.loopback.LoopbackLinkProvider
import org.kde.kdeconnect.datastore.AndroidSettingsDefaults
import org.kde.kdeconnect.datastore.ConnectionsSettingsDataStore
import org.kde.kdeconnect.datastore.MousePadSettingsDataStore
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.datastore.RunCommandSettingsDataStore
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.datastore.SettingsDefaults
import org.kde.kdeconnect.datastore.SftpSettingsDataStore
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.helpers.CustomDevicesHelper
import org.kde.kdeconnect.helpers.DeviceDao
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.DevicesRoomDatabase
import org.kde.kdeconnect.helpers.PermissionRequestHelper
import org.kde.kdeconnect.helpers.VideoUrlsHelper
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
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.plugin.module.dsl.factory
import org.koin.plugin.module.dsl.scoped
import org.koin.plugin.module.dsl.single

val sharedModule = module {
    includes(jvmSharedModule)
    
    single<SettingsDefaults> { AndroidSettingsDefaults(androidContext()) }

    single(named("telephony_settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("telephony_settings") })
    }
    single { TelephonySettingsDataStore(get(named("telephony_settings"))) }

    single(named("settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("settings") })
    }
    single { SettingsDataStore(get(named("settings")), get()) }

    single(named("runcommand_settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("runcommand_settings") })
    }
    single { RunCommandSettingsDataStore(get(named("runcommand_settings"))) }

    single(named("mousepad_settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("mousepad_settings") })
    }
    single { MousePadSettingsDataStore(get(named("mousepad_settings"))) }

    single(named("notification_settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("notification_settings") })
    }
    single { NotificationSettingsDataStore(get(named("notification_settings"))) }

    single(named("sftp_settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("sftp_settings") })
    }
    single { SftpSettingsDataStore(get(named("sftp_settings"))) }

    single(named("connections_settings")) {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("connections_settings") })
    }
    single { ConnectionsSettingsDataStore(get(named("connections_settings"))) }

    single<DeviceSettings>()
    single<DeviceHelper>()
    single<CustomDevicesHelper>()
    single<VideoUrlsHelper>()
    single<AppDatabase>()
    single<PermissionRequestHelper>()
    single<MprisMediaSession>()

    single<DevicesRoomDatabase> {
        Room.databaseBuilder(
            androidContext(),
            DevicesRoomDatabase::class.java,
            "Devices"
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single<DeviceDao> { get<DevicesRoomDatabase>().deviceDao() }

    factory<BluetoothLinkProvider>()
    factory<LanLinkProvider>()
    factory<HttpLinkProvider>()
    factory<LoopbackLinkProvider>()

    scope<Device> {
        scoped<SftpPlugin>()
        scoped<BatteryPlugin>()
        scoped<ClipboardPlugin>()
        scoped<ConnectivityReportPlugin>()
        scoped<ContactsPlugin>()
        scoped<FindMyPhonePlugin>()
        scoped<FindRemoteDevicePlugin>()
        scoped<InputDevicesReceiverPlugin>()
        scoped<MousePadPlugin>()
        scoped<MouseReceiverPlugin>()
        scoped<MprisPlugin>()
        scoped<MprisReceiverPlugin>()
        scoped<NotificationsPlugin>()
        scoped<PresenterPlugin>()
        scoped<ReceiveNotificationsPlugin>()
        scoped<RemoteKeyboardPlugin>()
        scoped<RunCommandPlugin>()
        scoped<SharePlugin>()
        scoped<SMSPlugin>()
        scoped<SystemVolumePlugin>()
        scoped<TelephonyPlugin>()
        scoped<DigitizerPlugin>()
    }
}
