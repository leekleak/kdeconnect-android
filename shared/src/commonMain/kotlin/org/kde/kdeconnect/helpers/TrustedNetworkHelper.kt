package org.kde.kdeconnect.helpers

import kotlinx.coroutines.flow.Flow

expect class TrustedNetworkHelper {
    val isTrustedNetwork: Flow<Boolean>
}