package com.taskmanager.android.domain

import com.taskmanager.android.model.TaskItem

private val taskTimePattern = Regex("""^(\d{2}):(\d{2})$""")

fun normalizeTaskTime(value: String?): String? {
    if (value.isNullOrBlank()) {
        return null
    }

    val trimmed = value.trim()
    val match = taskTimePattern.matchEntire(trimmed) ?: return null
    val hours = match.groupValues[1].toIntOrNull() ?: return null
    val minutes = match.groupValues[2].toIntOrNull() ?: return null
    if (hours !in 0..23 || minutes !in 0..59) {
        return null
    }

    return trimmed
}

fun parseTaskTimeToMinutes(value: String?): Int? {
    val normalized = normalizeTaskTime(value) ?: return null
    val parts = normalized.split(":")
    return (parts[0].toInt() * 60) + parts[1].toInt()
}

fun validateTaskTimeRange(
    dueDate: String?,
    startTime: String?,
    endTime: String?,
): String? {
    if (dueDate.isNullOrBlank()) {
        return null
    }

    val normalizedStart = normalizeTaskTime(startTime)
    val normalizedEnd = normalizeTaskTime(endTime)

    if (normalizedStart == null && normalizedEnd != null) {
        return "Choose a start time before setting an end time."
    }

    if (normalizedStart != null && normalizedEnd != null) {
        val startMinutes = parseTaskTimeToMinutes(normalizedStart)
        val endMinutes = parseTaskTimeToMinutes(normalizedEnd)
        if (startMinutes != null && endMinutes != null && endMinutes <= startMinutes) {
            return "End time must be later than the start time."
        }
    }

    return null
}

fun formatTaskTimeRange(
    startTime: String?,
    endTime: String?,
): String? {
    val normalizedStart = normalizeTaskTime(startTime) ?: return null
    val normalizedEnd = normalizeTaskTime(endTime)
    return if (normalizedEnd != null) {
        "$normalizedStart\u2013$normalizedEnd"
    } else {
        normalizedStart
    }
}

fun compareTaskStartTimes(
    leftStartTime: String?,
    rightStartTime: String?,
    leftEndTime: String? = null,
    rightEndTime: String? = null,
): Int {
    val leftStartMinutes = parseTaskTimeToMinutes(leftStartTime)
    val rightStartMinutes = parseTaskTimeToMinutes(rightStartTime)

    if (leftStartMinutes != null && rightStartMinutes == null) {
        return -1
    }
    if (leftStartMinutes == null && rightStartMinutes != null) {
        return 1
    }
    if (leftStartMinutes != null && rightStartMinutes != null && leftStartMinutes != rightStartMinutes) {
        return leftStartMinutes - rightStartMinutes
    }

    val leftEndMinutes = parseTaskTimeToMinutes(leftEndTime)
    val rightEndMinutes = parseTaskTimeToMinutes(rightEndTime)
    if (leftEndMinutes != null && rightEndMinutes != null && leftEndMinutes != rightEndMinutes) {
        return leftEndMinutes - rightEndMinutes
    }

    return 0
}

fun compareTaskItemsByTime(left: TaskItem, right: TaskItem): Int {
    val timeDelta = compareTaskStartTimes(
        left.startTime,
        right.startTime,
        left.endTime,
        right.endTime,
    )
    if (timeDelta != 0) {
        return timeDelta
    }

    if (left.position != right.position) {
        return left.position - right.position
    }

    val createdAtDelta = right.createdAt.compareTo(left.createdAt)
    if (createdAtDelta != 0) {
        return createdAtDelta
    }

    return right.id - left.id
}
