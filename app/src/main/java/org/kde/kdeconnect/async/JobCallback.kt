package org.kde.kdeconnect.async

interface JobCallback {
    fun onResult(jobId: Int)
    fun onError(jobId: Int, error: Throwable)
}
