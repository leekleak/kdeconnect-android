package org.kde.kdeconnect

import org.jetbrains.annotations.VisibleForTesting
import org.kde.kdeconnect.helpers.LoggerTagged
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
object DeviceStats {
    /**
     * Keep 24 hours of events
     */
    private const val EVENT_KEEP_WINDOW_MILLIS: Long = 24 * 60 * 60 * 1000

    /**
     * Delete old (>24 hours, see EVENT_KEEP_WINDOW_MILLIS) events every 6 hours
     */
    private const val CLEANUP_INTERVAL_MILLIS = EVENT_KEEP_WINDOW_MILLIS / 4

    private val eventsByDevice: ConcurrentHashMap<String, PacketStats> = ConcurrentHashMap<String, PacketStats>()
    private var nextCleanup = AtomicLong(System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS)

    fun countReceived(deviceId: String, packetType: String) {
        eventsByDevice
            .computeIfAbsent(deviceId) { PacketStats() }
            .receivedByType
            .computeIfAbsent(packetType) { ArrayList() }
            .add(System.currentTimeMillis())
        cleanupIfNeeded()
    }

    fun countSent(deviceId: String, packetType: String, success: Boolean) {
        if (success) {
            eventsByDevice
                .computeIfAbsent(deviceId) { PacketStats() }
                .sentSuccessfulByType
                .computeIfAbsent(packetType) { ArrayList() }
                .add(System.currentTimeMillis())
        } else {
            eventsByDevice
                .computeIfAbsent(deviceId) { PacketStats() }
                .sentFailedByType
                .computeIfAbsent(packetType) { ArrayList() }
                .add(System.currentTimeMillis())
        }
        cleanupIfNeeded()
    }

    private fun cleanupIfNeeded() {
        val cutoutTimestamp = System.currentTimeMillis() - EVENT_KEEP_WINDOW_MILLIS
        if (System.currentTimeMillis() > nextCleanup.load()) {
            LoggerTagged.i { "Doing periodic cleanup" }
            for (de in eventsByDevice.values) {
                removeOldEvents(de.receivedByType, cutoutTimestamp)
                removeOldEvents(de.sentFailedByType, cutoutTimestamp)
                removeOldEvents(de.sentSuccessfulByType, cutoutTimestamp)
            }
            nextCleanup.store(System.currentTimeMillis() + CLEANUP_INTERVAL_MILLIS)
        }
    }

    @VisibleForTesting
    fun removeOldEvents(eventsByType: ConcurrentHashMap<String, ArrayList<Long>>, cutoutTimestamp: Long) {
        val iterator = eventsByType.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val events = entry.value

            var index = events.binarySearch(cutoutTimestamp)
            if (index < 0) {
                index = -(index + 1) // Convert the negative index to insertion point
            }

            if (index < events.size) {
                events.subList(0, index).clear()
            } else {
                iterator.remove() // No element greater than the threshold
            }
        }
    }

    internal class PacketStats {
        val createdAtMillis: Long = System.currentTimeMillis()
        val receivedByType: ConcurrentHashMap<String, ArrayList<Long>> = ConcurrentHashMap()
        val sentSuccessfulByType: ConcurrentHashMap<String, ArrayList<Long>> = ConcurrentHashMap()
        val sentFailedByType: ConcurrentHashMap<String, ArrayList<Long>> = ConcurrentHashMap()

        internal data class Summary(
            val packetType: String,
            var received: Int = 0,
            var sentSuccessful: Int = 0,
            var sentFailed: Int = 0,
            var total: Int = 0
        )

        val summaries: Collection<Summary>
            get() {
                val countsByType: MutableMap<String, Summary> = HashMap()
                for ((key, value) in receivedByType) {
                    val summary = countsByType.computeIfAbsent(key) { packetType -> Summary(packetType) }
                    summary.received += value.size
                    summary.total += value.size
                }
                for ((key, value) in sentSuccessfulByType) {
                    val summary = countsByType.computeIfAbsent(key) { packetType -> Summary(packetType) }
                    summary.sentSuccessful += value.size
                    summary.total += value.size
                }
                for ((key, value) in sentFailedByType) {
                    val summary = countsByType.computeIfAbsent(key) { packetType -> Summary(packetType) }
                    summary.sentFailed += value.size
                    summary.total += value.size
                }
                return countsByType.values
            }
    }
}