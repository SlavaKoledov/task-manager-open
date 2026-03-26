package com.taskmanager.android.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.taskmanager.android.MainActivity
import com.taskmanager.android.data.local.LocalCacheMapper
import com.taskmanager.android.data.local.TaskManagerDao
import com.taskmanager.android.data.preferences.AppPreferencesStore
import com.taskmanager.android.domain.getLocalDateString
import com.taskmanager.android.domain.isDueDateOverdue
import com.taskmanager.android.model.TaskItem
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TaskNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val notificationManager: NotificationManagerCompat,
    private val dao: TaskManagerDao,
    private val localCacheMapper: LocalCacheMapper,
    private val preferencesStore: AppPreferencesStore,
    private val clock: Clock,
) {
    suspend fun refreshSchedules() = withContext(Dispatchers.IO) {
        val preferences = preferencesStore.currentPreferences()
        val taskItems = localCacheMapper.toTaskItems(dao.getTasks(preferences.baseUrl))
        scheduleDailyNotification(preferences.dailyNotificationEnabled, preferences.dailyNotificationTime)
        scheduleNextTaskReminder(taskItems)
    }

    suspend fun handleAlarm(kind: String, taskId: Int?) = withContext(Dispatchers.IO) {
        val preferences = preferencesStore.currentPreferences()
        val tasks = localCacheMapper.toTaskItems(dao.getTasks(preferences.baseUrl))
        when (kind) {
            ALARM_KIND_DAILY -> showDailyNotification(tasks, getLocalDateString())
            ALARM_KIND_TASK_REMINDER -> {
                val task = taskId?.let { id ->
                    tasks.firstOrNull { it.id == id }
                        ?: tasks.firstOrNull { taskItem -> taskItem.subtasks.any { it.id == id } }
                            ?.subtasks
                            ?.firstOrNull { it.id == id }
                }
                if (task != null && !task.isDone && !task.reminderTime.isNullOrBlank()) {
                    showTaskReminder(task)
                }
            }
        }

        refreshSchedules()
    }

    fun showDailyNotification(tasks: List<TaskItem>, todayString: String) {
        val lines = buildDailyNotificationLines(tasks, todayString)
        if (lines.isEmpty()) return
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()
        notificationManager.notify(
            DAILY_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Сегодняшние задачи:")
                .setContentText(lines.first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
                .setContentIntent(buildOpenAppIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    fun showTaskReminder(task: com.taskmanager.android.model.BaseTask) {
        if (!notificationManager.areNotificationsEnabled()) return
        ensureChannel()
        notificationManager.notify(
            TASK_REMINDER_NOTIFICATION_ID_BASE + kotlin.math.abs(task.id),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Reminder")
                .setContentText(task.title)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        buildString {
                            append(task.title)
                            task.dueDate?.let { append("\nDue $it") }
                            task.reminderTime?.let { append(" at $it") }
                        },
                    ),
                )
                .setContentIntent(buildOpenAppIntent())
                .setAutoCancel(true)
                .build(),
        )
    }

    fun scheduleDailyNotification(enabled: Boolean, localTime: String) {
        val pendingIntent = buildAlarmIntent(kind = ALARM_KIND_DAILY)
        alarmManager.cancel(pendingIntent)
        if (!enabled) return

        scheduleAlarm(nextOccurrence(localTime), pendingIntent)
    }

    fun scheduleNextTaskReminder(taskItems: List<TaskItem>) {
        val pendingIntent = buildAlarmIntent(kind = ALARM_KIND_TASK_REMINDER)
        alarmManager.cancel(pendingIntent)
        val nextReminder = nextTaskReminder(taskItems) ?: return
        scheduleAlarm(
            nextReminder.at,
            buildAlarmIntent(kind = ALARM_KIND_TASK_REMINDER, taskId = nextReminder.taskId),
        )
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            },
        )
    }

    private fun buildAlarmIntent(kind: String, taskId: Int? = null): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            if (kind == ALARM_KIND_DAILY) 2001 else 2002,
            Intent(context, TaskNotificationReceiver::class.java)
                .setAction(kind)
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun scheduleAlarm(triggerAt: Instant, pendingIntent: PendingIntent) {
        val triggerAtMillis = triggerAt.toEpochMilli()
        if (Build.VERSION.SDK_INT >= 23 && canScheduleExactAlarms()) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }.getOrElse {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 23) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun nextOccurrence(localTime: String): Instant {
        val zoneId = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(clock.millis()).atZone(zoneId)
        val time = LocalTime.parse(localTime)
        val todayCandidate = now.toLocalDate().atTime(time).atZone(zoneId)
        val target = if (todayCandidate.toInstant().isAfter(now.toInstant())) todayCandidate else todayCandidate.plusDays(1)
        return target.toInstant()
    }

    private fun nextTaskReminder(tasks: List<TaskItem>): ScheduledTaskReminder? {
        val zoneId = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(clock.millis()).atZone(zoneId).toInstant()
        return tasks
            .asSequence()
            .flatMap { sequenceOf<com.taskmanager.android.model.BaseTask>(it) + it.subtasks.asSequence() }
            .filter { !it.isDone && !it.dueDate.isNullOrBlank() && !it.reminderTime.isNullOrBlank() }
            .mapNotNull { task ->
                val dueDate = runCatching { LocalDate.parse(task.dueDate) }.getOrNull() ?: return@mapNotNull null
                val reminderTime = runCatching { LocalTime.parse(task.reminderTime) }.getOrNull() ?: return@mapNotNull null
                val reminderAt = LocalDateTime.of(dueDate, reminderTime).atZone(zoneId).toInstant()
                if (reminderAt.isAfter(now)) ScheduledTaskReminder(task.id, reminderAt) else null
            }
            .minByOrNull(ScheduledTaskReminder::at)
    }

    private fun buildOpenAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            3001,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Tasks",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        const val ALARM_KIND_DAILY = "com.taskmanager.android.notifications.DAILY"
        const val ALARM_KIND_TASK_REMINDER = "com.taskmanager.android.notifications.TASK_REMINDER"
        const val ACTION_REFRESH_SCHEDULES = "com.taskmanager.android.notifications.REFRESH_SCHEDULES"
        const val CHANNEL_ID = "task_notifications"
        const val EXTRA_TASK_ID = "task_id"
        const val DAILY_NOTIFICATION_ID = 4101
        const val TASK_REMINDER_NOTIFICATION_ID_BASE = 4200

        fun buildDailyNotificationLines(tasks: List<TaskItem>, todayString: String): List<String> {
            val visibleTasks = tasks
                .asSequence()
                .flatMap { sequenceOf<com.taskmanager.android.model.BaseTask>(it) + it.subtasks.asSequence() }
                .filter { !it.isDone && (it.dueDate == todayString || isDueDateOverdue(it.dueDate, todayString)) }
                .toList()
            val lines = visibleTasks
                .sortedWith(
                    compareBy<com.taskmanager.android.model.BaseTask>(
                        { if (isDueDateOverdue(it.dueDate, todayString)) 0 else if (it.reminderTime.isNullOrBlank()) 1 else 2 },
                        { it.reminderTime ?: "99:99" },
                        com.taskmanager.android.model.BaseTask::title,
                    ),
                )
                .map { task ->
                    when {
                        isDueDateOverdue(task.dueDate, todayString) -> "Overdue \"${task.title}\""
                        !task.reminderTime.isNullOrBlank() -> "${task.reminderTime} \"${task.title}\""
                        else -> "Today \"${task.title}\""
                    }
                }
            return if (lines.size <= 6) {
                lines
            } else {
                lines.take(6) + "+${lines.size - 6} more"
            }
        }
    }

    private data class ScheduledTaskReminder(
        val taskId: Int,
        val at: Instant,
    )
}
