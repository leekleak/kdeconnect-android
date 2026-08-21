package org.kde.kdeconnect.plugins.mpris

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.helpers.calculateNewVolume
import org.kde.kdeconnect.plugins.systemvolume.Sink
import org.kde.kdeconnect.plugins.systemvolume.SystemVolumePlugin
import org.koin.core.annotation.InjectedParam
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MprisViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    private val pluginFlow: StateFlow<MprisPlugin?> = deviceManager.getDevicePluginFlow(deviceId, MprisPlugin::class.java)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val systemVolumePluginFlow: StateFlow<SystemVolumePlugin?> = deviceManager.getDevicePluginFlow(deviceId, SystemVolumePlugin::class.java)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerList: StateFlow<List<String>> = pluginFlow.flatMapLatest { plugin ->
        plugin?.players?.map { it.keys.sorted() } ?: flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedPlayerName = MutableStateFlow<String?>(null)
    val selectedPlayerName: StateFlow<String?> = _selectedPlayerName.asStateFlow()

    val playerStatus: StateFlow<MprisPlayerState?> = pluginFlow.flatMapLatest { plugin ->
        if (plugin == null) flowOf(null)
        else combine(
            plugin.players,
            _selectedPlayerName
        ) { players, selectedName ->
            players[selectedName]
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    private val _playerPosition = MutableStateFlow(0L)
    val playerPosition: StateFlow<Long> = _playerPosition.asStateFlow()

    val sinks: StateFlow<List<Sink>> = systemVolumePluginFlow.flatMapLatest {
        it?.sinks ?: flowOf(emptyList())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    var currentTab = 0

    init {
        viewModelScope.launch {
            pluginFlow.collect { it?.requestPlayerList() }
        }
        viewModelScope.launch {
            playerList.collect { list ->
                if (_selectedPlayerName.value == null || !list.contains(_selectedPlayerName.value)) {
                    val playing = pluginFlow.value?.playingPlayer
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
    }

    fun playPause() = viewModelScope.launch { playerStatus.value?.let { pluginFlow.value?.sendPlayPause(it.playerName) } }
    fun next() = viewModelScope.launch { playerStatus.value?.let { pluginFlow.value?.sendNext(it.playerName) } }
    fun previous() = viewModelScope.launch { playerStatus.value?.let { pluginFlow.value?.sendPrevious(it.playerName) } }
    fun stop() = viewModelScope.launch { playerStatus.value?.let { pluginFlow.value?.sendStop(it.playerName) } }
    fun seek(offset: Int) = viewModelScope.launch { playerStatus.value?.let { pluginFlow.value?.sendSeek(it.playerName, offset) } }
    fun toggleShuffle() = viewModelScope.launch { playerStatus.value?.let { pluginFlow.value?.sendSetShuffle(it.playerName, !it.shuffle) } }
    fun setSinkEnabled(name: String) = viewModelScope.launch { systemVolumePluginFlow.value?.sendEnable(name) }
    fun setSinkVolume(name: String, volume: Int) = viewModelScope.launch { systemVolumePluginFlow.value?.sendVolume(name, volume) }

    fun toggleLoopStatus() {
        viewModelScope.launch {
            val status = playerStatus.value ?: return@launch
            val nextStatus = when (status.loopStatus) {
                "None" -> "Track"
                "Track" -> "Playlist"
                "Playlist" -> "None"
                else -> "None"
            }
            pluginFlow.value?.sendSetLoopStatus(status.playerName, nextStatus)
        }
    }

    fun onVolumeUp() {
        onVolumeChange(5)
    }

    fun onVolumeDown() {
        onVolumeChange(-5)
    }

    private fun onVolumeChange(step: Int) {
        viewModelScope.launch {
            if (currentTab == 0) {
                val status = playerStatus.value ?: return@launch
                val newVolume = calculateNewVolume(status.volume, 100, step)
                if (status.volume != newVolume) {
                    pluginFlow.value?.sendSetVolume(status.playerName, newVolume)
                }
            } else {
                val defaultSink = sinks.value.firstOrNull { it.isDefault } ?: return@launch
                val newVolume = calculateNewVolume(defaultSink.volume, defaultSink.maxVolume, step)
                if (defaultSink.volume != newVolume) {
                    systemVolumePluginFlow.value?.sendVolume(defaultSink.name, newVolume)
                }
            }
        }
    }
}
