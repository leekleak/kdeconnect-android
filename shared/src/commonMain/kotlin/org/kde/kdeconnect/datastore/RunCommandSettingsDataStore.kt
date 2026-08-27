package org.kde.kdeconnect.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.kde.kdeconnect.plugins.runcommand.RunCommand

class RunCommandSettingsDataStore(private val dataStore: DataStore<Preferences>) {

    private val serializer = Json { ignoreUnknownKeys = true }

    private fun getSerializedCommands(deviceId: String): Flow<String?> = dataStore.data
        .map { it[stringPreferencesKey(KEY_COMMANDS_PREFIX + deviceId)] }
        .distinctUntilChanged()

    fun getCommands(deviceId: String): Flow<List<RunCommand>> = getSerializedCommands(deviceId)
        .map { serialized ->
            if (serialized.isNullOrEmpty()) return@map emptyList()
            try {
                serializer.decodeFromString(serialized)
            } catch (_: Exception) {
                emptyList()
            }
        }

    suspend fun setCommands(deviceId: String, commands: List<RunCommand>) {
        val serialized = serializer.encodeToString(commands)
        dataStore.edit { it[stringPreferencesKey(KEY_COMMANDS_PREFIX + deviceId)] = serialized }
    }

    fun getWidgetDeviceId(appWidgetId: Int): Flow<String?> = dataStore.data
        .map { it[stringPreferencesKey(KEY_WIDGET_PREFIX + appWidgetId)] }
        .distinctUntilChanged()

    suspend fun setWidgetDeviceId(appWidgetId: Int, deviceId: String) {
        dataStore.edit { it[stringPreferencesKey(KEY_WIDGET_PREFIX + appWidgetId)] = deviceId }
    }

    suspend fun deleteWidgetDeviceId(appWidgetId: Int) {
        dataStore.edit { it.remove(stringPreferencesKey(KEY_WIDGET_PREFIX + appWidgetId)) }
    }

    companion object {
        private const val KEY_COMMANDS_PREFIX = "commands_preference_"
        private const val KEY_WIDGET_PREFIX = "appwidget_"
    }
}
