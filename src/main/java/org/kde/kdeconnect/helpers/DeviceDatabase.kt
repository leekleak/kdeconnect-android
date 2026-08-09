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

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String,
    val name: String,
    val type: String,
    val protocolVersion: Int,
    val certificate: ByteArray,
    val trusted: Boolean = true,
    val settings: Map<String, Boolean> = emptyMap()
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

    fun toDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            id = deviceId,
            certificate = certificate,
            name = name,
            type = DeviceType.fromString(type),
            protocolVersion = protocolVersion,
            incomingCapabilities = emptySet(),
            outgoingCapabilities = emptySet(),
            settings = settings
        )
    }
}

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

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    fun getDeviceFlow(deviceId: String): Flow<DeviceEntity?>

    @Query("SELECT deviceId FROM devices WHERE trusted = 1")
    suspend fun getAllTrustedIds(): List<String>
}

@Database(entities = [DeviceEntity::class], version = 1)
@TypeConverters(MapTypeConverter::class)
abstract class DevicesRoomDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
}

class DeviceSettings(
    private val deviceDao: DeviceDao,
    private val sslHelper: SslHelper
) {

    suspend fun isTrustedDevice(deviceId: String): Boolean {
        return deviceDao.isTrusted(deviceId) ?: false
    }

    suspend fun addTrustedDevice(device: DeviceEntity) {
        if (device.settings.size == PluginFactory.availablePlugins.size) {
            deviceDao.upsert(device)
        } else {
            val missingSettings = PluginFactory.availablePlugins.toSet().minus(device.settings.keys)
            val newDevice = device.copy(
                settings = device.settings.plus(missingSettings.map { it to PluginFactory.getPluginInfo(it).isEnabledByDefault })
            )
            deviceDao.upsert(newDevice)
        }
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
            ?: throw CertificateException("No certificate stored for device $deviceId")
        return sslHelper.parseCertificate(certificateBytes)
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

    suspend fun getDeviceEntity(deviceId: String): DeviceEntity? {
        return deviceDao.getDevice(deviceId)
    }

    fun getDeviceEntityFlow(deviceId: String): Flow<DeviceEntity?> = deviceDao.getDeviceFlow(deviceId)
}
