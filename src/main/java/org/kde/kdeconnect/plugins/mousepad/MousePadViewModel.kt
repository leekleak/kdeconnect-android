package org.kde.kdeconnect.plugins.mousepad

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect_tp.R
import org.koin.core.annotation.InjectedParam
import kotlin.math.pow

class MousePadViewModel(
    application: Application,
    @InjectedParam val deviceId: String
) : AndroidViewModel(application), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

    val plugin: MousePadPlugin? = KdeConnect.getInstance().getDevicePlugin(deviceId, MousePadPlugin::class.java)
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

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
        prefs.registerOnSharedPreferenceChangeListener(this)
        applyPrefs()
    }

    fun applyPrefs() {
        val app = getApplication<Application>()
        scrollDirection = if (prefs.getBoolean(app.getString(R.string.mousepad_scroll_direction), false)) -1 else 1

        val scrollSens = prefs.getInt(app.getString(R.string.mousepad_scroll_sensitivity), 100).coerceAtLeast(1)
        scrollCoefficient = (scrollSens / 100.0).pow(1.5)

        allowGyro = isGyroSensorAvailable() && prefs.getBoolean(app.getString(R.string.gyro_mouse_enabled), false)
        if (allowGyro) {
            gyroscopeSensitivity = prefs.getInt(app.getString(R.string.gyro_mouse_sensitivity), 100)
        }

        singleTapAction = ClickType.fromString(prefs.getString(app.getString(R.string.mousepad_single_tap_key), app.getString(R.string.mousepad_default_single)))
        doubleTapAction = ClickType.fromString(prefs.getString(app.getString(R.string.mousepad_double_tap_key), app.getString(R.string.mousepad_default_double)))
        tripleTapAction = ClickType.fromString(prefs.getString(app.getString(R.string.mousepad_triple_tap_key), app.getString(R.string.mousepad_default_triple)))

        val sensitivitySetting = prefs.getString(app.getString(R.string.mousepad_sensitivity_key), app.getString(R.string.mousepad_default_sensitivity))
        currentSensitivity = when (sensitivitySetting) {
            "slowest" -> 0.2f
            "aboveSlowest" -> 0.5f
            "default" -> 1.0f
            "aboveDefault" -> 1.5f
            "fastest" -> 2.0f
            else -> 1.0f
        }

        val accelerationProfileName = prefs.getString(app.getString(R.string.mousepad_acceleration_profile_key), app.getString(R.string.mousepad_default_acceleration_profile)) ?: app.getString(R.string.mousepad_default_acceleration_profile)
        accelerationProfile = PointerAccelerationProfileFactory.getProfileWithName(accelerationProfileName)

        mouseButtonsEnabled = prefs.getBoolean(app.getString(R.string.mousepad_mouse_buttons_enabled_pref), true)
        doubleTapDragEnabled = prefs.getBoolean(app.getString(R.string.mousepad_doubletap_drag_enabled_pref), true)
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
        prefs.unregisterOnSharedPreferenceChangeListener(this)
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        applyPrefs()
        updateGyroListener()
    }

    fun isGyroSensorAvailable(): Boolean {
        return sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    }

    fun setGyroEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        prefs.edit { putBoolean(app.getString(R.string.gyro_mouse_enabled), enabled) }
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

        val specialKey = MOUSE_PAD_SPECIAL_KEYS.get(event.keyCode, -1)

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
