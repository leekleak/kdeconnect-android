package org.kde.kdeconnect.plugins.notifications

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "applications")
data class AppDatabaseEntry(
    @PrimaryKey val packageName: String,
    val blacklisted: Boolean = false,
    val blockContents: Boolean = false,
    val blockImages: Boolean = false,
)

@Dao
interface AppDatabaseDao {
    @Upsert
    suspend fun upsert(entry: AppDatabaseEntry)

    @Query("SELECT * FROM applications WHERE packageName = :packageName")
    suspend fun getEntry(packageName: String): AppDatabaseEntry?

    @Query("UPDATE applications SET blacklisted = :enabled")
    suspend fun setAllEnabled(enabled: Boolean)

    @Query("SELECT blacklisted FROM applications WHERE packageName = :packageName")
    suspend fun isBlacklisted(packageName: String): Boolean?
}

@Database(entities = [AppDatabaseEntry::class], version = 1)
abstract class AppRoomDatabase : RoomDatabase() {
    abstract fun dao(): AppDatabaseDao
}

class AppDatabase(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        AppRoomDatabase::class.java,
        "Applications"
    ).build()

    private val dao = db.dao()

    suspend fun setBlacklisted(packageName: String?, blacklisted: Boolean) {
        if (packageName == null) return
        val entry = dao.getEntry(packageName) ?: AppDatabaseEntry(packageName)
        dao.upsert(entry.copy(blacklisted = blacklisted))
    }

    suspend fun setAllEnabled(enabled: Boolean) {
        dao.setAllEnabled(enabled)
    }

    suspend fun isBlacklisted(packageName: String): Boolean {
        return dao.isBlacklisted(packageName) ?: isBlacklistedByDefault(packageName)
    }

    private fun isBlacklistedByDefault(packageName: String?): Boolean {
        return disabledByDefault.contains(packageName)
    }

    enum class PrivacyOptions {
        BLOCK_CONTENTS,
        BLOCK_IMAGES
    }

    suspend fun setPrivacy(packageName: String?, option: PrivacyOptions, isBlocked: Boolean) {
        if (packageName == null) return
        val entry = dao.getEntry(packageName) ?: AppDatabaseEntry(packageName)
        val updatedEntry = when (option) {
            PrivacyOptions.BLOCK_CONTENTS -> entry.copy(blockContents = isBlocked)
            PrivacyOptions.BLOCK_IMAGES -> entry.copy(blockImages = isBlocked)
        }
        dao.upsert(updatedEntry)
    }

    suspend fun getPrivacy(packageName: String?, option: PrivacyOptions): Boolean {
        if (packageName == null) return false
        val entry = dao.getEntry(packageName) ?: return false
        return when (option) {
            PrivacyOptions.BLOCK_CONTENTS -> entry.blockContents
            PrivacyOptions.BLOCK_IMAGES -> entry.blockImages
        }
    }

    companion object {
        private val disabledByDefault = hashSetOf(
            "com.google.android.googlequicksearchbox" //Google Now notifications re-spawn every few minutes
        )
    }
}
