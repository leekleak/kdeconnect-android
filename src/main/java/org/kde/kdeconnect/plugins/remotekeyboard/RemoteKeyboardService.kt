/*
 * SPDX-FileCopyrightText: 2017 Holger Kaelberer <holger.k@elberer.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.remotekeyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin.Companion.acquireInstances
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin.Companion.isConnected
import org.kde.kdeconnect.plugins.remotekeyboard.RemoteKeyboardPlugin.Companion.releaseInstances
import org.kde.kdeconnect.ui.MainActivity
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.kde.kdeconnect_tp.R

class RemoteKeyboardService: InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /**
     * Whether this InputMethod is currently visible.
     */
    var visible: Boolean = false

    private val isConnectedState = mutableStateOf(isConnected)

    val handler: Handler = Handler(Looper.getMainLooper())

    fun updateInputView() {
        isConnectedState.value = isConnected
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        window.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        visible = false
        instance = this
        Log.d("RemoteKeyboardService", "Remote keyboard initialized")
    }

    override fun onWindowShown() {
        super.onWindowShown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        instance = null
        Log.d("RemoteKeyboardService", "Destroyed")
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@RemoteKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@RemoteKeyboardService)

            setContent {
                KdeTheme(context = this@RemoteKeyboardService) {
                    RemoteKeyboardContent(
                        isConnected = isConnectedState.value,
                        onKeyPress = { onPress(it) }
                    )
                }
            }
        }
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        visible = true
        val instances = acquireInstances()
        try {
            for (i in instances) i.notifyKeyboardState(true)
        } finally {
            releaseInstances()
        }

        window.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        visible = false
        val instances = acquireInstances()
        try {
            for (i in instances) i.notifyKeyboardState(false)
        } finally {
            releaseInstances()
        }

        window.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    enum class KeyboardAction {
        HIDE,
        SETTINGS,
        SELECT_KEYBOARD,
        CHECK_CONNECTION
    }
    fun onPress(primaryCode: KeyboardAction) {
        when (primaryCode) {
            KeyboardAction.HIDE -> {
                requestHideSelf(0)
            }

            KeyboardAction.SETTINGS -> {
                val instances = acquireInstances()
                try {
                    if (instances.size == 1) {  // single instance of RemoteKeyboardPlugin -> access its settings
                        val plugin: RemoteKeyboardPlugin = instances[0]
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        intent.putExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID, plugin.deviceId)
                        intent.putExtra(
                            KdeConnectKeyConstants.EXTRA_PLUGIN_KEY,
                            plugin.pluginKey
                        )
                        startActivity(intent)
                    } else { // != 1 instance of plugin -> show main activity view
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra(MainActivity.FLAG_FORCE_OVERVIEW, true)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        if (instances.isEmpty()) Toast.makeText(
                            this,
                            R.string.remotekeyboard_not_connected,
                            Toast.LENGTH_SHORT
                        ).show()
                        else  // instances.size() > 1
                            Toast.makeText(
                                this,
                                R.string.remotekeyboard_multiple_connections,
                                Toast.LENGTH_SHORT
                            ).show()
                    }
                } finally {
                    releaseInstances()
                }
            }

            KeyboardAction.SELECT_KEYBOARD -> {
                val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
                imm?.showInputMethodPicker()
            }

            KeyboardAction.CHECK_CONNECTION -> {
                if (isConnected) Toast.makeText(
                    this,
                    R.string.remotekeyboard_connected,
                    Toast.LENGTH_SHORT
                ).show()
                else Toast.makeText(this, R.string.remotekeyboard_not_connected, Toast.LENGTH_SHORT).show()
            }
        }
    }


    companion object {
        /**
         * Reference to our instance
         * null if this InputMethod is not currently selected.
         */
        var instance: RemoteKeyboardService? = null
    }
}
