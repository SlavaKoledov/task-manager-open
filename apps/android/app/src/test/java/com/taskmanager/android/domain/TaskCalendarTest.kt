package com.taskmanager.android.domain

import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskCustomRepeatUnit
import com.taskmanager.android.model.TaskRepeat
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class TaskCalendarTest {
    @Test
    fun `build calendar month days includes monday-aligned leading days`() {
        val days = buildCalendarMonthDays(YearMonth.of(2026, 3))

        assertThat(days).hasSize(42)
        assertThat(days.first().date).isEqualTo(LocalDate.parse("2026-02-23"))
        assertThat(days.last().date).isEqualTo(LocalDate.parse("2026-04-05"))
    }

    @Test
    fun `build task occurrences includes base due date and recurring instances`() {
        val occurrences = buildTaskOccurrencesInRange(
            tasks = listOf(
                testTaskItem(
                    id = 1,
                    title = "Release review",
                    dueDate = "2026-03-18",
                    reminderTime = "09:30",
                ),
                testTaskItem(
                    id = 2,
                    title = "Gym",
                    dueDate = "2026-03-15",
                    repeat = com.taskmanager.android.model.TaskRepeat.DAILY,
                    repeatUntil = "2026-03-18",
                ),
            ),
            range = CalendarDateRange(
                start = LocalDate.parse("2026-03-15"),
                endInclusive = LocalDate.parse("2026-03-20"),
            ),
        )

        assertThat(occurrences.map { Triple(it.task.id, it.date.toString(), it.isRecurring) }).containsExactly(
            Triple(2, "2026-03-15", false),
            Triple(2, "2026-03-16", true),
            Triple(2, "2026-03-17", true),
            Triple(1, "2026-03-18", false),
            Triple(2, "2026-03-18", true),
        ).inOrder()
    }

    @Test
    fun `compare calendar occurrences keeps incomplete timed tasks first`() {
        val occurrences = listOf(
            CalendarTaskOccurrence(
                task = testTaskItem(id = 1, title = "No time", dueDate = "2026-03-18"),
                date = LocalDate.parse("2026-03-18"),
                isRecurring = false,
            ),
            CalendarTaskOccurrence(
                task = testTaskItem(id = 2, title = "Morning", dueDate = "2026-03-18", reminderTime = "08:00"),
                date = LocalDate.parse("2026-03-18"),
                isRecurring = false,
            ),
            CalendarTaskOccurrence(
                task = testTaskItem(id = 3, title = "Done early", dueDate = "2026-03-18", reminderTime = "07:00", isDone = true),
                date = LocalDate.parse("2026-03-18"),
                isRecurring = false,
            ),
        ).sortedWith(::compareCalendarOccurrences)

        assertThat(occurrences.map { it.task.title }).containsExactly("Morning", "No time", "Done early").inOrder()
    }

    @Test
    fun `group task occurrences by date keeps each day bucket`() {
        val grouped = groupTaskOccurrencesByDate(
            buildTaskOccurrencesInRange(
                tasks = listOf(
                    testTaskItem(
                        id = 4,
                        title = "Weekly planning",
                        dueDate = "2026-03-16",
                        repeat = com.taskmanager.android.model.TaskRepeat.WEEKLY,
                        repeatUntil = "2026-03-30",
                    ),
                ),
                range = CalendarDateRange(
                    start = LocalDate.parse("2026-03-16"),
                    endInclusive = LocalDate.parse("2026-03-31"),
                ),
            ),
        )

        assertThat(grouped.keys.map(LocalDate::toString)).containsExactly(
            "2026-03-16",
            "2026-03-23",
            "2026-03-30",
        )
    }

    @Test
    fun `done recurring tasks do not keep projecting future occurrences`() {
        val occurrences = buildTaskOccurrencesInRange(
            tasks = listOf(
                testTaskItem(
                    id = 9,
                    title = "Completed repeat",
                    dueDate = "2026-03-17",
                    isDone = true,
                    repeat = com.taskmanager.android.model.TaskRepeat.DAILY,
                    repeatUntil = "2026-03-20",
                ),
            ),
            range = CalendarDateRange(
                start = LocalDate.parse("2026-03-17"),
                endInclusive = LocalDate.parse("2026-03-20"),
            ),
        )

        assertThat(occurrences.map { Triple(it.task.id, it.date.toString(), it.isRecurring) }).containsExactly(
            Triple(9, "2026-03-17", false),
        )
    }

    @Test
    fun `custom repeat logic handles skip weekends weekly intervals and safe month dates`() {
        assertThat(
            resolveNextRecurringDate(
                repeat = TaskRepeat.CUSTOM,
                currentDate = LocalDate.parse("2026-03-13"),
                repeatConfig = TaskCustomRepeatConfig(
                    interval = 1,
                    unit = TaskCustomRepeatUnit.DAY,
                    skipWeekends = true,
                ),
            ),
        ).isEqualTo(LocalDate.parse("2026-03-16"))

        assertThat(
            resolveNextRecurringDate(
                repeat = TaskRepeat.CUSTOM,
                currentDate = LocalDate.parse("2026-03-16"),
                repeatConfig = TaskCustomRepeatConfig(
                    interval = 2,
                    unit = TaskCustomRepeatUnit.WEEK,
                    weekdays = listOf(1, 3, 5),
                ),
            ),
        ).isEqualTo(LocalDate.parse("2026-03-18"))

        assertThat(
            resolveNextRecurringDate(
                repeat = TaskRepeat.CUSTOM,
                currentDate = LocalDate.parse("2026-01-31"),
                repeatConfig = TaskCustomRepeatConfig(
                    interval = 1,
                    unit = TaskCustomRepeatUnit.MONTH,
                    monthDay = 31,
                ),
            ),
        ).isEqualTo(LocalDate.parse("2026-02-28"))
    }

    @Test
    fun `calendar occurrences include custom recurring tasks`() {
        val occurrences = buildTaskOccurrencesInRange(
            tasks = listOf(
                testTaskItem(
                    id = 11,
                    title = "Custom weekly",
                    dueDate = "2026-03-16",
                    repeat = TaskRepeat.CUSTOM,
                    repeatConfig = TaskCustomRepeatConfig(
                        interval = 2,
                        unit = TaskCustomRepeatUnit.WEEK,
                        weekdays = listOf(1, 3, 5),
                    ),
                    repeatUntil = "2026-04-03",
                ),
            ),
            range = CalendarDateRange(
                start = LocalDate.parse("2026-03-16"),
                endInclusive = LocalDate.parse("2026-04-05"),
            ),
        )

        assertThat(occurrences.map { it.date.toString() }).containsExactly(
            "2026-03-16",
            "2026-03-18",
            "2026-03-20",
            "2026-03-30",
            "2026-04-01",
            "2026-04-03",
        ).inOrder()
        assertThat(getTaskRepeatSummary(TaskRepeat.CUSTOM, occurrences.first().task.repeatConfig))
            .isEqualTo("Every 2 weeks on M, W, F")
    }
}
