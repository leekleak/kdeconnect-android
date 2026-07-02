package org.kde.kdeconnect.ui.compose.screen.plugin

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.fragment.app.FragmentActivity
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.plugins.PluginFactory
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityPluginSettingsBinding

@Composable
fun PluginIndividualSettingsScreen(
    deviceId: String,
    pluginKey: String
) {
    val context = LocalContext.current
    val pluginInfo = PluginFactory.getPluginInfo(pluginKey)
    
    HazeScaffold(
        title = stringResource(id = R.string.plugin_settings_with_name, pluginInfo.displayName),
        backButton = true,
        scrollState = null
    ) { paddingValues ->
        AndroidViewBinding(
            factory = { inflater, parent, attachToParent ->
                ActivityPluginSettingsBinding.inflate(inflater, parent, attachToParent)
            },
            modifier = Modifier.padding(paddingValues)
        ) {
            this.toolbarLayout.root.visibility = android.view.View.GONE

            val activity = context as? FragmentActivity ?: return@AndroidViewBinding
            val fragmentManager = activity.supportFragmentManager

            // We use the same ID as in activity_plugin_settings.xml
            val containerId = R.id.fragmentPlaceHolder

            if (fragmentManager.findFragmentById(containerId) == null) {
                val device = KdeConnect.getInstance().getDevice(deviceId)
                if (device != null) {
                    val plugin = device.getPluginIncludingWithoutPermissions(pluginKey)
                    if (plugin != null) {
                        val fragment = plugin.getSettingsFragment(activity as Activity)
                        if (fragment != null) {
                            val args = fragment.arguments ?: Bundle()
                            args.putString(KdeConnectKeyConstants.EXTRA_DEVICE_ID, deviceId)
                            fragment.arguments = args

                            fragmentManager.beginTransaction()
                                .replace(containerId, fragment)
                                .commit()
                        }
                    }
                }
            }
        }
    }
}
