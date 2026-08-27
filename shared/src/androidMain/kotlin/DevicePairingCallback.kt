package org.kde.kdeconnect

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import org.kde.kdeconnect.PairingHandler.PairingCallback
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.ui.PairingActivity

import org.jetbrains.compose.resources.StringResource

class DevicePairingCallback(private val device: Device, private val context: Context) : PairingCallback {
    override fun incomingPairRequest() {
        val intent = Intent(context, PairingActivity::class.java).apply {
            putExtra("deviceId", device.deviceId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun pairingSuccessful() {
        LoggerTagged.i { "pairing successful, adding to trusted devices list" }

        device.updateState {
            it.copy(
                deviceInfo = it.deviceInfo.copy(trusted = true),
                pairState = PairState.Paired,
            )
        }
        val intent = Intent().setClassName(context.packageName, BuildConfig.MAIN_ACTIVITY_NAME).apply {
            putExtra("deviceId", device.deviceId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun pairingFailed(error: StringResource) {
    }

    override fun unpaired(device: Device) {
        assert(device == device)
        LoggerTagged.i { "unpaired, removing from trusted devices list" }
        device.updateState {
            it.copy(
                deviceInfo = it.deviceInfo.copy(trusted = false),
                pairState = PairState.NotPaired,
                batteryInfo = null,
                verificationKey = null,
            )
        }
        device.jobScope.launch {
            device.deviceSettings.removeTrustedDevice(device.deviceId)
        }
    }
}
