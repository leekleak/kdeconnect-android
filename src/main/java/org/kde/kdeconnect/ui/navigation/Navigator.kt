package org.kde.kdeconnect.ui.navigation

import androidx.compose.runtime.mutableStateListOf
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class Navigator(startDestination: KdeConnectKey = HomeKey) {
    val backStack = mutableStateListOf(startDestination)
    var forceBasicTransition = AtomicBoolean(false)

    fun goTo(destination: KdeConnectKey) {
        forceBasicTransition.store(false)
        backStack.add(destination)
    }

    fun setTo(destination: KdeConnectKey) {
        forceBasicTransition.store(true)
        backStack.apply { clear(); add(destination) }
    }

    fun setTo(destination: List<KdeConnectKey>) {
        forceBasicTransition.store(true)
        backStack.apply { clear(); addAll(destination) }
    }

    fun goBack() {
        forceBasicTransition.store(false)
        backStack.removeLastOrNull()
    }
}
