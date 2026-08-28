package org.kde.kdeconnect.ui.screen.home

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.HomeKey
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation

@OptIn(KoinExperimentalAPI::class)
val homeModule = module {
    factory { HomeViewModel(get(), get(), get()) }
    navigation<HomeKey> {
        val viewModel: HomeViewModel = koinViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val navigator: Navigator = get()
        HomeScreen(
            uiState = state,
            navigator = navigator,
            onClick = { deviceId -> navigator.goTo(DeviceKey(deviceId, true)) },
            onRefresh = { viewModel.onRefresh() },
            onNavigateToPairingScreen = { navigator.goTo(PairingKey) },
            onNavigateToSettingsScreen = { navigator.goTo(SettingsKey) }
        )
    }
}
