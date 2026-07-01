package org.kde.kdeconnect.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavKey

/**
 * Handles navigation events (forward and back) by updating the navigation state.
 */
class Navigator(startDestination: KdeConnectKey) {
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
