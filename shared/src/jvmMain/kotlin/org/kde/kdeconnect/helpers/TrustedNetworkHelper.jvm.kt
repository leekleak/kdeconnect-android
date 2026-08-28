package org.kde.kdeconnect.helpers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

actual class TrustedNetworkHelper {
    actual val isTrustedNetwork: Flow<Boolean>
        get() = flowOf(true)
}