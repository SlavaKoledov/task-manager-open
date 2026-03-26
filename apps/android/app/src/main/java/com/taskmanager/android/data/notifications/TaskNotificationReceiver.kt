package com.taskmanager.android.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TaskNotificationReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notificationManager: TaskNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    TaskNotificationManager.ALARM_KIND_DAILY,
                    TaskNotificationManager.ALARM_KIND_TASK_REMINDER,
                    -> notificationManager.handleAlarm(
                        kind = intent.action.orEmpty(),
                        taskId = intent.getIntExtra(TaskNotificationManager.EXTRA_TASK_ID, 0).takeIf { it != 0 },
                    )
                    TaskNotificationManager.ACTION_REFRESH_SCHEDULES -> notificationManager.refreshSchedules()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
