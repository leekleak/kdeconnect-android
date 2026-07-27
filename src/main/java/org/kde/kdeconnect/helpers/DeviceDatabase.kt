package org.kde.kdeconnect.helpers

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import org.kde.kdeconnect.helpers.security.SslHelper.parseCertificate
import java.security.cert.Certificate

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val type: String,
    val protocolVersion: Int,
    val certificate: ByteArray,
    val trusted: Boolean = true,
    val settings: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DeviceEntity
        if (deviceId != other.deviceId) return false
        if (name != other.name) return false
        if (type != other.type) return false
        if (protocolVersion != other.protocolVersion) return false
        if (!certificate.contentEquals(other.certificate)) return false
        if (trusted != other.trusted) return false
        if (settings != other.settings) return false
        return true
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + protocolVersion
        result = 31 * result + certificate.contentHashCode()
        result = 31 * result + trusted.hashCode()
        result = 31 * result + settings.hashCode()
        return result
    }
}

class MapTypeConverter {
    @TypeConverter
    fun fromString(value: String): Map<String, String> {
        return try {
            Json.decodeFromString(value)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromMap(map: Map<String, String>): String {
        return Json.encodeToString(map)
    }
}

@Dao
interface DeviceDao {
    @Query("SELECT trusted FROM devices WHERE deviceId = :deviceId")
    suspend fun isTrusted(deviceId: String): Boolean?

    @Query("SELECT * FROM devices WHERE trusted = 1")
    fun getAllTrusted(): Flow<List<DeviceEntity>>

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun remove(deviceId: String)

    @Query("DELETE FROM devices")
    suspend fun removeAll()

    @Query("SELECT certificate FROM devices WHERE deviceId = :deviceId")
    suspend fun getCertificate(deviceId: String): ByteArray?

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    suspend fun getDevice(deviceId: String): DeviceEntity?

    @Query("SELECT deviceId FROM devices WHERE trusted = 1")
    suspend fun getAllTrustedIds(): List<String>
}

@Database(entities = [DeviceEntity::class], version = 1)
@TypeConverters(MapTypeConverter::class)
abstract class DevicesRoomDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}

class DeviceSettings(private val deviceDao: DeviceDao) {

    suspend fun isTrustedDevice(deviceId: String): Boolean {
        return deviceDao.isTrusted(deviceId) ?: false
    }

    suspend fun addTrustedDevice(device: DeviceEntity) {
        deviceDao.upsert(device)
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

    suspend fun getDeviceCertificate(deviceId: String): Certificate {
        val certificateBytes = deviceDao.getCertificate(deviceId)
            ?: throw java.security.cert.CertificateException("No certificate stored for device $deviceId")
        return parseCertificate(certificateBytes)
    }

    suspend fun isCertificateStored(deviceId: String): Boolean {
        return deviceDao.getCertificate(deviceId) != null
    }

    suspend fun getSetting(deviceId: String, key: String, defaultValue: String?): String? {
        val device = deviceDao.getDevice(deviceId) ?: return defaultValue
        return device.settings[key] ?: defaultValue
    }

    suspend fun setSetting(deviceId: String, key: String, value: String) {
        val device = deviceDao.getDevice(deviceId) ?: return
        val updatedSettings = device.settings.toMutableMap().apply { put(key, value) }
        deviceDao.upsert(device.copy(settings = updatedSettings))
    }

    suspend fun getBooleanSetting(deviceId: String, key: String, defaultValue: Boolean): Boolean {
        val value = getSetting(deviceId, key, null)
        return value?.toBoolean() ?: defaultValue
    }

    suspend fun setBooleanSetting(deviceId: String, key: String, value: Boolean) {
        setSetting(deviceId, key, value.toString())
    }

    suspend fun getDeviceEntity(deviceId: String): DeviceEntity? {
        return deviceDao.getDevice(deviceId)
    }
}
