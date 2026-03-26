package com.taskmanager.android.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskSyncSchedulerTest {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerTaskSyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerTaskSyncScheduler(workManager)
    }

    @Test
    fun `buildRequest configures connected network and exponential backoff`() {
        val request = scheduler.buildRequest()

        assertThat(request.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(request.workSpec.backoffPolicy).isEqualTo(BackoffPolicy.EXPONENTIAL)
        assertThat(request.workSpec.backoffDelayDuration).isEqualTo(TimeUnit.MINUTES.toMillis(15))
    }

    @Test
    fun `enqueuePendingSync uses unique work and avoids duplicates`() = runBlocking {
        scheduler.enqueuePendingSync()
        scheduler.enqueuePendingSync()

        val workInfos = workManager.getWorkInfosForUniqueWork(WorkManagerTaskSyncScheduler.UNIQUE_WORK_NAME).get()

        assertThat(workInfos).hasSize(1)
        assertThat(workInfos.single().state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }
}
