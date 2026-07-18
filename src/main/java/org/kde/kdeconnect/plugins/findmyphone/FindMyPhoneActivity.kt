package org.kde.kdeconnect.plugins.findmyphone

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.ui.compose.KdeTheme

class FindMyPhoneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val deviceId = intent.getStringExtra(EXTRA_DEVICE_ID)
        if (deviceId == null) {
            Log.e("FindMyPhoneActivity", "You must include the deviceId for which this activity is started as an intent EXTRA")
            finish()
            return
        }

        setContent {
            KdeTheme(this) {
                FindMyPhoneScreen(
                    deviceId = deviceId,
                    onFinish = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = "deviceId"
    }
}
