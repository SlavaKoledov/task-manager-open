package com.taskmanager.android.domain

import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskCustomRepeatUnit
import com.taskmanager.android.model.TaskRepeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

fun buildDefaultCustomRepeatConfig(
    unit: TaskCustomRepeatUnit,
    anchorDate: LocalDate,
    interval: Int = 1,
): TaskCustomRepeatConfig = TaskCustomRepeatConfig(
    interval = maxOf(1, interval),
    unit = unit,
    skipWeekends = false,
    weekdays = if (unit == TaskCustomRepeatUnit.WEEK) listOf(getIsoWeekday(anchorDate)) else emptyList(),
    monthDay = if (unit == TaskCustomRepeatUnit.MONTH) anchorDate.dayOfMonth else null,
    month = if (unit == TaskCustomRepeatUnit.YEAR) anchorDate.monthValue else null,
    day = if (unit == TaskCustomRepeatUnit.YEAR) anchorDate.dayOfMonth else null,
)

fun normalizeCustomRepeatConfig(config: TaskCustomRepeatConfig?): TaskCustomRepeatConfig? {
    if (config == null) {
        return null
    }

    return TaskCustomRepeatConfig(
        interval = maxOf(1, config.interval),
        unit = config.unit,
        skipWeekends = config.skipWeekends,
        weekdays = config.weekdays
            .distinct()
            .filter { it in 1..7 }
            .sorted(),
        monthDay = config.monthDay?.takeIf { it in 1..31 },
        month = config.month?.takeIf { it in 1..12 },
        day = config.day?.takeIf { it in 1..31 },
    )
}

fun ensureCustomRepeatConfig(config: TaskCustomRepeatConfig?, anchorDate: LocalDate): TaskCustomRepeatConfig =
    normalizeCustomRepeatConfig(config) ?: buildDefaultCustomRepeatConfig(TaskCustomRepeatUnit.DAY, anchorDate)

fun switchCustomRepeatUnit(
    config: TaskCustomRepeatConfig?,
    unit: TaskCustomRepeatUnit,
    anchorDate: LocalDate,
): TaskCustomRepeatConfig {
    val current = ensureCustomRepeatConfig(config, anchorDate)
    val next = buildDefaultCustomRepeatConfig(unit, anchorDate, current.interval)
    return next.copy(
        interval = current.interval,
        skipWeekends = if (unit == TaskCustomRepeatUnit.DAY || unit == TaskCustomRepeatUnit.MONTH) {
            current.skipWeekends
        } else {
            false
        },
    )
}

fun validateCustomRepeatConfig(config: TaskCustomRepeatConfig?): String? {
    val normalized = normalizeCustomRepeatConfig(config)
        ?: return "Choose a custom repeat pattern."

    if (normalized.interval < 1) {
        return "Custom repeat interval must be at least 1."
    }

    return when (normalized.unit) {
        TaskCustomRepeatUnit.DAY -> null
        TaskCustomRepeatUnit.WEEK -> if (normalized.weekdays.isEmpty()) {
            "Choose at least one weekday for a custom weekly repeat."
        } else {
            null
        }
        TaskCustomRepeatUnit.MONTH -> if (normalized.monthDay == null) {
            "Choose a day of the month for a custom monthly repeat."
        } else {
            null
        }
        TaskCustomRepeatUnit.YEAR -> when {
            normalized.month == null || normalized.day == null -> "Choose a month and date for a custom yearly repeat."
            normalized.day > YearMonth.of(if (normalized.month == 2) 2024 else 2025, normalized.month).lengthOfMonth() ->
                "Choose a valid month and date for a custom yearly repeat."
            else -> null
        }
    }
}

fun getTaskRepeatSummary(
    repeat: TaskRepeat,
    repeatConfig: TaskCustomRepeatConfig? = null,
): String {
    return when (repeat) {
        TaskRepeat.NONE -> "No repeat"
        TaskRepeat.DAILY -> "Daily"
        TaskRepeat.WEEKLY -> "Weekly"
        TaskRepeat.MONTHLY -> "Monthly"
        TaskRepeat.YEARLY -> "Yearly"
        TaskRepeat.CUSTOM -> {
            val normalized = normalizeCustomRepeatConfig(repeatConfig) ?: return "Custom"
            when (normalized.unit) {
                TaskCustomRepeatUnit.DAY ->
                    "Every ${formatInterval(normalized.interval, "day")}" +
                        if (normalized.skipWeekends) ", skip weekends" else ""
                TaskCustomRepeatUnit.WEEK ->
                    "Every ${formatInterval(normalized.interval, "week")} on ${
                        normalized.weekdays.joinToString(", ") { formatWeekdayLetter(it) }
                    }"
                TaskCustomRepeatUnit.MONTH ->
                    "Every ${formatInterval(normalized.interval, "month")} on ${normalized.monthDay}" +
                        if (normalized.skipWeekends) ", skip weekends" else ""
                TaskCustomRepeatUnit.YEAR -> {
                    val safeDate = resolveSafeDate(2025, normalized.month ?: 1, normalized.day ?: 1)
                    "Every ${formatInterval(normalized.interval, "year")} on ${
                        safeDate.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
                    } ${safeDate.dayOfMonth}"
                }
            }
        }
    }
}

