package org.kde.kdeconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.kde.kdeconnect.backends.BaseLinkProvider
import org.kde.kdeconnect.backends.BaseLinkProvider.ConnectionReceiver
import org.kde.kdeconnect.backends.http.HttpLinkProvider
import org.kde.kdeconnect.device.DeviceManager
import org.koin.core.component.KoinComponent
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class BackgroundService(
    val deviceManager: DeviceManager,
    val httpLinkProvider: HttpLinkProvider
) : KoinComponent {

    private val linkProviders = mutableListOf<BaseLinkProvider>()

    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun registerLinkProviders() {
        linkProviders.add(httpLinkProvider)
    }

    fun addConnectionListener(connectionReceiver: ConnectionReceiver) {
        for (linkProvider in linkProviders) {
            linkProvider.addConnectionReceiver(connectionReceiver)
        }
    }

    init {
        serviceScope.launch {
            registerLinkProviders()
            addConnectionListener(deviceManager.connectionListener) // Link Providers need to be already registered
            for (linkProvider in linkProviders) {
                linkProvider.onStart()
            }
            initialized.store(true)
        }
    }

    companion object {
        val initialized = AtomicBoolean(false)
    }
}