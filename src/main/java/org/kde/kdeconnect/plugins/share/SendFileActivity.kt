package org.kde.kdeconnect.plugins.share

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
import androidx.appcompat.app.AppCompatActivity
import org.kde.kdeconnect.KdeConnect.Companion.getInstance
import org.kde.kdeconnect_tp.R

class SendFileActivity : AppCompatActivity() {
    private var mDeviceId: String? = null
    private val getResult = registerForActivityResult(GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            Log.w("SendFileActivity", "No files to send?")
        } else {
            val plugin = getInstance().getDevicePlugin(mDeviceId, SharePlugin::class.java)
            plugin?.sendUriList(uris)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDeviceId = intent.getStringExtra("deviceId")
        try {
            getResult.launch("*/*")
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_file_browser, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
