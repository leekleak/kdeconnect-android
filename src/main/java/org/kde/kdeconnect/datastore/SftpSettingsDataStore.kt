package org.kde.kdeconnect.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class SftpSettingsDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "sftp_settings",
        produceMigrations = { context ->
            listOf(
                SharedPreferencesMigration(context, "SftpPlugin_preferences")
            )
        }
    )

    val storageInfoListJson: Flow<String> = context.dataStore.data
        .map { it[KEY_STORAGE_INFO_LIST] ?: "[]" }
        .distinctUntilChanged()

    fun getStorageInfoListJsonBlocking(): String = runBlocking { storageInfoListJson.first() }

    suspend fun setStorageInfoListJson(json: String) {
        context.dataStore.edit { it[KEY_STORAGE_INFO_LIST] = json }
    }

    companion object {
        private val KEY_STORAGE_INFO_LIST = stringPreferencesKey("key_sftp_storage_info_list")
    }
}
