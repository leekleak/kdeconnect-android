package org.kde.kdeconnect.plugins.runcommand

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json as DataFormat

@Serializable
data class RunCommand(
    val key: String,
    val name: String,
    val command: String
) {
    companion object {
        private val serializer = DataFormat { ignoreUnknownKeys = true }

        fun fromPacket(commandListString: String): List<RunCommand> {
            return try {
                val rawCommands: Map<String, RunCommandMapEntry> = serializer.decodeFromString(commandListString)
                rawCommands.map { (key, raw) -> RunCommand(key, raw.name, raw.command) }
            } catch (_: Exception) {
                emptyList()
            }
        }

        @Serializable
        private data class RunCommandMapEntry(val name: String, val command: String)
    }
}
