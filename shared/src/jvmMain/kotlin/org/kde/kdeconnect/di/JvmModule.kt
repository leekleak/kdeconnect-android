package org.kde.kdeconnect.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.StringResource
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.PairingHandler
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.datastore.SettingsDefaults
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.helpers.DeviceDao
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.DevicesRoomDatabase
import org.kde.kdeconnect.ui.navigation.HomeKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.screen.home.HomeModule
import org.koin.dsl.module
import java.io.File

val jvmModule = module {
    includes(jvmSharedModule, HomeModule)
    single { Navigator(HomeKey) }
    single<SettingsDefaults> {
        object : SettingsDefaults {
            override fun getDefaultDeviceName(): String = "Desktop"
            override fun getDefaultFileDestination(): String = System.getProperty("user.home")
        }
    }
    single {
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                File(System.getProperty("user.home"), ".kdeconnect.preferences_pb").absolutePath.toPath()
            },
        )
        SettingsDataStore(dataStore, get())
    }

    single<DevicesRoomDatabase> {
        Room.databaseBuilder<DevicesRoomDatabase>(
            name = File(System.getProperty("user.home"), ".kdeconnect.db").absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    single<DeviceDao> { get<DevicesRoomDatabase>().deviceDao() }
    single { DeviceSettings(get()) }

    factory { (deviceInfo: DeviceInfo) ->
        Device(get(), get(), { dummyPairingCallback }, deviceInfo)
    }
}

private val dummyPairingCallback = object : PairingHandler.PairingCallback {
    override fun incomingPairRequest() {}
    override fun pairingFailed(error: StringResource) {}
    override fun pairingSuccessful() {}
    override fun unpaired(device: Device) {}
}