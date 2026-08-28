package org.kde.kdeconnect.di

import org.kde.kdeconnect.BackgroundServiceData
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.device.DeviceManager
import org.kde.kdeconnect.helpers.TrustedNetworkHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val jvmSharedModule = module {
    single<BackgroundServiceData>()
    single<SslHelper>()
    single<DeviceManager> {
        DeviceManager(get()) { deviceInfo ->
            get<Device> { parametersOf(deviceInfo) }
        }
    }
    single<TrustedNetworkHelper>()
}
