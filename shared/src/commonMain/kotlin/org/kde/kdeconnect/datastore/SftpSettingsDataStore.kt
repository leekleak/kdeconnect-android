package org.kde.kdeconnect.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class SftpSettingsDataStore(private val dataStore: DataStore<Preferences>) {

    val storageInfoListJson: Flow<String> = dataStore.data
        .map { it[KEY_STORAGE_INFO_LIST] ?: "[]" }
        .distinctUntilChanged()

    suspend fun setStorageInfoListJson(json: String) {
        dataStore.edit { it[KEY_STORAGE_INFO_LIST] = json }
    }

    companion object {
        private val KEY_STORAGE_INFO_LIST = stringPreferencesKey("key_sftp_storage_info_list")
    }
}
