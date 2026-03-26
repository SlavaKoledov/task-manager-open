package com.taskmanager.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.taskmanager.android.data.repository.TaskManagerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TaskSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TaskManagerRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val result = repository.syncAllPendingBaseUrls()
        return when {
            result.success -> Result.success()
            result.shouldRetry -> Result.retry()
            else -> Result.failure()
        }
    }
}
