package org.kde.kdeconnect.ui.compose.screen.mpris

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.helpers.VideoUrlsHelper
import org.kde.kdeconnect.helpers.calculateNewVolume
import org.kde.kdeconnect.plugins.mpris.MprisMediaSession
import org.kde.kdeconnect.plugins.mpris.MprisPlayerState
import org.kde.kdeconnect.plugins.mpris.MprisPlugin
import org.kde.kdeconnect.plugins.systemvolume.Sink
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.koin.core.annotation.InjectedParam
import kotlin.time.Duration.Companion.seconds

class MprisViewModel(
    application: Application,
    deviceManager: DeviceManager,
    private val mprisMediaSession: MprisMediaSession,
    val videoUrlsHelper: VideoUrlsHelper,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application) {

    val plugin: MprisPlugin? = deviceManager.getDevicePlugin(deviceId, MprisPlugin::class.java)
    val systemVolumePlugin: SystemVolumePlugin? = deviceManager.getDevicePlugin(deviceId, SystemVolumePlugin::class.java)

    val playerList: StateFlow<List<String>> = plugin?.players?.map { it.keys.sorted() }
        ?.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        ?: MutableStateFlow(emptyList())

    private val _selectedPlayerName = MutableStateFlow<String?>(null)
    val selectedPlayerName: StateFlow<String?> = _selectedPlayerName.asStateFlow()

    val playerStatus: StateFlow<MprisPlayerState?> = combine(
        plugin?.players ?: MutableStateFlow(emptyMap()),
        _selectedPlayerName
    ) { players, selectedName ->
        players[selectedName]
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _playerPosition = MutableStateFlow(0L)
    val playerPosition: StateFlow<Long> = _playerPosition.asStateFlow()

    val sinks: StateFlow<List<Sink>> = systemVolumePlugin?.sinks
        ?: MutableStateFlow(emptyList())

    var currentTab = 0

    init {
        viewModelScope.launch {
            playerList.collect { list ->
                if (_selectedPlayerName.value == null || !list.contains(_selectedPlayerName.value)) {
                    val playing = plugin?.playingPlayer
                    val first = if (list.isNotEmpty()) list[0] else null
                    selectPlayer(playing?.playerName ?: first)
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                val status = playerStatus.value
                if (status != null && status.isPlaying) {
                    _playerPosition.value = status.position
                }
                delay(1.seconds)
            }
        }
        
        viewModelScope.launch {
            playerStatus.collect { status ->
                _playerPosition.value = status?.position ?: 0L
            }
        }
    }

    fun selectPlayer(playerName: String?) {
        _selectedPlayerName.value = playerName
        
        val status = playerStatus.value
        if (status?.isPlaying == true) {
            mprisMediaSession.playerSelected(status)
        }
    }

    fun onVolumeUp() {
        onVolumeChange(5)
    }

    fun onVolumeDown() {
        onVolumeChange(-5)
    }

    private fun onVolumeChange(step: Int) {
        if (currentTab == 0) {
            val status = playerStatus.value ?: return
            val newVolume = calculateNewVolume(status.volume, 100, step)
            if (status.volume != newVolume) {
                plugin?.sendSetVolume(status.playerName, newVolume)
            }
        } else {
            val defaultSink = sinks.value.firstOrNull { it.isDefault } ?: return
            val newVolume = calculateNewVolume(defaultSink.volume, defaultSink.maxVolume, step)
            if (defaultSink.volume != newVolume) {
                systemVolumePlugin?.sendVolume(defaultSink.name, newVolume)
            }
        }
    }
}
