/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.async

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor

/**
 * Scheduler for [BackgroundJob] objects.
 *
 * We use an internal [ThreadPoolExecutor] to catch Exceptions and pass them along to [.handleUncaughtException].
 *
 * Might be able to be replaced with coroutines later
 */
class BackgroundJobHandler(numThreads: Int) {
    val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val concurrencyLimit: Semaphore = Semaphore(permits = numThreads)

    private val jobMap: ConcurrentHashMap<BackgroundJob<*, *>, Job> = ConcurrentHashMap()

    private val handler: Handler = Handler(Looper.getMainLooper())

    fun runJob(bgJob: BackgroundJob<*, *>) {
        val job = jobScope.launch {
            concurrencyLimit.withPermit {
                runCatching {
                    bgJob.setBackgroundJobHandler(this@BackgroundJobHandler)
                    bgJob.run()
                }.onFailure { e ->
                    Log.d(LOG_TAG, "Failed to launch a background job: ${e.message}")
                    bgJob.reportError(e)
                }
            }
        }
        jobMap[bgJob] = job
    }

    fun isRunning(jobId: Long): Boolean = jobMap.keys.any { it.id == jobId }

    fun getJob(jobId: Long): BackgroundJob<*, *>? = jobMap.keys.find { it.id == jobId }

    fun cancelJob(job: BackgroundJob<*, *>) {
        jobMap[job]?.cancel()
        jobMap.remove(job)
    }

    fun onFinished(job: BackgroundJob<*, *>) {
        jobMap.remove(job)
    }

    fun runOnUiThread(runnable: Runnable) {
        handler.post(runnable)
    }

    companion object {
        private val LOG_TAG: String = BackgroundJobHandler::class.java.simpleName

        @JvmStatic
        fun newFixedThreadPoolBackgroundJobHandler(numThreads: Int): BackgroundJobHandler = BackgroundJobHandler(numThreads)
    }
}
