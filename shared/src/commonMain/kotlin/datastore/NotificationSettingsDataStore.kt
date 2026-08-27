package org.kde.kdeconnect.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class NotificationSettingsDataStore(private val dataStore: DataStore<Preferences>) {

    val screenOffNotification: Flow<Boolean> = dataStore.data
        .map { it[KEY_SCREEN_OFF_NOTIFICATION] ?: false }
        .distinctUntilChanged()

    val mprisNotificationEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_MPRIS_NOTIFICATION_ENABLED] ?: false }
        .distinctUntilChanged()

    val mprisKeepWatchingEnabled: Flow<Boolean> = dataStore.data
        .map { it[KEY_MPRIS_KEEP_WATCHING_ENABLED] ?: false }
        .distinctUntilChanged()

    suspend fun setScreenOffNotification(enabled: Boolean) {
        dataStore.edit { it[KEY_SCREEN_OFF_NOTIFICATION] = enabled }
    }

    suspend fun setMprisNotificationEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MPRIS_NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setMprisKeepWatchingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MPRIS_KEEP_WATCHING_ENABLED] = enabled }
    }

    companion object {
        private val KEY_SCREEN_OFF_NOTIFICATION = booleanPreferencesKey("pref_notification_screen_off")
        private val KEY_MPRIS_NOTIFICATION_ENABLED = booleanPreferencesKey("mpris_notification_enabled")
        private val KEY_MPRIS_KEEP_WATCHING_ENABLED = booleanPreferencesKey("mpris_keepwatching_enabled")
    }
}
