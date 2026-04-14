package com.taskmanager.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskSectionId

data class TaskBadgeColors(
    val container: Color,
    val border: Color,
    val content: Color,
)

private object TaskPalette {
    val high = Color(0xFFF43F5E)
    val medium = Color(0xFFF59E0B)
    val low = Color(0xFF0EA5E9)
    val none = Color(0xFF71717A)
    val pinned = Color(0xFFF59E0B)
}

fun taskPriorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.URGENT_IMPORTANT -> TaskPalette.high
    TaskPriority.NOT_URGENT_IMPORTANT -> TaskPalette.medium
    TaskPriority.URGENT_UNIMPORTANT -> TaskPalette.low
    TaskPriority.NOT_URGENT_UNIMPORTANT -> TaskPalette.none
}

fun sectionPriorityColor(sectionId: TaskSectionId): Color = when (sectionId) {
    TaskSectionId.PINNED -> TaskPalette.pinned
    TaskSectionId.URGENT_IMPORTANT -> taskPriorityColor(TaskPriority.URGENT_IMPORTANT)
    TaskSectionId.NOT_URGENT_IMPORTANT -> taskPriorityColor(TaskPriority.NOT_URGENT_IMPORTANT)
    TaskSectionId.URGENT_UNIMPORTANT -> taskPriorityColor(TaskPriority.URGENT_UNIMPORTANT)
    TaskSectionId.NOT_URGENT_UNIMPORTANT -> taskPriorityColor(TaskPriority.NOT_URGENT_UNIMPORTANT)
}

fun taskPriorityBadgeColors(priority: TaskPriority): TaskBadgeColors = badgeColors(taskPriorityColor(priority))

fun neutralBadgeColors(): TaskBadgeColors = TaskBadgeColors(
    container = Color(0x1A71717A),
    border = Color(0x3371717A),
    content = TaskPalette.none,
)

fun listBadgeColors(rawColor: String?): TaskBadgeColors = badgeColors(parseHexColor(rawColor) ?: TaskPalette.none)

private fun badgeColors(accent: Color): TaskBadgeColors = TaskBadgeColors(
    container = accent.copy(alpha = 0.10f),
    border = accent.copy(alpha = 0.33f),
    content = accent,
)

fun parseHexColor(rawColor: String?): Color? {
    if (rawColor.isNullOrBlank()) return null
    val normalized = rawColor.removePrefix("#")
    val colorLong = normalized.toLongOrNull(16) ?: return null
    return when (normalized.length) {
        6 -> Color(colorLong or 0xFF000000)
        8 -> Color(colorLong)
        else -> null
    }
}

@Composable
fun PriorityCheckbox(
    checked: Boolean,
    priority: TaskPriority,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = taskPriorityColor(priority)
    Box(
        modifier = modifier
            .size(24.dp)
            .border(width = 2.dp, color = accent, shape = RoundedCornerShape(8.dp))
            .background(
                color = if (checked) accent else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onCheckedChange() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
