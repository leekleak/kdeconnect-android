package org.kde.kdeconnect.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import org.kde.kdeconnect.DeviceManager
import org.koin.android.ext.android.inject

class PermissionExplanationActivity : AppCompatActivity() {
    private val deviceManager: DeviceManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra("deviceId")
        val pluginKey = intent.getStringExtra("pluginKey")

        if (deviceId == null || pluginKey == null) {
            finish()
            return
        }

        val device = deviceManager.getDevice(deviceId)
        val plugin = device?.getPluginIncludingWithoutPermissions(pluginKey)

        if (plugin == null) {
            finish()
            return
        }

        val dialog = plugin.pluginInfo.let {
            if (plugin.preferences != null) {
                it.getPermissionExplanationDialog(plugin.preferences!!, this, device)
            } else {
                it.getPermissionExplanationDialog(this)
            }
        }

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentDestroyed(fm: FragmentManager, f: androidx.fragment.app.Fragment) {
                super.onFragmentDestroyed(fm, f)
                if (f === dialog) {
                    finish()
                }
            }
        }, false)

        dialog.show(supportFragmentManager, "permission_explanation")
    }
}
