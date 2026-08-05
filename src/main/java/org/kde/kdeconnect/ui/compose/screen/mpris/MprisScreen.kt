package org.kde.kdeconnect.ui.compose.screen.mpris

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MprisScreen(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.currentTab = page
        }
    }

    HazeScaffold(
        title = stringResource(R.string.open_mpris_controls),
        scrollState = null,
        backButton = true,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.mpris_play)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.devices)) }
                )
            }
            HorizontalPager(
                modifier = Modifier.weight(1f).background(Color.Red),
                state = pagerState,
            ) { page ->
                when (page) {
                    0 -> MprisNowPlayingScreen(deviceId = deviceId)
                    1 -> MprisSystemVolumeScreen(deviceId = deviceId)
                }
            }
        }
    }
}
