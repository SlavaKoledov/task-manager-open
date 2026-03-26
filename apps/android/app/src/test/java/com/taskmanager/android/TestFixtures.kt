package com.taskmanager.android

import com.taskmanager.android.model.DescriptionBlock
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskSubtask

fun testListItem(
    id: Int,
    name: String,
    color: String = "#2563EB",
    position: Int = 0,
): ListItem = ListItem(
    id = id,
    name = name,
    color = color,
    position = position,
    createdAt = "2026-03-15T10:00:00Z",
    updatedAt = "2026-03-15T10:00:00Z",
)

fun testTaskSubtask(
    id: Int,
    title: String,
    dueDate: String? = null,
    reminderTime: String? = null,
    isDone: Boolean = false,
    repeatUntil: String? = null,
    repeat: TaskRepeat = TaskRepeat.NONE,
    position: Int = 0,
    descriptionBlocks: List<DescriptionBlock> = listOf(DescriptionBlock.Text("")),
): TaskSubtask = TaskSubtask(
    id = id,
    title = title,
    description = null,
    descriptionBlocks = descriptionBlocks,
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatUntil = repeatUntil,
    isDone = isDone,
    isPinned = false,
    priority = TaskPriority.NOT_URGENT_UNIMPORTANT,
    repeat = repeat,
    parentId = 100,
    position = position,
    listId = null,
    createdAt = "2026-03-15T10:00:00Z",
    updatedAt = "2026-03-15T10:00:00Z",
)

fun testTaskItem(
    id: Int,
    title: String,
    dueDate: String? = null,
    reminderTime: String? = null,
    isDone: Boolean = false,
    isPinned: Boolean = false,
    priority: TaskPriority = TaskPriority.NOT_URGENT_UNIMPORTANT,
    repeat: TaskRepeat = TaskRepeat.NONE,
    repeatUntil: String? = null,
    parentId: Int? = null,
    position: Int = 0,
    listId: Int? = null,
    subtasks: List<TaskSubtask> = emptyList(),
    descriptionBlocks: List<DescriptionBlock> = listOf(DescriptionBlock.Text("")),
): TaskItem = TaskItem(
    id = id,
    title = title,
    description = null,
    descriptionBlocks = descriptionBlocks,
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatUntil = repeatUntil,
    isDone = isDone,
    isPinned = isPinned,
    priority = priority,
    repeat = repeat,
    parentId = parentId,
    position = position,
    listId = listId,
    createdAt = "2026-03-15T10:00:00Z",
    updatedAt = "2026-03-15T10:00:00Z",
    subtasks = subtasks,
)
