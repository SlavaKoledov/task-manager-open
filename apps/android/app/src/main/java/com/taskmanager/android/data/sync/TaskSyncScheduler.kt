package com.taskmanager.android.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface TaskSyncScheduler {
    fun enqueuePendingSync()
}

@Singleton
class WorkManagerTaskSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : TaskSyncScheduler {
    override fun enqueuePendingSync() {
        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            buildRequest(),
        )
    }

    internal fun buildRequest(): OneTimeWorkRequest =
        androidx.work.OneTimeWorkRequestBuilder<TaskSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()

    companion object {
        const val UNIQUE_WORK_NAME = "task_manager_pending_sync"
    }
}
