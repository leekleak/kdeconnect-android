package org.kde.kdeconnect.helpers

import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceType

class MapTypeConverter {
    @ColumnTypeConverter
    fun fromString(value: String): Map<String, Boolean> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    @ColumnTypeConverter
    fun fromMap(map: Map<String, Boolean>): String {
        return Json.encodeToString(map)
    }
}

class SetTypeConverter {
    @ColumnTypeConverter
    fun fromString(value: String): Set<String> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptySet()
        }
    }

    @ColumnTypeConverter
    fun fromSet(set: Set<String>): String {
        return Json.encodeToString(set)
    }
}

class DeviceTypeConverter {
    @ColumnTypeConverter
    fun fromString(value: String): DeviceType {
        return DeviceType.fromString(value)
    }

    @ColumnTypeConverter
    fun fromDeviceType(type: DeviceType): String {
        return type.toString()
    }
}

@Dao
interface DeviceDao {
    @Query("SELECT trusted FROM devices WHERE deviceId = :deviceId")
    suspend fun isTrusted(deviceId: String): Boolean?

    @Query("SELECT * FROM devices WHERE trusted = 1")
    fun getAllTrusted(): Flow<List<DeviceInfo>>

    @Upsert
    suspend fun upsert(device: DeviceInfo)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun remove(deviceId: String)

    @Query("DELETE FROM devices")
    suspend fun removeAll()

    @Query("SELECT certificate FROM devices WHERE deviceId = :deviceId")
    suspend fun getCertificate(deviceId: String): ByteArray?

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    suspend fun getDevice(deviceId: String): DeviceInfo?

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    fun getDeviceFlow(deviceId: String): Flow<DeviceInfo?>

    @Query("SELECT deviceId FROM devices WHERE trusted = 1")
    suspend fun getAllTrustedIds(): List<String>
}

@Database(entities = [DeviceInfo::class], version = 1)
@ColumnTypeConverters(MapTypeConverter::class, SetTypeConverter::class, DeviceTypeConverter::class)
abstract class DevicesRoomDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}

/**
 * This class and all device-related db reads should only be used for saving data or loading fresh.
 *
 * If you need to access a device's info, use the device manager to get an active instance.
 */
class DeviceSettings(
    private val deviceDao: DeviceDao,
) {

    suspend fun isTrustedDevice(deviceId: String): Boolean {
        return deviceDao.isTrusted(deviceId) ?: false
    }

    suspend fun addTrustedDevice(device: DeviceInfo) {
        val newDevice = device.copy(trusted = true).withPopulatedSettings()
        deviceDao.upsert(newDevice)
    }

    suspend fun removeTrustedDevice(deviceId: String) {
        deviceDao.remove(deviceId)
    }

    suspend fun getAllTrustedDevices(): List<String> {
        return deviceDao.getAllTrustedIds()
    }

    suspend fun removeAllTrustedDevices() {
        deviceDao.removeAll()
    }

    suspend fun getDeviceInfo(deviceId: String): DeviceInfo? {
        val info = deviceDao.getDevice(deviceId)
        return info
    }
}
