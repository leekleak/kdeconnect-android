package org.kde.kdeconnect.async

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.kde.kdeconnect.helpers.LoggerTagged
import java.util.concurrent.ConcurrentHashMap

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DataTransferJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val jobId = params.extras.getInt(EXTRA_DATA_TRANSFER_JOB_ID, -1)
        if (jobId == -1) {
            LoggerTagged.e { "DataTransferJobService: Missing job ID" }
            return false
        }

        val job = DataTransferJobRegistry.get(jobId)
        if (job == null) {
            LoggerTagged.e { "DataTransferJobService: Job $jobId not found in registry" }
            return false
        }

        val notification = job.getNotification()
        val notificationId = job.getNotificationId()

        setNotification(params, notificationId, notification, JOB_END_NOTIFICATION_POLICY_DETACH)

        val coroutineJob = serviceScope.launch {
            try {
                job.run()
                jobFinished(params, false)
            } catch (e: Exception) {
                LoggerTagged.e(e) { "DataTransferJobService: Job $jobId failed" }
                jobFinished(params, true)
            } finally {
                activeJobs.remove(jobId)
                DataTransferJobRegistry.unregister(jobId)
            }
        }
        activeJobs[jobId] = coroutineJob

        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val jobId = params.extras.getInt(EXTRA_DATA_TRANSFER_JOB_ID, -1)
        if (jobId != -1) {
            DataTransferJobRegistry.get(jobId)?.cancel()
            activeJobs[jobId]?.cancel()
            activeJobs.remove(jobId)
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_DATA_TRANSFER_JOB_ID = "dataTransferJobId"
    }
}
