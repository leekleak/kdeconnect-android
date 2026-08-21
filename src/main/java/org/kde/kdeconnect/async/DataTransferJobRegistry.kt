package org.kde.kdeconnect.async

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object DataTransferJobRegistry {
    private val jobs = ConcurrentHashMap<Int, DataTransferJob>()
    private val idIncrementer = AtomicInteger(0)

    fun generateJobId(): Int = idIncrementer.incrementAndGet()

    fun register(job: DataTransferJob) {
        jobs[job.id] = job
    }

    fun unregister(jobId: Int) {
        jobs.remove(jobId)
    }

    fun get(jobId: Int): DataTransferJob? {
        return jobs[jobId]
    }
}
