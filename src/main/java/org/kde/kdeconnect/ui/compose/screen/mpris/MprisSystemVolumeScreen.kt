package org.kde.kdeconnect.ui.compose.screen.mpris

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.kde.kdeconnect.plugins.systemvolume.Sink
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MprisSystemVolumeScreen(
    deviceId: String,
    viewModel: MprisViewModel = koinViewModel(key = "MprisViewModel_$deviceId") { parametersOf(deviceId) }
) {
    val sinks by viewModel.sinks.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sinks) { sink ->
            SinkItem(sink = sink, viewModel = viewModel)
        }
    }
}

@Composable
private fun SinkItem(
    sink: Sink,
    viewModel: MprisViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = sink.isDefault,
                    onClick = { if (!sink.isDefault) viewModel.setSinkEnabled(sink.name) }
                )
                Text(
                    text = sink.description,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleSinkMute(sink.name, sink.isMuted) }) {
                    val icon = if (sink.isMuted) R.drawable.ic_volume_mute else R.drawable.ic_volume
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = stringResource(R.string.mpris_volume)
                    )
                }
            }
            Slider(
                value = sink.volume.toFloat(),
                onValueChange = { viewModel.setSinkVolume(sink.name, it.toInt()) },
                valueRange = 0f..sink.maxVolume.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
