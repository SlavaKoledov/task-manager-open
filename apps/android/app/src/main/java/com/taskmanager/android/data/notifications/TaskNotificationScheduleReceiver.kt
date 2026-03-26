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
class TaskNotificationScheduleReceiver : BroadcastReceiver() {
    @Inject
    lateinit var notificationManager: TaskNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                notificationManager.refreshSchedules()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
