package org.kde.kdeconnect.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.kde.kdeconnect_tp.R

class MousePadSettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mousepad_settings")

    val singleTap: Flow<String> = context.dataStore.data
        .map { it[KEY_SINGLE_TAP] ?: context.getString(R.string.mousepad_default_single) }
        .distinctUntilChanged()

    val doubleTap: Flow<String> = context.dataStore.data
        .map { it[KEY_DOUBLE_TAP] ?: context.getString(R.string.mousepad_default_double) }
        .distinctUntilChanged()

    val tripleTap: Flow<String> = context.dataStore.data
        .map { it[KEY_TRIPLE_TAP] ?: context.getString(R.string.mousepad_default_triple) }
        .distinctUntilChanged()

    val sensitivity: Flow<Int> = context.dataStore.data
        .map { it[KEY_SENSITIVITY] ?: 4 }
        .distinctUntilChanged()

    val acceleration: Flow<Int> = context.dataStore.data
        .map { it[KEY_ACCELERATION] ?: 0 }
        .distinctUntilChanged()

    val scrollDirection: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SCROLL_DIRECTION] ?: false }
        .distinctUntilChanged()

    val scrollSensitivity: Flow<Int> = context.dataStore.data
        .map { it[KEY_SCROLL_SENSITIVITY] ?: 100 }
        .distinctUntilChanged()

    val gyroEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_GYRO_ENABLED] ?: false }
        .distinctUntilChanged()

    val gyroSensitivity: Flow<Int> = context.dataStore.data
        .map { it[KEY_GYRO_SENSITIVITY] ?: 100 }
        .distinctUntilChanged()

    val doubleTapDragEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_DOUBLE_TAP_DRAG_ENABLED] ?: true }
        .distinctUntilChanged()

    val sendKeystrokesEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SEND_KEYSTROKES_ENABLED] ?: true }
        .distinctUntilChanged()

    val sendSafeTextImmediately: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SEND_SAFE_TEXT_IMMEDIATELY] ?: true }
        .distinctUntilChanged()

    // Setters
    suspend fun setSingleTap(value: String) = context.dataStore.edit { it[KEY_SINGLE_TAP] = value }
    suspend fun setDoubleTap(value: String) = context.dataStore.edit { it[KEY_DOUBLE_TAP] = value }
    suspend fun setTripleTap(value: String) = context.dataStore.edit { it[KEY_TRIPLE_TAP] = value }
    suspend fun setSensitivity(value: Int) = context.dataStore.edit { it[KEY_SENSITIVITY] = value }
    suspend fun setAccelerationProfile(value: Int) = context.dataStore.edit { it[KEY_ACCELERATION] = value }
    suspend fun setScrollDirection(value: Boolean) = context.dataStore.edit { it[KEY_SCROLL_DIRECTION] = value }
    suspend fun setScrollSensitivity(value: Int) = context.dataStore.edit { it[KEY_SCROLL_SENSITIVITY] = value }
    suspend fun setGyroEnabled(value: Boolean) = context.dataStore.edit { it[KEY_GYRO_ENABLED] = value }
    suspend fun setGyroSensitivity(value: Int) = context.dataStore.edit { it[KEY_GYRO_SENSITIVITY] = value }
    suspend fun setDoubleTapDragEnabled(value: Boolean) = context.dataStore.edit { it[KEY_DOUBLE_TAP_DRAG_ENABLED] = value }
    suspend fun setSendKeystrokesEnabled(value: Boolean) = context.dataStore.edit { it[KEY_SEND_KEYSTROKES_ENABLED] = value }
    suspend fun setSendSafeTextImmediately(value: Boolean) = context.dataStore.edit { it[KEY_SEND_SAFE_TEXT_IMMEDIATELY] = value }

    companion object {
        private val KEY_SINGLE_TAP = stringPreferencesKey("mousepad_single_tap_key")
        private val KEY_DOUBLE_TAP = stringPreferencesKey("mousepad_double_tap_key")
        private val KEY_TRIPLE_TAP = stringPreferencesKey("mousepad_triple_tap_key")
        private val KEY_SENSITIVITY = intPreferencesKey("mousepad_sensitivity_key")
        private val KEY_ACCELERATION = intPreferencesKey("mousepad_acceleration_key")
        private val KEY_SCROLL_SENSITIVITY = intPreferencesKey("mousepad_scroll_sensitivity")
        private val KEY_GYRO_SENSITIVITY = intPreferencesKey("gyro_mouse_sensitivity")
        private val KEY_SCROLL_DIRECTION = booleanPreferencesKey("mousepad_scroll_direction")

        private val KEY_GYRO_ENABLED = booleanPreferencesKey("gyro_mouse_enabled")
        private val KEY_DOUBLE_TAP_DRAG_ENABLED = booleanPreferencesKey("doubletap_drag_enabled")
        private val KEY_SEND_KEYSTROKES_ENABLED = booleanPreferencesKey("pref_sendkeystrokes_enabled")
        private val KEY_SEND_SAFE_TEXT_IMMEDIATELY = booleanPreferencesKey("pref_send_safe_text_immediately")
    }
}
