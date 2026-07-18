/*
 * SPDX-FileCopyrightText: 2017 Holger Kaelberer <holger.k@elberer.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.remotekeyboard

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import androidx.core.util.Pair
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.helpers.SPECIAL_KEY_MAP
import org.kde.kdeconnect.plugins.Plugin
import java.util.concurrent.locks.ReentrantLock

class RemoteKeyboardPlugin(context: Context, device: Device) : Plugin(context, device) {
    override val pluginInfo: RemoteKeyboardPluginInfo = RemoteKeyboardPluginInfo

    override fun onCreate(): Boolean {
        Log.d("RemoteKeyboardPlugin", "Creating for device " + device.name)
        acquireInstances()
        try {
            instances.add(this)
        } finally {
            releaseInstances()
        }
        if (RemoteKeyboardService.instance != null) RemoteKeyboardService.instance?.handler?.post { RemoteKeyboardService.instance?.updateInputView() }

        val visible = RemoteKeyboardService.instance != null && RemoteKeyboardService.instance?.visible == true
        notifyKeyboardState(visible)

        return true
    }

    override fun onDestroy() {
        acquireInstances()
        try {
            if (instances.contains(this)) {
                instances.remove(this)
                if (instances.isEmpty() && RemoteKeyboardService.instance != null)
                    RemoteKeyboardService.instance?.handler?.post { RemoteKeyboardService.instance?.updateInputView() }
            }
        } finally {
            releaseInstances()
        }

        Log.d("RemoteKeyboardPlugin", "Destroying for device " + device.name)
    }

    private fun isValidSpecialKey(key: Int): Boolean {
        return ((SPECIAL_KEY_MAP[key] ?: 0) > 0)
    }

    private fun getCharPos(extractedText: ExtractedText?, forward: Boolean): Int {
        var pos = -1
        if (extractedText != null) {
            pos = if (!forward)  // backward
                extractedText.text.toString().lastIndexOf(" ", extractedText.selectionEnd - 2)
            else extractedText.text.toString().indexOf(" ", extractedText.selectionEnd + 1)
            return pos
        }
        return pos
    }

    private fun currentTextLength(extractedText: ExtractedText?): Int {
        if (extractedText != null) return extractedText.text.length
        return -1
    }

    private fun currentCursorPos(extractedText: ExtractedText?): Int {
        if (extractedText != null) return extractedText.selectionEnd
        return -1
    }

    private fun currentSelection(extractedText: ExtractedText?): Pair<Int, Int> {
        if (extractedText != null) return Pair(
            extractedText.selectionStart,
            extractedText.selectionEnd
        )
        return Pair(-1, -1)
    }

    private fun handleSpecialKey(key: Int, shift: Boolean, ctrl: Boolean): Boolean {
        val keyEvent: Int = SPECIAL_KEY_MAP[key] ?: 0
        if (keyEvent == 0) return false
        val inputConn = RemoteKeyboardService.instance?.currentInputConnection ?: return false

        //        Log.d("RemoteKeyboardPlugin", "Handling special key " + key + " translated to " + keyEvent + " shift=" + shift + " ctrl=" + ctrl + " alt=" + alt);

        // special sequences:
        if (ctrl && (keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // Ctrl + right -> next word
            val extractedText = inputConn.getExtractedText(ExtractedTextRequest(), 0)
            var pos = getCharPos(extractedText, true)
            if (pos == -1) pos = currentTextLength(extractedText)
            else pos++
            var startPos = pos
            val endPos = pos
            if (shift) { // Shift -> select word (otherwise jump)
                val sel = currentSelection(extractedText)
                val cursor = currentCursorPos(extractedText)
                //                Log.d("RemoteKeyboardPlugin", "Selection (to right): " + sel.first + " / " + sel.second + " cursor: " + cursor);
                startPos = cursor
                if (sel.first < cursor ||  // active selection from left to right -> grow
                    sel.first > sel.second
                )  // active selection from right to left -> shrink
                    startPos = sel.first
            }
            inputConn.setSelection(startPos, endPos)
        } else if (ctrl && keyEvent == KeyEvent.KEYCODE_DPAD_LEFT) {
            // Ctrl + left -> previous word
            val extractedText = inputConn.getExtractedText(ExtractedTextRequest(), 0)
            var pos = getCharPos(extractedText, false)
            if (pos == -1) pos = 0
            else pos++
            var startPos = pos
            val endPos = pos
            if (shift) {
                val sel = currentSelection(extractedText)
                val cursor = currentCursorPos(extractedText)
                //                Log.d("RemoteKeyboardPlugin", "Selection (to left): " + sel.first + " / " + sel.second + " cursor: " + cursor);
                startPos = cursor
                if (cursor < sel.first ||  // active selection from right to left -> grow
                    sel.first < sel.second
                )  // active selection from right to left -> shrink
                    startPos = sel.first
            }
            inputConn.setSelection(startPos, endPos)
        } else if (shift
            && (keyEvent == KeyEvent.KEYCODE_DPAD_LEFT || keyEvent == KeyEvent.KEYCODE_DPAD_RIGHT || keyEvent == KeyEvent.KEYCODE_DPAD_UP || keyEvent == KeyEvent.KEYCODE_DPAD_DOWN || keyEvent == KeyEvent.KEYCODE_MOVE_HOME || keyEvent == KeyEvent.KEYCODE_MOVE_END)
        ) {
            // Shift + up/down/left/right/home/end
            val now = SystemClock.uptimeMillis()
            inputConn.sendKeyEvent(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    0,
                    0
                )
            )
            inputConn.sendKeyEvent(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_DOWN,
                    keyEvent,
                    0,
                    KeyEvent.META_SHIFT_LEFT_ON
                )
            )
            inputConn.sendKeyEvent(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_UP,
                    keyEvent,
                    0,
                    KeyEvent.META_SHIFT_LEFT_ON
                )
            )
            inputConn.sendKeyEvent(
                KeyEvent(
                    now,
                    now,
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_SHIFT_LEFT,
                    0,
                    0
                )
            )
        } else if (keyEvent == KeyEvent.KEYCODE_NUMPAD_ENTER
            || keyEvent == KeyEvent.KEYCODE_ENTER
        ) {
            // Enter key
            val editorInfo = RemoteKeyboardService.instance?.currentInputEditorInfo
            //            Log.d("RemoteKeyboardPlugin", "Enter: " + editorInfo.imeOptions);
            if (editorInfo != null
                && (((editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0)
                        || ctrl)
            ) {  // Ctrl+Return overrides IME_FLAG_NO_ENTER_ACTION (FIXME: make configurable?)
                // check for special DONE/GO/etc actions first:
                val actions = intArrayOf(
                    EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_DONE
                ) // note: DONE should be last or we might hide the ime instead of "go"
                for (action in actions) {
                    if ((editorInfo.imeOptions and action) == action) {
//                        Log.d("RemoteKeyboardPlugin", "Enter-action: " + actions[i]);
                        inputConn.performEditorAction(action)
                        return true
                    }
                }
            } else {
                // else: fall back to regular Enter-event:
//                Log.d("RemoteKeyboardPlugin", "Enter: normal keypress");
                inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEvent))
                inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEvent))
            }
        } else {
            // default handling:
            inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyEvent))
            inputConn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyEvent))
        }

        return true
    }

    private fun handleVisibleKey(
        key: String,
        ctrl: Boolean
    ): Boolean {
//        Log.d("RemoteKeyboardPlugin", "Handling visible key " + key + " shift=" + shift + " ctrl=" + ctrl + " alt=" + alt + " " + key.equalsIgnoreCase("c") + " " + key.length());

        if (key.isEmpty()) return false

        val inputConn = RemoteKeyboardService.instance?.currentInputConnection ?: return false

        // ctrl+c/v/x
        if (key.equals("c", ignoreCase = true) && ctrl) {
            return inputConn.performContextMenuAction(android.R.id.copy)
        } else if (key.equals(
                "v",
                ignoreCase = true
            ) && ctrl
        ) return inputConn.performContextMenuAction(android.R.id.paste)
        else if (key.equals(
                "x",
                ignoreCase = true
            ) && ctrl
        ) return inputConn.performContextMenuAction(android.R.id.cut)
        else if (key.equals(
                "a",
                ignoreCase = true
            ) && ctrl
        ) return inputConn.performContextMenuAction(android.R.id.selectAll)

        //        Log.d("RemoteKeyboardPlugin", "Committing visible key '" + key + "'");
        inputConn.commitText(key, key.length)
        return true
    }

    private fun handleEvent(np: NetworkPacket): Boolean {
        if (np.has("specialKey") && isValidSpecialKey(np.getInt("specialKey"))) return handleSpecialKey(
            np.getInt("specialKey"), np.getBoolean("shift"),
            np.getBoolean("ctrl")
        )

        // try visible key
        return handleVisibleKey(
            np.getString("key"),
            np.getBoolean("ctrl")
        )
    }


    enum class MousePadPacketType {
        Keyboard,
        Mouse,
    }

    override fun onPacketReceived(np: NetworkPacket): Boolean {
        if (np.type != PACKET_TYPE_MOUSEPAD_REQUEST) {
            Log.e(
                "RemoteKeyboardPlugin",
                "Invalid packet type for RemoteKeyboardPlugin: " + np.type
            )
            return false
        }

        if (getMousePadPacketType(np) != MousePadPacketType.Keyboard) {
            return false // This packet will be handled by the MouseReceiverPlugin instead, silently ignore
        }

        if (!pluginInfo.checkRequiredPermissions(context)) {
            pluginInfo.showPermissionExplanation(context, deviceId)
            return false
        }

        if (RemoteKeyboardService.instance == null) {
            Log.i(
                "RemoteKeyboardPlugin",
                "Remote keyboard is not the currently selected input method, dropping key"
            )
            return false
        }

        if (RemoteKeyboardService.instance?.visible == false) {
            Log.i("RemoteKeyboardPlugin", "Remote keyboard is currently not visible, dropping key")
            return false
        }

        if (!handleEvent(np)) {
            Log.i("RemoteKeyboardPlugin", "Could not handle event!")
            return false
        }

        if (np.getBoolean("sendAck")) {
            val reply = NetworkPacket(PACKET_TYPE_MOUSEPAD_ECHO)
            reply["key"] = np.getString("key")
            if (np.has("specialKey")) reply["specialKey"] = np.getInt("specialKey")
            if (np.has("shift")) reply["shift"] = np.getBoolean("shift")
            if (np.has("ctrl")) reply["ctrl"] = np.getBoolean("ctrl")
            if (np.has("alt")) reply["alt"] = np.getBoolean("alt")
            reply["isAck"] = true
            device.sendPacket(reply)
        }

        return true
    }

    fun notifyKeyboardState(state: Boolean) {
        Log.d("RemoteKeyboardPlugin", "Keyboardstate changed to $state")
        val np = NetworkPacket(PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE)
        np["state"] = state
        device.sendPacket(np)
    }

    companion object {
        const val PACKET_TYPE_MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
        const val PACKET_TYPE_MOUSEPAD_ECHO = "kdeconnect.mousepad.echo"
        const val PACKET_TYPE_MOUSEPAD_KEYBOARDSTATE = "kdeconnect.mousepad.keyboardstate"

        /**
         * Track and expose plugin instances to allow for a 'connected'-indicator in the IME:
         */
        private val instances = ArrayList<RemoteKeyboardPlugin>()
        private val instancesLock = ReentrantLock(true)

        @JvmStatic
        fun acquireInstances(): ArrayList<RemoteKeyboardPlugin> {
            instancesLock.lock()
            return instances
        }

        @JvmStatic
        fun releaseInstances(): ArrayList<RemoteKeyboardPlugin> {
            instancesLock.unlock()
            return instances
        }

        @JvmStatic
        val isConnected: Boolean
            get() = !instances.isEmpty()

        fun getMousePadPacketType(np: NetworkPacket): MousePadPacketType {
            return if (np.has("key") || np.has("specialKey")) {
                MousePadPacketType.Keyboard
            } else {
                MousePadPacketType.Mouse
            }
        }
    }
}
