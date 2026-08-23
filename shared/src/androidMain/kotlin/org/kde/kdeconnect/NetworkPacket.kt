package org.kde.kdeconnect

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okio.Buffer
import okio.Source
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
data class NetworkPacket(
    val type: String,
    private val mBody: JsonObject,
    var payload: Payload?,
    var payloadTransferInfo: JsonObject?,
    val isCanceled: AtomicBoolean = AtomicBoolean(false)
) {
    constructor(type: String) : this(
        type = type,
        mBody = JsonObject(emptyMap()),
        payload = null,
        payloadTransferInfo = JsonObject(emptyMap())
    )

    fun cancel() {
        isCanceled.store(true)
    }

    fun getString(key: String, defaultValue: String): String = getString(key) ?: defaultValue

    fun getString(key: String): String? = mBody[key]?.jsonPrimitive?.contentOrNull

    fun getRawString(key: String): String = mBody[key]?.toString() ?: ""

    fun getInt(key: String, defaultValue: Int): Int = getInt(key) ?: defaultValue

    fun getInt(key: String): Int? = mBody[key]?.jsonPrimitive?.intOrNull


    fun getLong(key: String, defaultValue: Long): Long = getLong(key) ?: defaultValue

    fun getLong(key: String): Long? = mBody[key]?.jsonPrimitive?.longOrNull


    fun getBoolean(key: String, defaultValue: Boolean): Boolean = getBoolean(key) ?: defaultValue

    fun getBoolean(key: String): Boolean? = mBody[key]?.jsonPrimitive?.booleanOrNull

    fun getDouble(key: String, defaultValue: Double): Double = getDouble(key) ?: defaultValue

    fun getDouble(key: String): Double? = mBody[key]?.jsonPrimitive?.doubleOrNull

    fun getJsonArray(key: String): JsonArray? = mBody[key] as? JsonArray

    fun getJsonObject(key: String): JsonObject? = mBody[key] as? JsonObject

    fun getStringSet(key: String): Set<String>? {
        val jsonArray = getJsonArray(key) ?: return null
        val list: MutableSet<String> = HashSet()
        jsonArray.forEach { list.add(it.jsonPrimitive.content) }
        return list
    }

    fun getStringList(key: String): List<String>? {
        val jsonArray = getJsonArray(key) ?: return null
        val list: MutableList<String> = ArrayList()
        jsonArray.forEach { list.add(it.jsonPrimitive.content) }
        return list
    }

    fun update(action: JsonObjectBuilder.() -> Unit): NetworkPacket =
        copy(
            mBody = buildJsonObject {
                for ((k, v) in mBody) put(k, v)
                action()
            }
        )

    fun has(key: String): Boolean {
        return mBody.containsKey(key)
    }

    operator fun contains(key: String): Boolean {
        return has(key)
    }

    fun serialize(): String {
        val jo = buildJsonObject {
            put("id", System.currentTimeMillis())
            put("type", type)
            put("body", mBody)
            if (hasPayload()) {
                put("payloadSize", payload!!.payloadSize)
                if (hasPayloadTransferInfo()) {
                    put("payloadTransferInfo", payloadTransferInfo!!)
                }
            }
        }

        try {
            return jo.toString() + "\n"
        } catch (e: Exception) {
            throw RuntimeException("Error serializing packet of type $type", e)
        }
    }

    val payloadSize: Long
        get() = payload?.payloadSize ?: 0

    fun hasPayload(): Boolean {
        val payload = payload
        return payload != null && payload.payloadSize != 0L
    }

    fun hasPayloadTransferInfo(): Boolean {
        return payloadTransferInfo != null && payloadTransferInfo?.isEmpty() == false
    }

    class Payload(
        val source: Source?,
        val payloadSize: Long,
        val onCloseCallback: () -> Unit = {}
    ) {
        constructor(payloadSize: Long) : this(null, payloadSize)
        constructor(data: ByteArray) : this(Buffer().write(data), data.size.toLong())

        fun close() {
            source?.close()
            onCloseCallback()
        }
    }

    companion object {
        const val PACKET_TYPE_IDENTITY: String = "kdeconnect.identity"
        const val PACKET_TYPE_PAIR: String = "kdeconnect.pair"

        val PROTOCOL_PACKET_TYPES: List<String> = listOf(
            PACKET_TYPE_IDENTITY,
            PACKET_TYPE_PAIR
        )

        fun unserialize(s: String): NetworkPacket {
            val jsonObject = Json.parseToJsonElement(s.trim { it <= ' ' }).jsonObject
            val type = jsonObject["type"]?.jsonPrimitive?.content!!
            val body = jsonObject["body"]?.jsonObject!!
            val payloadTransferInfo = jsonObject["payloadTransferInfo"]?.jsonObject
            val payloadSize = jsonObject["payloadSize"]?.jsonPrimitive?.longOrNull
            val payload = payloadSize?.let { Payload(it) }

            return NetworkPacket(type, body, payload, payloadTransferInfo)
        }
    }
}

fun Set<String>.toJsonArray(): JsonArray = buildJsonArray { forEach { add(it) } }
fun List<String>.toJsonArray(): JsonArray = buildJsonArray { forEach { add(it) } }
