package com.taskmanager.android.domain

import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskRepeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class CalendarDateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
)

data class CalendarMonthDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
)

data class CalendarTaskOccurrence(
    val task: TaskItem,
    val date: LocalDate,
    val isRecurring: Boolean,
)

fun getCalendarWeekStart(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun getCalendarMonthRange(month: YearMonth): CalendarDateRange {
    val start = getCalendarWeekStart(month.atDay(1))
    return CalendarDateRange(
        start = start,
        endInclusive = start.plusDays(41),
    )
}

fun buildCalendarMonthDays(month: YearMonth): List<CalendarMonthDay> {
    val range = getCalendarMonthRange(month)
    return (0L..41L).map { index ->
        val date = range.start.plusDays(index)
        CalendarMonthDay(
            date = date,
            isCurrentMonth = YearMonth.from(date) == month,
        )
    }
}

fun resolveNextRecurringDate(repeat: TaskRepeat, currentDate: LocalDate): LocalDate = when (repeat) {
    TaskRepeat.NONE -> currentDate
    TaskRepeat.DAILY -> currentDate.plusDays(1)
    TaskRepeat.WEEKLY -> currentDate.plusWeeks(1)
    TaskRepeat.MONTHLY -> currentDate.plusMonths(1)
    TaskRepeat.YEARLY -> currentDate.plusYears(1)
}

private fun getFirstRecurringCandidate(baseDate: LocalDate, repeat: TaskRepeat, rangeStart: LocalDate): LocalDate {
    var cursor = resolveNextRecurringDate(repeat, baseDate)
    if (cursor >= rangeStart) {
        return cursor
    }

    cursor = when (repeat) {
        TaskRepeat.DAILY -> cursor.plusDays(kotlin.math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(cursor, rangeStart)))
        TaskRepeat.WEEKLY -> {
            val diffDays = kotlin.math.max(0L, java.time.temporal.ChronoUnit.DAYS.between(cursor, rangeStart))
            cursor.plusWeeks((diffDays + 6) / 7)
        }
        TaskRepeat.MONTHLY,
        TaskRepeat.YEARLY,
        TaskRepeat.NONE,
        -> cursor
    }

    while (cursor < rangeStart) {
        cursor = resolveNextRecurringDate(repeat, cursor)
    }

    return cursor
}

fun compareCalendarOccurrences(left: CalendarTaskOccurrence, right: CalendarTaskOccurrence): Int {
    if (left.task.isDone != right.task.isDone) {
        return if (left.task.isDone) 1 else -1
    }

    val leftReminder = left.task.reminderTime.orEmpty()
    val rightReminder = right.task.reminderTime.orEmpty()

    if (leftReminder.isNotBlank() && rightReminder.isBlank()) {
        return -1
    }

    if (leftReminder.isBlank() && rightReminder.isNotBlank()) {
        return 1
    }

    if (leftReminder != rightReminder) {
        return leftReminder.compareTo(rightReminder)
    }

    val titleDelta = left.task.title.compareTo(right.task.title)
    if (titleDelta != 0) {
        return titleDelta
    }

    return left.task.id.compareTo(right.task.id)
}

fun buildTaskOccurrencesInRange(
    tasks: List<TaskItem>,
    range: CalendarDateRange,
): List<CalendarTaskOccurrence> {
    val occurrences = mutableListOf<CalendarTaskOccurrence>()

    for (task in tasks) {
        val baseDate = task.dueDate?.let(::parseLocalDateString) ?: continue

        if (baseDate in range.start..range.endInclusive) {
            occurrences += CalendarTaskOccurrence(
                task = task,
                date = baseDate,
                isRecurring = false,
            )
        }

        if (task.repeat == TaskRepeat.NONE || task.isDone) {
            continue
        }

        val repeatUntil = task.repeatUntil?.let(::parseLocalDateString)
        var cursor = getFirstRecurringCandidate(baseDate, task.repeat, range.start)

        while (cursor <= range.endInclusive) {
            if (repeatUntil != null && cursor > repeatUntil) {
                break
            }

            occurrences += CalendarTaskOccurrence(
                task = task,
                date = cursor,
                isRecurring = true,
            )
            cursor = resolveNextRecurringDate(task.repeat, cursor)
        }
    }

    return occurrences.sortedWith { left, right ->
        val dateDelta = left.date.compareTo(right.date)
        if (dateDelta != 0) {
            dateDelta
        } else {
            compareCalendarOccurrences(left, right)
        }
    }
}

fun groupTaskOccurrencesByDate(
    occurrences: List<CalendarTaskOccurrence>,
): Map<LocalDate, List<CalendarTaskOccurrence>> =
    occurrences.groupBy(CalendarTaskOccurrence::date)
