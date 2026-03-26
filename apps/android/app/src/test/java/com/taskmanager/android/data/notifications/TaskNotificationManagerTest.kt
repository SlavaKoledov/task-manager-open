package com.taskmanager.android.data.notifications

import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.testTaskSubtask
import org.junit.Test

class TaskNotificationManagerTest {
    @Test
    fun `daily notification lines include overdue today and timed tasks in order`() {
        val lines = TaskNotificationManager.buildDailyNotificationLines(
            tasks = listOf(
                testTaskItem(id = 1, title = "Overdue task", dueDate = "2026-03-14"),
                testTaskItem(id = 2, title = "Today task", dueDate = "2026-03-15"),
                testTaskItem(
                    id = 3,
                    title = "Parent",
                    dueDate = "2026-03-16",
                    subtasks = listOf(
                        testTaskSubtask(id = 31, title = "Subtask reminder").copy(
                            dueDate = "2026-03-15",
                            reminderTime = "16:00",
                        ),
                    ),
                ),
            ),
            todayString = "2026-03-15",
        )

        assertThat(lines).containsExactly(
            "Overdue \"Overdue task\"",
            "Today \"Today task\"",
            "16:00 \"Subtask reminder\"",
        ).inOrder()
    }
}
