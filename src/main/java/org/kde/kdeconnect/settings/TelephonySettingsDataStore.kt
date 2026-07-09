package org.kde.kdeconnect.settings

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class TelephonySettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "telephony_settings")

    val blockedNumbers: Flow<Set<String>> = context.dataStore.data
        .map { preferences -> preferences[KEY_BLOCKED_NUMBERS]?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet() }
        .distinctUntilChanged()

    val groupMessageAsMms: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_MMS_GROUP] ?: true }
        .distinctUntilChanged()

    val longTextAsMms: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_MMS_LONG_TEXT] ?: false }
        .distinctUntilChanged()

    val convertToMmsAfter: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[KEY_CONVERT_TO_MMS] ?: 3 }
        .distinctUntilChanged()

    val ringtoneUri: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[KEY_RINGTONE] ?: "" }
        .distinctUntilChanged()

    val flashlightEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[KEY_FLASHLIGHT] ?: false }
        .distinctUntilChanged()

    fun getBlockedNumbersBlockingBlocking(): Set<String> = runBlocking { blockedNumbers.first() }
    fun getGroupMessageAsMmsBlockingBlocking(): Boolean = runBlocking { groupMessageAsMms.first() }
    fun getLongTextAsMmsBlockingBlocking(): Boolean = runBlocking { longTextAsMms.first() }
    fun getConvertToMmsAfterBlockingBlocking(): Int = runBlocking { convertToMmsAfter.first() }
    fun getRingtoneUriBlockingBlocking(): String = runBlocking { ringtoneUri.first() }
    fun getFlashlightEnabledBlockingBlocking(): Boolean = runBlocking { flashlightEnabled.first() }

    suspend fun updateBlockedNumbers(numbers: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BLOCKED_NUMBERS] = numbers.joinToString(",")
        }
    }

    suspend fun setGroupMessageAsMms(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MMS_GROUP] = enabled
        }
    }

    suspend fun setLongTextAsMms(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_MMS_LONG_TEXT] = enabled
        }
    }

    suspend fun setConvertToMmsAfter(value: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CONVERT_TO_MMS] = value
        }
    }

    suspend fun setRingtone(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_RINGTONE] = uri
        }
    }

    suspend fun setFlashlightEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FLASHLIGHT] = enabled
        }
    }

    companion object {
        private val KEY_BLOCKED_NUMBERS = stringPreferencesKey("telephony_blocked_numbers")
        private val KEY_MMS_GROUP = booleanPreferencesKey("set_group_message_as_mms")
        private val KEY_MMS_LONG_TEXT = booleanPreferencesKey("set_long_text_as_mms")
        private val KEY_CONVERT_TO_MMS = intPreferencesKey("convert_to_mms_after")
        private val KEY_RINGTONE = stringPreferencesKey("findmyphone_ringtone")
        private val KEY_FLASHLIGHT = booleanPreferencesKey("findmyphone_flashlight")
    }
}
