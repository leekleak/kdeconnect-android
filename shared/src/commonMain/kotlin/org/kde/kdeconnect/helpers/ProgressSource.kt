package org.kde.kdeconnect.helpers

import okio.Buffer
import okio.ForwardingSource
import okio.Source
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ProgressSource(
    delegate: Source,
    private val totalPayloadSize: AtomicLong, // or Long if you already know the total
    private val isCancelled: () -> Boolean,
    private val setProgress: (Int) -> Unit
) : ForwardingSource(delegate) {

    private var totalReceived = 0L
    private var prevProgressPercentage = -1L
    private var lastProgressTimeMillis = 0L

    fun getReceived(): Long = totalReceived

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (isCancelled()) {
            throw Exception("Cancelled")
        }

        val bytesRead = super.read(sink, byteCount)

        if (bytesRead > 0) {
            totalReceived += bytesRead

            val total = totalPayloadSize.load()
            if (total > 0) {
                val progressPercentage = totalReceived * 100 / total
                val curTimeMillis = System.currentTimeMillis()

                if (progressPercentage != prevProgressPercentage &&
                    (progressPercentage == 100L || curTimeMillis - lastProgressTimeMillis >= 500)
                ) {
                    prevProgressPercentage = progressPercentage
                    lastProgressTimeMillis = curTimeMillis
                    setProgress(progressPercentage.toInt())
                }
            }
        }

        return bytesRead
    }
}