package org.kde.kdeconnect.helpers

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.DeviceType
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect.plugins.PluginFactory
import java.security.cert.Certificate
import java.security.cert.CertificateException

class MapTypeConverter {
    @TypeConverter
    fun fromString(value: String): Map<String, Boolean> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromMap(map: Map<String, Boolean>): String {
        return Json.encodeToString(map)
    }
}

class SetTypeConverter {
    @TypeConverter
    fun fromString(value: String): Set<String> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptySet()
        }
    }

    @TypeConverter
    fun fromSet(set: Set<String>): String {
        return Json.encodeToString(set)
    }
}

class DeviceTypeConverter {
    @TypeConverter
    fun fromString(value: String): DeviceType {
        return DeviceType.fromString(value)
    }

    @TypeConverter
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
@TypeConverters(MapTypeConverter::class, SetTypeConverter::class, DeviceTypeConverter::class)
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

    suspend fun getDeviceCertificateBytes(deviceId: String): ByteArray {
        return deviceDao.getCertificate(deviceId) ?: throw CertificateException("No certificate stored for device $deviceId")
    }

    suspend fun isCertificateStored(deviceId: String): Boolean {
        return deviceDao.getCertificate(deviceId) != null
    }

    suspend fun getBooleanSetting(deviceId: String, key: String, defaultValue: Boolean): Boolean = withContext(Dispatchers.IO) {
        val device = deviceDao.getDevice(deviceId) ?: return@withContext defaultValue
        return@withContext device.settings[key] ?: defaultValue
    }

    suspend fun setBooleanSetting(deviceId: String, key: String, value: Boolean) = withContext(Dispatchers.IO) {
        val device = deviceDao.getDevice(deviceId) ?: return@withContext
        val updatedSettings = device.settings.toMutableMap().apply { put(key, value) }
        deviceDao.upsert(device.copy(settings = updatedSettings))
    }

    suspend fun getDeviceInfo(deviceId: String): DeviceInfo? {
        val info = deviceDao.getDevice(deviceId)
        return info
    }

    fun getDeviceInfoFlow(deviceId: String): Flow<DeviceInfo?> = deviceDao.getDeviceFlow(deviceId)
}
