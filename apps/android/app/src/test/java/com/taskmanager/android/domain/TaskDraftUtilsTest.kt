package com.taskmanager.android.domain

import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.EditableSubtaskDraft
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskCustomRepeatUnit
import com.taskmanager.android.model.TaskDraft
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.model.TaskViewTarget
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class TaskDraftUtilsTest {
    @Test
    fun `build task draft applies view and section defaults`() {
        val todayDraft = buildTaskDraft(
            context = TaskEditorContext(
                viewTarget = TaskViewTarget.Today,
                sectionId = TaskSectionId.URGENT_IMPORTANT,
            ),
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )

        val allLaterDraft = buildTaskDraft(
            context = TaskEditorContext(
                viewTarget = TaskViewTarget.All,
                groupId = AllTaskGroupId.LATER,
                sectionId = TaskSectionId.URGENT_UNIMPORTANT,
            ),
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )

        val listDraft = buildTaskDraft(
            context = TaskEditorContext(
                viewTarget = TaskViewTarget.ListView(7, "Personal"),
            ),
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )
        val calendarDraft = buildTaskDraft(
            context = TaskEditorContext(
                viewTarget = TaskViewTarget.Calendar,
                prefilledDueDate = "2026-03-22",
            ),
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )

        assertThat(todayDraft.dueDate).isEqualTo("2026-03-15")
        assertThat(todayDraft.priority).isEqualTo(TaskPriority.URGENT_IMPORTANT)
        assertThat(allLaterDraft.dueDate).isEqualTo("2026-03-23")
        assertThat(allLaterDraft.priority).isEqualTo(TaskPriority.URGENT_UNIMPORTANT)
        assertThat(listDraft.listId).isEqualTo(7)
        assertThat(calendarDraft.dueDate).isEqualTo("2026-03-22")
    }

    @Test
    fun `build create payload keeps ordered subtasks and repeat until`() {
        val payload = buildTaskCreatePayloadFromDraft(
            draft = TaskDraft(
                title = "Plan trip",
                description = "Book hotel\n- [x] Buy tickets",
                dueDate = "2026-04-01",
                reminderTime = "08:45",
                repeatUntil = "2026-05-01",
                isPinned = true,
                priority = TaskPriority.NOT_URGENT_IMPORTANT,
                repeat = TaskRepeat.MONTHLY,
                listId = 5,
            ),
            subtasks = listOf(
                EditableSubtaskDraft(id = -1, title = "Passport", position = 0),
                EditableSubtaskDraft(id = -2, title = "Travel insurance", position = 1),
            ),
        )

        assertThat(payload.title).isEqualTo("Plan trip")
        assertThat(payload.description).isEqualTo("Book hotel\n- [x] Buy tickets")
        assertThat(payload.reminderTime).isEqualTo("08:45")
        assertThat(payload.repeatUntil).isEqualTo("2026-05-01")
        assertThat(payload.isPinned).isTrue()
        assertThat(payload.priority).isEqualTo(TaskPriority.NOT_URGENT_IMPORTANT.wire)
        assertThat(payload.listId).isEqualTo(5)
        assertThat(payload.subtasks.map { it.title }).containsExactly("Passport", "Travel insurance").inOrder()
    }

    @Test
    fun `build update payload excludes create only fields`() {
        val payload = buildTaskUpdatePayloadJson(
            draft = TaskDraft(
                title = "Refine roadmap",
                dueDate = "2026-03-20",
                reminderTime = "07:30",
                repeatUntil = "2026-04-20",
                isDone = true,
                priority = TaskPriority.URGENT_IMPORTANT,
                repeat = TaskRepeat.WEEKLY,
                listId = 9,
            ),
        )

        assertThat(payload.keys).doesNotContain("parent_id")
        assertThat(payload.keys).doesNotContain("subtasks")
        assertThat(payload["title"]?.jsonPrimitive?.content).isEqualTo("Refine roadmap")
        assertThat(payload["due_date"]?.jsonPrimitive?.content).isEqualTo("2026-03-20")
        assertThat(payload["reminder_time"]?.jsonPrimitive?.content).isEqualTo("07:30")
        assertThat(payload["repeat_until"]?.jsonPrimitive?.content).isEqualTo("2026-04-20")
        assertThat(payload["priority"]?.jsonPrimitive?.content).isEqualTo(TaskPriority.URGENT_IMPORTANT.wire)
    }

    @Test
    fun `validate task draft enforces repeat rules`() {
        val missingRepeatError = validateTaskDraft(
            TaskDraft(
                title = "Recurring without repeat",
                dueDate = "2026-03-20",
                repeatUntil = "2026-03-25",
            ),
        )
        val missingDueDateError = validateTaskDraft(
            TaskDraft(
                title = "Recurring without date",
                repeat = TaskRepeat.WEEKLY,
            ),
        )
        val invalidEndDateError = validateTaskDraft(
            TaskDraft(
                title = "Recurring with invalid end",
                dueDate = "2026-03-20",
                repeat = TaskRepeat.WEEKLY,
                repeatUntil = "2026-03-19",
            ),
        )

        assertThat(missingRepeatError).contains("Choose a repeat schedule")
        assertThat(missingDueDateError).contains("Choose a due date")
        assertThat(invalidEndDateError).contains("cannot be earlier")
    }

    @Test
    fun `validate task draft requires due date for reminder`() {
        val error = validateTaskDraft(
            TaskDraft(
                title = "Reminder without date",
                reminderTime = "09:00",
            ),
        )

        assertThat(error).contains("Choose a due date before setting a reminder")
    }

    @Test
    fun `task draft validates and serializes start and end time`() {
        val missingStartError = validateTaskDraft(
            TaskDraft(
                title = "Bad end only",
                dueDate = "2026-03-20",
                endTime = "10:30",
            ),
        )
        val invalidRangeError = validateTaskDraft(
            TaskDraft(
                title = "Backwards time",
                dueDate = "2026-03-20",
                startTime = "10:30",
                endTime = "09:30",
            ),
        )
        val payload = buildTaskUpdatePayloadJson(
            TaskDraft(
                title = "Timed task",
                dueDate = "2026-03-20",
                startTime = "09:00",
                endTime = "10:30",
            ),
        )

        assertThat(missingStartError).contains("Choose a start time")
        assertThat(invalidRangeError).contains("End time must be later")
        assertThat(payload["start_time"]?.jsonPrimitive?.content).isEqualTo("09:00")
        assertThat(payload["end_time"]?.jsonPrimitive?.content).isEqualTo("10:30")
    }

    @Test
    fun `custom repeat validation and serialization work end to end`() {
        val invalidWeeklyError = validateTaskDraft(
            TaskDraft(
                title = "Custom weekly",
                dueDate = "2026-03-20",
                repeat = TaskRepeat.CUSTOM,
                repeatConfig = TaskCustomRepeatConfig(
                    interval = 2,
                    unit = TaskCustomRepeatUnit.WEEK,
                    weekdays = emptyList(),
                ),
            ),
        )

        val payload = buildTaskUpdatePayloadJson(
            TaskDraft(
                title = "Custom monthly",
                dueDate = "2026-03-20",
                repeat = TaskRepeat.CUSTOM,
                repeatConfig = TaskCustomRepeatConfig(
                    interval = 3,
                    unit = TaskCustomRepeatUnit.MONTH,
                    skipWeekends = true,
                    monthDay = 31,
                ),
            ),
        )

        assertThat(invalidWeeklyError).contains("Choose at least one weekday")
        assertThat(payload["repeat"]?.jsonPrimitive?.content).isEqualTo("custom")
        assertThat(payload["repeat_config"]?.jsonObject?.get("interval")?.jsonPrimitive?.int).isEqualTo(3)
        assertThat(payload["repeat_config"]?.jsonObject?.get("unit")?.jsonPrimitive?.content).isEqualTo("month")
        assertThat(payload["repeat_config"]?.jsonObject?.get("month_day")?.jsonPrimitive?.int).isEqualTo(31)
    }
}
