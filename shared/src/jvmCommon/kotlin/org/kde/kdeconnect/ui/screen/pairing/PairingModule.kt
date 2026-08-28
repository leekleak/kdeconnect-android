package org.kde.kdeconnect.ui.screen.pairing

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.plugin.module.dsl.viewModel

@OptIn(KoinExperimentalAPI::class)
val pairingModule = module {
    viewModel<PairingViewModel>()
    navigation<PairingKey> {
        val viewModel: PairingViewModel = koinViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        PairingScreen(
            uiState = state,
            onClick = { viewModel.pair(it) },
            onRefresh = { viewModel.onRefresh() },
            navigator = get(),
        )
    }
}
