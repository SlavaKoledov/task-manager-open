package com.taskmanager.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taskmanager.android.domain.formatDueDateLabel
import com.taskmanager.android.domain.getSubtaskProgressSummary
import com.taskmanager.android.domain.getTaskRepeatSummary
import com.taskmanager.android.domain.hasMeaningfulDescription
import com.taskmanager.android.domain.isTaskOverdue
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskSubtask

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: TaskItem,
    list: ListItem?,
    todayString: String,
    tomorrowString: String,
    isSubtasksCollapsed: Boolean,
    isExpandedSubtaskPreview: Boolean,
    onToggleTask: (TaskItem) -> Unit,
    onToggleSubtask: (TaskSubtask) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onRequestDeleteTask: (TaskItem) -> Unit,
    onToggleSubtasks: (Int) -> Unit,
    onToggleExpandedSubtaskPreview: (Int) -> Unit,
    onStartMoveTask: ((TaskItem) -> Unit)? = null,
    onStartMoveSubtask: ((TaskSubtask) -> Unit)? = null,
    isMoveSource: Boolean = false,
    canDropInside: Boolean = false,
    onDropInside: (() -> Unit)? = null,
    onDropAfterSubtask: ((TaskSubtask) -> Unit)? = null,
) {
    var menuExpanded by remember(task.id) { mutableStateOf(false) }
    val dueLabel = remember(task.dueDate, todayString, tomorrowString) {
        formatDueDateLabel(task.dueDate, todayString, tomorrowString)
    }
    val subtaskProgress = remember(task.subtasks) {
        getSubtaskProgressSummary(
            done = task.subtasks.count(TaskSubtask::isDone),
            total = task.subtasks.size,
        )
    }
    val visibleSubtasks = remember(task.subtasks, isExpandedSubtaskPreview) {
        if (isExpandedSubtaskPreview) task.subtasks else task.subtasks.take(4)
    }
    val accentColor = taskPriorityColor(task.priority)
    val priorityBadgeColors = remember(task.priority) { taskPriorityBadgeColors(task.priority) }
    val listBadgeColors = remember(list?.color) {
        if (list != null) listBadgeColors(list.color) else neutralBadgeColors()
    }
    val errorColor = MaterialTheme.colorScheme.error
    val dueBadgeColors = remember(todayString, task.dueDate, task.isDone, errorColor) {
        if (isTaskOverdue(task, todayString)) {
            TaskBadgeColors(
                container = errorColor.copy(alpha = 0.10f),
                border = errorColor.copy(alpha = 0.33f),
                content = errorColor,
            )
        } else {
            neutralBadgeColors()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEditTask(task) },
                onLongClick = {
                    if (!task.isDone) {
                        onStartMoveTask?.invoke(task)
                    }
                },
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (isMoveSource) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                PriorityCheckbox(
                    checked = task.isDone,
                    priority = task.priority,
                    onCheckedChange = { onToggleTask(task) },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (hasMeaningfulDescription(task.descriptionBlocks, task.description)) {
                            DescriptionIndicator(modifier = Modifier.padding(start = 8.dp))
                        }
                        if (task.isPinned) {
                            Icon(
                                imageVector = Icons.Outlined.PushPin,
                                contentDescription = null,
                                tint = sectionPriorityColor(com.taskmanager.android.model.TaskSectionId.PINNED),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(18.dp),
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TaskBadge(
                            label = dueLabel ?: "No date",
                            colors = dueBadgeColors,
                        )
                        TaskBadge(label = list?.name ?: "Inbox", colors = listBadgeColors)
                        TaskBadge(label = task.priority.title, colors = priorityBadgeColors)
                        if (task.repeat != com.taskmanager.android.model.TaskRepeat.NONE) {
                            TaskBadge(
                                label = getTaskRepeatSummary(task.repeat, task.repeatConfig),
                                colors = neutralBadgeColors(),
                                icon = { Icon(Icons.Outlined.Repeat, contentDescription = null, modifier = Modifier.size(12.dp)) },
                            )
                        }
                        if (subtaskProgress.percent != null) {
                            TaskBadge(label = "${subtaskProgress.percent}%", colors = neutralBadgeColors())
                        }
                    }
                }

                Box {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Task actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { menuExpanded = true },
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                menuExpanded = false
                                onEditTask(task)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (isSubtasksCollapsed) "Show subtasks" else "Hide subtasks") },
                            onClick = {
                                menuExpanded = false
                                onToggleSubtasks(task.id)
                            },
                        )
                        if (!task.isDone && onStartMoveTask != null) {
                            DropdownMenuItem(
                                text = { Text("Move") },
                                onClick = {
                                    menuExpanded = false
                                    onStartMoveTask(task)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete task") },
                            onClick = {
                                menuExpanded = false
                                onRequestDeleteTask(task)
                            },
                        )
                    }
                }
            }

            if (canDropInside && onDropInside != null) {
                MoveTargetButton(
                    label = "Drop into ${task.title}",
                    modifier = Modifier.padding(top = 12.dp),
                    onClick = onDropInside,
                )
            }

            if (task.subtasks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSubtasks(task.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isSubtasksCollapsed) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${task.subtasks.size} subtasks",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (subtaskProgress.percent != null) {
                            Text(
                                text = "${subtaskProgress.percent}%",
                                color = accentColor,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    if (!isSubtasksCollapsed) {
                        visibleSubtasks.forEach { subtask ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .background(
                                        color = if (onDropAfterSubtask != null && subtask.id != task.id) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .combinedClickable(
                                        onClick = {
                                            if (onDropAfterSubtask != null) {
                                                onDropAfterSubtask(subtask)
                                            }
                                        },
                                        onLongClick = {
                                            if (!subtask.isDone) {
                                                onStartMoveSubtask?.invoke(subtask)
                                            }
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PriorityCheckbox(
                                    checked = subtask.isDone,
                                    priority = subtask.priority,
                                    onCheckedChange = { onToggleSubtask(subtask) },
                                )
                                SubtaskPreviewTitle(
                                    title = subtask.title,
                                    hasDescription = hasMeaningfulDescription(subtask.descriptionBlocks, subtask.description),
                                    color = if (subtask.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (subtask.isDone) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f),
                                )
                                if (onDropAfterSubtask != null) {
                                    Text(
                                        text = "Drop",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accentColor,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }

                        if (task.subtasks.size > 4) {
                            Text(
                                text = if (isExpandedSubtaskPreview) "Show less" else "+${task.subtasks.size - 4} more",
                                style = MaterialTheme.typography.bodyMedium,
                                color = accentColor,
                                modifier = Modifier
                                    .padding(start = 12.dp, top = 8.dp)
                                    .clickable { onToggleExpandedSubtaskPreview(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveTargetButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TaskBadge(
    label: String,
    colors: TaskBadgeColors,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.container,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            icon?.invoke()
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.content,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DescriptionIndicator(
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = Icons.Outlined.Subject,
        contentDescription = "Has description",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(16.dp),
    )
}

@Composable
private fun SubtaskPreviewTitle(
    title: String,
    hasDescription: Boolean,
    color: Color,
    textDecoration: TextDecoration,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val showOverflowCue = title.length > 30 && scrollState.value == 0

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                textDecoration = textDecoration,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .padding(end = if (showOverflowCue) 18.dp else 0.dp),
            )
            if (showOverflowCue) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)),
                            ),
                        )
                        .padding(start = 8.dp),
                ) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (hasDescription) {
            DescriptionIndicator()
        }
    }
}
