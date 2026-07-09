package org.kde.kdeconnect.plugins.runcommand

import android.app.Application
import android.content.ClipData
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect_tp.R
import org.koin.core.annotation.InjectedParam

class RunCommandViewModel(
    application: Application,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application) {

    val commandList = mutableStateListOf<CommandEntry>()
    val plugin: RunCommandPlugin? = KdeConnect.getInstance().getDevicePlugin(deviceId, RunCommandPlugin::class.java)
    val device: Device? = KdeConnect.getInstance().getDevice(deviceId)

    init {
        updateList()
    }

    fun updateList() {
        commandList.clear()
        val plugin = plugin ?: return

        for (obj in plugin.commandList) {
            try {
                commandList.add(CommandEntry(obj))
            } catch (e: JSONException) {
                Log.e("RunCommand", "Error parsing JSON", e)
            }
        }
        commandList.sortBy { it.name.lowercase() }
    }

    fun copyCommandToClipboard(
        command: CommandEntry,
        clipboardManager: Clipboard
    ) {
        val deviceId = deviceId
        val url = "kdeconnect://runcommand/$deviceId/${command.key}"
        val clipData = ClipData.newPlainText("Command", url)

        CoroutineScope(Dispatchers.IO).launch {
            clipboardManager.setClipEntry(clipData.toClipEntry())
        }
        Toast.makeText(
            getApplication(),
            R.string.clipboard_toast,
            Toast.LENGTH_SHORT
        ).show()
    }
}