fun resolveNextRecurringDate(
    repeat: TaskRepeat,
    currentDate: LocalDate,
    repeatConfig: TaskCustomRepeatConfig? = null,
): LocalDate = when (repeat) {
    TaskRepeat.NONE -> currentDate
    TaskRepeat.DAILY -> currentDate.plusDays(1)
    TaskRepeat.WEEKLY -> currentDate.plusWeeks(1)
    TaskRepeat.MONTHLY -> resolveNextMonthlyDate(currentDate, currentDate.dayOfMonth, 1)
    TaskRepeat.YEARLY -> resolveSafeDate(currentDate.year + 1, currentDate.monthValue, currentDate.dayOfMonth)
    TaskRepeat.CUSTOM -> {
        val normalized = normalizeCustomRepeatConfig(repeatConfig)
            ?: buildDefaultCustomRepeatConfig(TaskCustomRepeatUnit.DAY, currentDate)
        when (normalized.unit) {
            TaskCustomRepeatUnit.DAY -> {
                val nextDate = currentDate.plusDays(normalized.interval.toLong())
                if (normalized.skipWeekends) shiftForwardPastWeekend(nextDate) else nextDate
            }
            TaskCustomRepeatUnit.WEEK -> {
                val currentWeekday = getIsoWeekday(currentDate)
                val laterWeekdays = normalized.weekdays.filter { it > currentWeekday }
                if (laterWeekdays.isNotEmpty()) {
                    currentDate.plusDays((laterWeekdays.first() - currentWeekday).toLong())
                } else {
                    val nextWeekStart = currentDate
                        .minusDays((currentWeekday - 1).toLong())
                        .plusWeeks(normalized.interval.toLong())
                    nextWeekStart.plusDays(((normalized.weekdays.firstOrNull() ?: 1) - 1).toLong())
                }
            }
            TaskCustomRepeatUnit.MONTH -> {
                val nextDate = resolveNextMonthlyDate(currentDate, normalized.monthDay ?: currentDate.dayOfMonth, normalized.interval)
                if (normalized.skipWeekends) shiftForwardPastWeekend(nextDate) else nextDate
            }
            TaskCustomRepeatUnit.YEAR -> resolveSafeDate(
                currentDate.year + normalized.interval,
                normalized.month ?: currentDate.monthValue,
                normalized.day ?: currentDate.dayOfMonth,
            )
        }
    }
}

private fun getFirstRecurringCandidate(
    baseDate: LocalDate,
    repeat: TaskRepeat,
    rangeStart: LocalDate,
    repeatConfig: TaskCustomRepeatConfig? = null,
): LocalDate {
    var cursor = resolveNextRecurringDate(repeat, baseDate, repeatConfig)
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
        TaskRepeat.CUSTOM,
        TaskRepeat.NONE,
        -> cursor
    }

    while (cursor < rangeStart) {
        cursor = resolveNextRecurringDate(repeat, cursor, repeatConfig)
    }

    return cursor
}

fun compareCalendarOccurrences(left: CalendarTaskOccurrence, right: CalendarTaskOccurrence): Int {
    if (left.task.isDone != right.task.isDone) {
        return if (left.task.isDone) 1 else -1
    }

    val timeDelta = compareTaskStartTimes(
        left.task.startTime,
        right.task.startTime,
        left.task.endTime,
        right.task.endTime,
    )
    if (timeDelta != 0) {
        return timeDelta
    }

    if (left.task.position != right.task.position) {
        return left.task.position - right.task.position
    }

    val createdAtDelta = left.task.createdAt.compareTo(right.task.createdAt)
    if (createdAtDelta != 0) {
        return createdAtDelta
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
        var cursor = getFirstRecurringCandidate(baseDate, task.repeat, range.start, task.repeatConfig)

        while (cursor <= range.endInclusive) {
            if (repeatUntil != null && cursor > repeatUntil) {
                break
            }

            occurrences += CalendarTaskOccurrence(
                task = task,
                date = cursor,
                isRecurring = true,
            )
            cursor = resolveNextRecurringDate(task.repeat, cursor, task.repeatConfig)
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

fun getIsoWeekday(date: LocalDate): Int = date.dayOfWeek.value

fun resolveSafeDate(year: Int, month: Int, day: Int): LocalDate {
    val safeDay = minOf(day, YearMonth.of(year, month).lengthOfMonth())
    return LocalDate.of(year, month, safeDay)
}

private fun resolveNextMonthlyDate(currentDate: LocalDate, dayOfMonth: Int, intervalMonths: Int): LocalDate =
    resolveSafeDate(
        year = currentDate.plusMonths(intervalMonths.toLong()).year,
        month = currentDate.plusMonths(intervalMonths.toLong()).monthValue,
        day = dayOfMonth,
    )

private fun shiftForwardPastWeekend(date: LocalDate): LocalDate = when (date.dayOfWeek) {
    DayOfWeek.SATURDAY -> date.plusDays(2)
    DayOfWeek.SUNDAY -> date.plusDays(1)
    else -> date
}

private fun formatInterval(interval: Int, unit: String): String = "$interval $unit" + if (interval == 1) "" else "s"

private fun formatWeekdayLetter(isoWeekday: Int): String = when (isoWeekday) {
    1 -> "M"
    2 -> "T"
    3 -> "W"
    4 -> "T"
    5 -> "F"
    6 -> "S"
    7 -> "S"
    else -> "?"
}
