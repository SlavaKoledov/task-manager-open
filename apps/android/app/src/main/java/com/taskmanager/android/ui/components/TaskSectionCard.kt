package com.taskmanager.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taskmanager.android.domain.insertOrderedIdRelative
import com.taskmanager.android.domain.moveOrderedIds
import com.taskmanager.android.model.ActiveTaskMove
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskInsertDirection
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.model.TaskTopLevelReorderScope
import com.taskmanager.android.model.TaskSubtask

@Composable
fun TaskSectionCard(
    title: String,
    sectionId: TaskSectionId,
    tasks: List<TaskItem>,
    collapsed: Boolean,
    listById: Map<Int, ListItem>,
    collapsedTaskIds: Set<Int>,
    expandedSubtaskPreviewIds: Set<Int>,
    activeMoveTask: ActiveTaskMove?,
    onToggleTask: (TaskItem) -> Unit,
    onToggleSubtask: (TaskSubtask) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onRequestDeleteTask: (TaskItem) -> Unit,
    onToggleSubtasks: (Int) -> Unit,
    onToggleExpandedSubtaskPreview: (Int) -> Unit,
    onStartTaskMove: (Int, Int?, String, Boolean) -> Unit,
    onToggleCollapsed: () -> Unit,
    todayString: String,
    tomorrowString: String,
    onCreateTaskInSection: (() -> Unit)? = null,
    reorderScope: TaskTopLevelReorderScope? = null,
    onReorderTasks: (TaskTopLevelReorderScope, List<Int>) -> Unit = { _, _ -> },
    onMoveTaskToParent: (Int, Int, List<Int>) -> Unit = { _, _, _ -> },
    onMoveTaskToScope: (Int, TaskTopLevelReorderScope, List<Int>) -> Unit = { _, _, _ -> },
) {
    if (tasks.isEmpty()) return

    val canMoveIntoSection = activeMoveTask != null && reorderScope != null
    val activeMoveTaskId = activeMoveTask?.taskId
    val sectionTaskIds = remember(tasks) { tasks.map(TaskItem::id) }

    fun submitTopLevelMove(targetId: Int, direction: TaskInsertDirection) {
        val movingTask = activeMoveTask ?: return
        val scope = reorderScope ?: return
        val reorderedIds = if (sectionTaskIds.contains(movingTask.taskId)) {
            moveOrderedIds(sectionTaskIds, movingTask.taskId, targetId, direction)
        } else {
            insertOrderedIdRelative(sectionTaskIds, movingTask.taskId, targetId, direction)
        }

        if (reorderedIds == sectionTaskIds) {
            return
        }

        if (movingTask.parentId == null && sectionTaskIds.contains(movingTask.taskId)) {
            onReorderTasks(scope, reorderedIds)
        } else {
            onMoveTaskToScope(movingTask.taskId, scope, reorderedIds)
        }
    }

    fun submitMoveIntoTask(parentTask: TaskItem) {
        val movingTask = activeMoveTask ?: return
        if (movingTask.taskId == parentTask.id || movingTask.hasSubtasks) {
            return
        }

        val orderedIds = parentTask.subtasks.map(TaskSubtask::id)
            .filterNot { it == movingTask.taskId } + movingTask.taskId
        onMoveTaskToParent(movingTask.taskId, parentTask.id, orderedIds)
    }

    fun submitMoveAfterSubtask(parentTask: TaskItem, targetSubtask: TaskSubtask) {
        val movingTask = activeMoveTask ?: return
        if (movingTask.taskId == targetSubtask.id || movingTask.hasSubtasks) {
            return
        }

        val destinationIds = parentTask.subtasks.map(TaskSubtask::id).filterNot { it == movingTask.taskId }
        val orderedIds = insertOrderedIdRelative(
            ids = destinationIds,
            insertedId = movingTask.taskId,
            targetId = targetSubtask.id,
            direction = TaskInsertDirection.AFTER,
        )
        onMoveTaskToParent(movingTask.taskId, parentTask.id, orderedIds)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val collapseDescription = if (collapsed) {
                "Expand $title group"
            } else {
                "Collapse $title group"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("section-tag-${sectionId.wire}")
                        .clip(RoundedCornerShape(20.dp))
                        .semantics(mergeDescendants = true) {
                            contentDescription = collapseDescription
                        }
                        .clickable(
                            role = Role.Button,
                            onClickLabel = collapseDescription,
                            onClick = onToggleCollapsed,
                        )
                        .padding(top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = sectionPriorityColor(sectionId).copy(alpha = 0.12f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            when (sectionId) {
                                TaskSectionId.PINNED -> Icon(
                                    imageVector = Icons.Outlined.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                )
                                else -> androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(sectionPriorityColor(sectionId), CircleShape),
                                )
                            }
                            Text(
                                text = title.uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                color = sectionPriorityColor(sectionId),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        modifier = Modifier.testTag("section-collapse-${sectionId.wire}"),
                        imageVector = if (collapsed) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = tasks.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("section-count-${sectionId.wire}"),
                )
                if (onCreateTaskInSection != null && sectionId != TaskSectionId.PINNED) {
                    IconButton(
                        modifier = Modifier.testTag("section-create-${sectionId.wire}"),
                        onClick = onCreateTaskInSection,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create task")
                    }
                }
            }

            if (!collapsed) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tasks.forEachIndexed { index, task ->
                        key(task.id) {
                            if (canMoveIntoSection) {
                                SectionMoveTarget(
                                    label = if (index == 0) "Move to the top of $title" else "Move above ${task.title}",
                                    onClick = { submitTopLevelMove(task.id, TaskInsertDirection.BEFORE) },
                                )
                            }
                            TaskCard(
                                task = task,
                                list = task.listId?.let(listById::get),
                                todayString = todayString,
                                tomorrowString = tomorrowString,
                                isSubtasksCollapsed = task.id in collapsedTaskIds,
                                onToggleTask = onToggleTask,
                                onToggleSubtask = onToggleSubtask,
                                onEditTask = onEditTask,
                                onRequestDeleteTask = onRequestDeleteTask,
                                onToggleSubtasks = onToggleSubtasks,
                                isExpandedSubtaskPreview = task.id in expandedSubtaskPreviewIds,
                                onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                                onStartMoveTask = { movingTask ->
                                    onStartTaskMove(
                                        movingTask.id,
                                        movingTask.parentId,
                                        movingTask.title,
                                        movingTask.subtasks.isNotEmpty(),
                                    )
                                },
                                onStartMoveSubtask = { movingSubtask ->
                                    onStartTaskMove(
                                        movingSubtask.id,
                                        task.id,
                                        movingSubtask.title,
                                        false,
                                    )
                                },
                                isMoveSource = activeMoveTaskId == task.id,
                                canDropInside = activeMoveTask != null && activeMoveTaskId != task.id && !activeMoveTask.hasSubtasks && !task.isDone,
                                onDropInside = if (activeMoveTask != null && activeMoveTaskId != task.id && !activeMoveTask.hasSubtasks && !task.isDone) {
                                    { submitMoveIntoTask(task) }
                                } else {
                                    null
                                },
                                onDropAfterSubtask = if (activeMoveTask != null && !activeMoveTask.hasSubtasks && !task.isDone) {
                                    { targetSubtask -> submitMoveAfterSubtask(task, targetSubtask) }
                                } else {
                                    null
                                },
                            )
                            if (canMoveIntoSection) {
                                SectionMoveTarget(
                                    label = if (index == tasks.lastIndex) "Move to the end of $title" else "Move below ${task.title}",
                                    onClick = { submitTopLevelMove(task.id, TaskInsertDirection.AFTER) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionMoveTarget(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
