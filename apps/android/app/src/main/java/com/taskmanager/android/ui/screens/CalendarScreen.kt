package com.taskmanager.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taskmanager.android.domain.buildCalendarMonthDays
import com.taskmanager.android.domain.buildTaskOccurrencesInRange
import com.taskmanager.android.domain.compareCalendarOccurrences
import com.taskmanager.android.domain.formatTaskTimeRange
import com.taskmanager.android.domain.getCalendarMonthRange
import com.taskmanager.android.domain.getTaskRepeatSummary
import com.taskmanager.android.domain.groupTaskOccurrencesByDate
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.ui.components.PriorityCheckbox
import com.taskmanager.android.ui.components.parseHexColor
import com.taskmanager.android.ui.components.taskPriorityColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.US)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val selectedDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

@Composable
fun CalendarScreen(
    tasks: List<TaskItem>,
    lists: List<ListItem>,
    todayString: String,
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onChangeMonth: (YearMonth) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember(todayString) { com.taskmanager.android.domain.parseLocalDateString(todayString) ?: LocalDate.now() }
    val monthDays = remember(visibleMonth) { buildCalendarMonthDays(visibleMonth) }
    val monthOccurrences = remember(tasks, visibleMonth) {
        buildTaskOccurrencesInRange(tasks, getCalendarMonthRange(visibleMonth))
    }
    val occurrencesByDate = remember(monthOccurrences) {
        groupTaskOccurrencesByDate(monthOccurrences)
    }
    val listById = remember(lists) { lists.associateBy(ListItem::id) }
    val monthWeeks = remember(monthDays) { monthDays.chunked(7) }
    val markerColorsByDate = remember(occurrencesByDate, listById) {
        occurrencesByDate.mapValues { (_, occurrences) ->
            occurrences
                .take(3)
                .map { occurrence ->
                    occurrence.task.listId?.let { listById[it]?.color }?.let(::parseHexColor)
                        ?: taskPriorityColor(occurrence.task.priority)
                }
        }
    }
    val selectedOccurrences = remember(occurrencesByDate, selectedDate) {
        (occurrencesByDate[selectedDate] ?: emptyList()).sortedWith(::compareCalendarOccurrences)
    }
    val activeOccurrences = remember(selectedOccurrences) { selectedOccurrences.filterNot { it.task.isDone } }
    val completedOccurrences = remember(selectedOccurrences) { selectedOccurrences.filter { it.task.isDone } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = visibleMonth.format(monthFormatter),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { onChangeMonth(visibleMonth.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous month")
                }
                IconButton(onClick = { onChangeMonth(visibleMonth.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next month")
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(1, 2, 3, 4, 5, 6, 7).forEach { weekday ->
                        val label = weekdayFormatter.format(LocalDate.of(2026, 3, weekday + 1))
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                monthWeeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f)) {
                                CalendarDayCell(
                                    date = day.date,
                                    isCurrentMonth = day.isCurrentMonth,
                                    isSelected = day.date == selectedDate,
                                    isToday = day.date == today,
                                    markerColors = markerColorsByDate[day.date].orEmpty(),
                                    onClick = { onSelectDate(day.date) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedOccurrences.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = selectedDate.format(selectedDateFormatter).uppercase(Locale.US),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "No tasks for the selected date.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            CalendarTaskSection(
                title = selectedDate.format(selectedDateFormatter).uppercase(Locale.US),
                occurrences = activeOccurrences,
                listById = listById,
                onToggleTask = onToggleTask,
                onOpenTask = onOpenTask,
            )

            if (completedOccurrences.isNotEmpty()) {
                CalendarTaskSection(
                    title = "COMPLETED",
                    occurrences = completedOccurrences,
                    listById = listById,
                    onToggleTask = onToggleTask,
                    onOpenTask = onOpenTask,
                    muted = true,
                )
            }
        }

        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    markerColors: List<Color>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> Color.Transparent
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onSecondary
        isCurrentMonth -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.secondary
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .aspectRatio(0.82f)
            .clip(shape)
            .background(containerColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .testTag("calendar-day-$date"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
            )

            if (markerColors.isNotEmpty()) {
                Row(
                    modifier = Modifier.testTag("calendar-markers-$date"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    markerColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.onSecondary else color)
                                .testTag("calendar-marker-$date-$index"),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun CalendarTaskSection(
    title: String,
    occurrences: List<com.taskmanager.android.domain.CalendarTaskOccurrence>,
    listById: Map<Int, ListItem>,
    onToggleTask: (TaskItem) -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    muted: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (muted) MaterialTheme.colorScheme.surface.copy(alpha = 0.76f) else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )

            if (occurrences.isEmpty()) {
                Text(
                    text = "No tasks in this section.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                occurrences.forEach { occurrence ->
                    CalendarTaskRow(
                        task = occurrence.task,
                        accentColor = occurrence.task.listId?.let { listById[it]?.color }?.let(::parseHexColor)
                            ?: taskPriorityColor(occurrence.task.priority),
                        listName = occurrence.task.listId?.let { listById[it]?.name },
                        onToggleTask = onToggleTask,
                        onOpenTask = onOpenTask,
                        muted = muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarTaskRow(
    task: TaskItem,
    accentColor: Color,
    listName: String?,
    onToggleTask: (TaskItem) -> Unit,
    onOpenTask: (TaskItem) -> Unit,
    muted: Boolean,
) {
    val timeLabel = remember(task.startTime, task.endTime) {
        formatTaskTimeRange(task.startTime, task.endTime)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenTask(task) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PriorityCheckbox(
            checked = task.isDone,
            priority = task.priority,
            onCheckedChange = { onToggleTask(task) },
            modifier = Modifier.testTag("calendar-task-toggle-${task.id}"),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.repeat != com.taskmanager.android.model.TaskRepeat.NONE || listName != null) {
                Text(
                    text = buildString {
                        if (listName != null) {
                            append(listName)
                        }
                        if (task.repeat != com.taskmanager.android.model.TaskRepeat.NONE) {
                            if (isNotEmpty()) append(" • ")
                            append(getTaskRepeatSummary(task.repeat, task.repeatConfig))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (timeLabel != null) {
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                contentColor = accentColor,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = timeLabel,
                    modifier = Modifier
                        .widthIn(min = 76.dp, max = 132.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
