package org.kde.kdeconnect.plugins.mousepad

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.kde.kdeconnect.DeviceManager
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.datastore.MousePadSettingsDataStore
import org.kde.kdeconnect.helpers.SPECIAL_KEY_ENCODING_MAP
import org.koin.core.annotation.InjectedParam
import kotlin.math.pow

class MousePadViewModel(
    application: Application,
    deviceManager: DeviceManager,
    private val dataStore: MousePadSettingsDataStore,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application), SensorEventListener {

    val plugin: MousePadPlugin? = deviceManager.getDevicePlugin(deviceId, MousePadPlugin::class.java)
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    var mouseButtonsEnabled by mutableStateOf(true)
    var doubleTapDragEnabled by mutableStateOf(true)
    var isGyroListenerActive by mutableStateOf(false)
    var allowGyro by mutableStateOf(false)
    var gyroscopeSensitivity by mutableIntStateOf(100)
    var scrollDirection by mutableIntStateOf(1)
    var scrollCoefficient by mutableDoubleStateOf(1.0)
    var currentSensitivity by mutableFloatStateOf(1.0f)
    var accelerationProfile by mutableStateOf<PointerAccelerationProfile?>(null)

    var singleTapAction by mutableStateOf(ClickType.LEFT)
    var doubleTapAction by mutableStateOf(ClickType.RIGHT)
    var tripleTapAction by mutableStateOf(ClickType.MIDDLE)

    var isDragging by mutableStateOf(false)
    private var isResumed = false

    enum class ClickType {
        LEFT, RIGHT, MIDDLE, NONE;

        companion object {
            fun fromString(s: String?): ClickType = when (s) {
                "left" -> LEFT
                "right" -> RIGHT
                "middle" -> MIDDLE
                else -> NONE
            }
        }
    }

    init {
        viewModelScope.launch {
            combine<Any, Unit>(
                dataStore.scrollDirection,
                dataStore.scrollSensitivity,
                dataStore.gyroEnabled,
                dataStore.gyroSensitivity,
                dataStore.singleTap,
                dataStore.doubleTap,
                dataStore.tripleTap,
                dataStore.sensitivity,
                dataStore.accelerationProfile,
                dataStore.mouseButtonsEnabled,
                dataStore.doubleTapDragEnabled
            ) { params ->
                val scrollDir = params[0] as Boolean
                val scrollSens = params[1] as Int
                val gyroEnabled = params[2] as Boolean
                val gyroSens = params[3] as Int
                val singleTap = params[4] as String
                val doubleTap = params[5] as String
                val tripleTap = params[6] as String
                val sensitivity = params[7] as String
                val accelProfile = params[8] as String
                val mouseButtons = params[9] as Boolean
                val doubleTapDrag = params[10] as Boolean

                scrollDirection = if (scrollDir) -1 else 1
                scrollCoefficient = (scrollSens.coerceAtLeast(1) / 100.0).pow(1.5)
                
                allowGyro = isGyroSensorAvailable() && gyroEnabled
                if (allowGyro) {
                    gyroscopeSensitivity = gyroSens
                }

                singleTapAction = ClickType.fromString(singleTap)
                doubleTapAction = ClickType.fromString(doubleTap)
                tripleTapAction = ClickType.fromString(tripleTap)

                currentSensitivity = when (sensitivity) {
                    "slowest" -> 0.2f
                    "aboveSlowest" -> 0.5f
                    "default" -> 1.0f
                    "aboveDefault" -> 1.5f
                    "fastest" -> 2.0f
                    else -> 1.0f
                }

                accelerationProfile = PointerAccelerationProfileFactory.getProfileWithName(accelProfile)
                mouseButtonsEnabled = mouseButtons
                doubleTapDragEnabled = doubleTapDrag
            }.collect {
                updateGyroListener()
            }
        }
    }

    fun onResume() {
        isResumed = true
        updateGyroListener()
    }

    fun onPause() {
        isResumed = false
        updateGyroListener()
    }

    private fun updateGyroListener() {
        if (isResumed && allowGyro && !isGyroListenerActive) {
            sensorManager?.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
            isGyroListenerActive = true
        } else if ((!isResumed || !allowGyro) && isGyroListenerActive) {
            sensorManager?.unregisterListener(this)
            isGyroListenerActive = false
        }
    }

    override fun onCleared() {
        onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        val sens = gyroscopeSensitivity / 100.0f
        
        var dx = -values[2] * 70 * sens
        var dy = -values[0] * 70 * sens

        dx = if (dx in -0.25f..0.25f) 0f else dx * sens
        dy = if (dy in -0.25f..0.25f) 0f else dy * sens

        plugin?.sendMouseDelta(dx, dy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun isGyroSensorAvailable(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    fun setGyroEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setGyroEnabled(enabled)
        }
    }

    fun sendLeftClick() {
        if (isDragging) {
            plugin?.sendSingleRelease()
            isDragging = false
        } else {
            plugin?.sendLeftClick()
        }
    }

    fun sendMiddleClick() {
        plugin?.sendMiddleClick()
    }

    fun sendRightClick() {
        plugin?.sendRightClick()
    }

    fun sendScroll(y: Double) {
        plugin?.sendScroll(0.0, y)
    }

    fun sendMouseDelta(dx: Float, dy: Float) {
        plugin?.sendMouseDelta(dx, dy)
    }

    fun sendSingleHold() {
        plugin?.sendSingleHold()
        isDragging = true
    }

    fun sendDoubleClick() {
        plugin?.sendDoubleClick()
    }

    fun performClickAction(action: ClickType) {
        when (action) {
            ClickType.LEFT -> sendLeftClick()
            ClickType.RIGHT -> sendRightClick()
            ClickType.MIDDLE -> sendMiddleClick()
            ClickType.NONE -> {}
        }
    }

    fun sendChars(chars: CharSequence) {
        plugin?.sendText(chars.toString())
    }

    fun sendComposed(text: String) {
        plugin?.sendText(text)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) {
            // consume events that otherwise would move the focus away from us
            return event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    event.keyCode == KeyEvent.KEYCODE_ENTER
        }

        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            //We don't want to swallow the back button press
            return false
        }

        val np = NetworkPacket(MousePadPlugin.PACKET_TYPE_MOUSEPAD_REQUEST)

        var modifier = false
        if (event.isAltPressed) {
            np["alt"] = true
            modifier = true
        }

        if (event.isCtrlPressed) {
            np["ctrl"] = true
            modifier = true
        }

        if (event.isShiftPressed) {
            np["shift"] = true
        }

        if (event.isMetaPressed) {
            np["super"] = true
            modifier = true
        }

        val specialKey = SPECIAL_KEY_ENCODING_MAP[event.keyCode] ?: -1

        if (specialKey != -1) {
            np["specialKey"] = specialKey
        } else if (event.displayLabel.code != 0 && modifier) {
            //Alt will change the utf symbol to non-ascii characters, we want the plain original letter
            //Since getDisplayLabel will always have a value, we have to check for special keys before
            val keyCharacter = event.displayLabel
            np["key"] = keyCharacter.toString().lowercase()
        } else {
            //A normal key, but still not handled by the KeyInputConnection (happens with numbers)
            np["key"] = event.unicodeChar.toChar().toString()
        }

        plugin?.sendPacket(np)
        return true
    }
}
