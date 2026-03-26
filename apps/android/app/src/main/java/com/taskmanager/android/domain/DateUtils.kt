package com.taskmanager.android.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val humanDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

fun getLocalDateString(date: LocalDate = LocalDate.now()): String = date.toString()

fun getTomorrowDateString(date: LocalDate = LocalDate.now()): String = date.plusDays(1).toString()

fun getOffsetLocalDateString(offsetDays: Long, date: LocalDate = LocalDate.now()): String =
    date.plusDays(offsetDays).toString()

fun parseLocalDateString(value: String): LocalDate? = try {
    if (value.isBlank()) {
        null
    } else {
        LocalDate.parse(value)
    }
} catch (_: DateTimeParseException) {
    null
}

fun formatDueDateLabel(
    dueDate: String?,
    todayString: String,
    tomorrowString: String = getTomorrowDateString(parseLocalDateString(todayString) ?: LocalDate.now()),
): String? {
    if (dueDate.isNullOrBlank()) {
        return null
    }

    if (dueDate == todayString) {
        return "Today"
    }

    if (dueDate == tomorrowString) {
        return "Tomorrow"
    }

    return parseLocalDateString(dueDate)?.format(humanDateFormatter) ?: dueDate
}

fun getLocalTimeZoneId(): String = ZoneId.systemDefault().id

fun getMillisecondsUntilNextLocalMidnight(now: ZonedDateTime = ZonedDateTime.now()): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return java.time.Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0L)
}
