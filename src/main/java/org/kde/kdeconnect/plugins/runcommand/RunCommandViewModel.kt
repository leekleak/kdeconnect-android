package org.kde.kdeconnect.plugins.runcommand

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect_tp.R
import org.koin.core.annotation.InjectedParam

class RunCommandViewModel(
    deviceManager: DeviceManager,
    @InjectedParam val deviceId: String
) : ViewModel() {

    val commandList = mutableStateListOf<RunCommand>()
    val plugin: RunCommandPlugin? = deviceManager.getDevicePlugin(deviceId, RunCommandPlugin::class.java)
    val device: Device? = deviceManager.getDevice(deviceId)

    init {
        updateList()
    }

    fun updateList() {
        commandList.clear()
        val plugin = plugin ?: return

        commandList.addAll(plugin.commandList)
        commandList.sortBy { it.name.lowercase() }
    }

    fun copyCommandToClipboard(
        context: Context,
        command: RunCommand,
        clipboardManager: Clipboard
    ) {
        val deviceId = deviceId
        val url = "kdeconnect://runcommand/$deviceId/${command.key}"
        val clipData = ClipData.newPlainText("Command", url)

        CoroutineScope(Dispatchers.IO).launch {
            clipboardManager.setClipEntry(clipData.toClipEntry())
        }
        Toast.makeText(
            context,
            R.string.clipboard_toast,
            Toast.LENGTH_SHORT
        ).show()
    }
}
