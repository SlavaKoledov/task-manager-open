package com.taskmanager.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class TaskManagerBottomDestination {
    TASKS,
    CALENDAR,
}

@Composable
fun TaskManagerBottomBar(
    selectedDestination: TaskManagerBottomDestination,
    onTasksClick: () -> Unit,
    onCalendarClick: () -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selectedDestination == TaskManagerBottomDestination.TASKS,
            onClick = onTasksClick,
            icon = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
            label = { Text("Task") },
        )
        NavigationBarItem(
            selected = selectedDestination == TaskManagerBottomDestination.CALENDAR,
            onClick = onCalendarClick,
            icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            label = { Text("Calendar") },
        )
    }
}
