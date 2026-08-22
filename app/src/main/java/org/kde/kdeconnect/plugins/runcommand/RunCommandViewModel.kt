package org.kde.kdeconnect.plugins.runcommand

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect_tp.R
import org.koin.core.annotation.InjectedParam

class RunCommandViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    private val _state: MutableStateFlow<RunCommandViewModeState> = MutableStateFlow(RunCommandViewModeState())
    val state: StateFlow<RunCommandViewModeState> = _state.asStateFlow()

    private val pluginFlow: Flow<RunCommandPlugin?> = deviceManager.getDevicePluginFlow(deviceId, RunCommandPlugin::class.java)
    val plugin: StateFlow<RunCommandPlugin?> = pluginFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )
    val device: Device? = deviceManager.getDevice(deviceId)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<RunCommandViewModeState> = pluginFlow.flatMapLatest { plugin ->
        if (plugin == null) flowOf(RunCommandViewModeState())
        else combine(
            plugin.commandList,
            plugin.canAddCommand
        ) { commandList, canAdd ->
            RunCommandViewModeState(
                commandList = commandList,
                canAddCommands = canAdd
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RunCommandViewModeState()
    )

    fun copyCommandToClipboard(
        context: Context,
        command: RunCommand,
        clipboardManager: Clipboard
    ) {
        val deviceId = deviceId
        val url = "kdeconnect://runcommand/$deviceId/${command.key}"
        val clipData = ClipData.newPlainText("Command", url)

        viewModelScope.launch(Dispatchers.IO) {
            clipboardManager.setClipEntry(clipData.toClipEntry())
        }
        Toast.makeText(
            context,
            R.string.clipboard_toast,
            Toast.LENGTH_SHORT
        ).show()
    }

    fun runCommand(cmdKey: String) = viewModelScope.launch { plugin.value?.runCommand(cmdKey) }
    fun sendStop() = viewModelScope.launch { plugin.value?.sendStop() }
    fun sendSetupPacket() = viewModelScope.launch { plugin.value?.sendSetupPacket() }
}

data class RunCommandViewModeState(
    val commandList: List<RunCommand> = emptyList(),
    val canAddCommands: Boolean = false
)
