package org.kde.kdeconnect.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class NotificationSettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "notification_settings",
        produceMigrations = { context ->
            listOf(
                SharedPreferencesMigration(context, "NotificationsPlugin_preferences"),
                SharedPreferencesMigration(context, "app_database")
            )
        }
    )

    val screenOffNotification: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_SCREEN_OFF_NOTIFICATION] ?: false }
        .distinctUntilChanged()

    val mprisNotificationEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_MPRIS_NOTIFICATION_ENABLED] ?: true }
        .distinctUntilChanged()

    val mprisKeepWatchingEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_MPRIS_KEEP_WATCHING_ENABLED] ?: true }
        .distinctUntilChanged()

    val allNotificationsEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ALL_NOTIFICATIONS_ENABLED] ?: true }
        .distinctUntilChanged()

    // Blocking getters for legacy interop
    fun isScreenOffNotificationEnabledBlocking(): Boolean = runBlocking { screenOffNotification.first() }
    fun isMprisNotificationEnabledBlocking(): Boolean = runBlocking { mprisNotificationEnabled.first() }
    fun isMprisKeepWatchingEnabledBlocking(): Boolean = runBlocking { mprisKeepWatchingEnabled.first() }
    fun areAllNotificationsEnabledBlocking(): Boolean = runBlocking { allNotificationsEnabled.first() }

    suspend fun setScreenOffNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SCREEN_OFF_NOTIFICATION] = enabled }
    }

    suspend fun setMprisNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MPRIS_NOTIFICATION_ENABLED] = enabled }
    }

    suspend fun setMprisKeepWatchingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MPRIS_KEEP_WATCHING_ENABLED] = enabled }
    }

    suspend fun setAllNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ALL_NOTIFICATIONS_ENABLED] = enabled }
    }

    companion object {
        private val KEY_SCREEN_OFF_NOTIFICATION = booleanPreferencesKey("pref_notification_screen_off")
        private val KEY_MPRIS_NOTIFICATION_ENABLED = booleanPreferencesKey("mpris_notification_enabled")
        private val KEY_MPRIS_KEEP_WATCHING_ENABLED = booleanPreferencesKey("mpris_keepwatching_enabled")
        private val KEY_ALL_NOTIFICATIONS_ENABLED = booleanPreferencesKey("all_enabled")
    }
}
