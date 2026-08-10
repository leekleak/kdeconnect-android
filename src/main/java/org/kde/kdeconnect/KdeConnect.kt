/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.app.Application
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.kde.kdeconnect.di.appModule
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.helpers.NotificationHelper
import org.kde.kdeconnect.helpers.security.EcHelper
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.ui.ThemeUtil
import org.kde.kdeconnect_tp.BuildConfig
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.slf4j.impl.HandroidLoggerAdapter


/*
 * This class holds all the active devices and makes them accessible from every other class.
 * It also takes care of initializing all classes that need so when the app boots.
 * It provides a ConnectionReceiver that the BackgroundService uses to ping this class every time a new DeviceLink is created.
 */
class KdeConnect : Application() {
    private val deviceHelper: DeviceHelper by inject()
    private val themeUtil: ThemeUtil by inject()
    private val sslHelper: SslHelper by inject()

    override fun onCreate() {
        super.onCreate()
        setupSL4JLogging()
        startKoin {
            androidContext(this@KdeConnect)
            modules(appModule)
        }
        Log.d("KdeConnect/Application", "onCreate")
        themeUtil.setUserPreferredTheme(this)
        runBlocking { deviceHelper.initializeDeviceId() }
        EcHelper.ensureKeyPair()
        runBlocking { sslHelper.initialiseCertificate(this@KdeConnect) }
        NotificationHelper.initializeChannels(this)

        if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            StrictMode.setVmPolicy(
                VmPolicy.Builder(StrictMode.getVmPolicy()).apply {
                    detectActivityLeaks()
                    detectLeakedClosableObjects()
                    detectLeakedRegistrationObjects()
                    detectFileUriExposure()
                    detectContentUriWithoutPermission()
                    detectCredentialProtectedWhileLocked()
                    detectIncorrectContextUse()
                    detectUnsafeIntentLaunch()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        detectBlockedBackgroundActivityLaunch()
                    }
                    penaltyLog()
                }.build()
            )
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder(StrictMode.getThreadPolicy())
                    .detectUnbufferedIo()
                    .detectResourceMismatches()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun setupSL4JLogging() {
        HandroidLoggerAdapter.DEBUG = BuildConfig.DEBUG
        HandroidLoggerAdapter.ANDROID_API_LEVEL = Build.VERSION.SDK_INT
        HandroidLoggerAdapter.APP_NAME = "KDEConnect"
    }

    override fun onTerminate() {
        Log.d("KdeConnect/Application", "onTerminate")
        super.onTerminate()
    }
}
