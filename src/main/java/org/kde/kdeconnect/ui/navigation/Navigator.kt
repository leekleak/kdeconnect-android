package org.kde.kdeconnect.ui.navigation

import androidx.compose.runtime.mutableStateListOf

class Navigator(startDestination: KdeConnectKey = PairingKey) {
    val backStack = mutableStateListOf(startDestination)

    fun goTo(destination: KdeConnectKey) {
        backStack.add(destination)
    }

    fun setTo(destination: KdeConnectKey) {
        backStack.clear().also { backStack.add(destination) }
    }

    fun goBack() {
        backStack.removeLastOrNull()
    }
}
