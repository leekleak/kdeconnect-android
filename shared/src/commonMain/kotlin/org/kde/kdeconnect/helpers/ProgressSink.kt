package org.kde.kdeconnect.helpers

import okio.Buffer
import okio.ForwardingSink
import okio.Sink

class ProgressSink(
    delegate: Sink,
    private val totalPayloadSize: Long,
    private val isCancelled: () -> Boolean,
    private val setProgress: (Int) -> Unit
) : ForwardingSink(delegate) {

    private var totalWritten = 0L
    private var prevProgressPercentage = -1L
    private var lastProgressTimeMillis = 0L

    fun getWritten(): Long = totalWritten

    override fun write(source: Buffer, byteCount: Long) {
        if (isCancelled()) {
            throw Exception("Cancelled")
        }

        super.write(source, byteCount)

        if (byteCount > 0) {
            totalWritten += byteCount

            if (totalPayloadSize > 0) {
                val progressPercentage = totalWritten * 100 / totalPayloadSize
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
    }
}
