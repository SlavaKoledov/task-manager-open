package com.taskmanager.android.domain

import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.data.api.ApiTaskCreateSubtaskPayload
import com.taskmanager.android.data.api.toApi
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.DescriptionBlock
import com.taskmanager.android.model.EditableSubtaskDraft
import com.taskmanager.android.model.TaskDraft
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.model.TaskViewTarget
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

fun buildTaskDraft(
    context: TaskEditorContext,
    todayString: String,
    tomorrowString: String,
): TaskDraft {
    val dueDate = context.prefilledDueDate ?: when (context.viewTarget.mode) {
        com.taskmanager.android.model.ViewMode.TODAY -> todayString
        com.taskmanager.android.model.ViewMode.TOMORROW -> tomorrowString
        com.taskmanager.android.model.ViewMode.INBOX -> ""
        com.taskmanager.android.model.ViewMode.LIST -> ""
        com.taskmanager.android.model.ViewMode.CALENDAR -> ""
        com.taskmanager.android.model.ViewMode.ALL -> defaultDueDateForAllGroup(context.groupId, todayString)
    }

    val listId = when (val target = context.viewTarget) {
        TaskViewTarget.Inbox -> null
        is TaskViewTarget.ListView -> target.listId
        TaskViewTarget.Calendar -> null
        else -> null
    }

    val priority = when (context.sectionId) {
        null,
        TaskSectionId.PINNED,
        -> com.taskmanager.android.model.TaskPriority.NOT_URGENT_UNIMPORTANT
        else -> context.sectionId.toPriorityOrNull() ?: com.taskmanager.android.model.TaskPriority.NOT_URGENT_UNIMPORTANT
    }

    return TaskDraft(
        title = "",
        description = "",
        dueDate = dueDate,
        reminderTime = "",
        repeatUntil = "",
        isDone = false,
        isPinned = false,
        priority = priority,
        repeat = TaskRepeat.NONE,
        listId = listId,
    )
}

fun buildTaskDraft(task: TaskItem): TaskDraft = TaskDraft(
    title = task.title,
    description = descriptionBlocksToText(ensureDescriptionBlocks(task.descriptionBlocks, task.description)).orEmpty(),
    dueDate = task.dueDate.orEmpty(),
    reminderTime = task.reminderTime.orEmpty(),
    repeatUntil = task.repeatUntil.orEmpty(),
    isDone = task.isDone,
    isPinned = task.isPinned,
    priority = task.priority,
    repeat = task.repeat,
    listId = task.listId,
)

private fun defaultDueDateForAllGroup(groupId: AllTaskGroupId?, todayString: String): String = when (groupId) {
    AllTaskGroupId.OVERDUE -> getOffsetLocalDateString(-1, parseLocalDateString(todayString) ?: java.time.LocalDate.now())
    AllTaskGroupId.TODAY -> todayString
    AllTaskGroupId.TOMORROW -> getTomorrowDateString(parseLocalDateString(todayString) ?: java.time.LocalDate.now())
    AllTaskGroupId.NEXT_7_DAYS -> getOffsetLocalDateString(2, parseLocalDateString(todayString) ?: java.time.LocalDate.now())
    AllTaskGroupId.LATER -> getOffsetLocalDateString(8, parseLocalDateString(todayString) ?: java.time.LocalDate.now())
    AllTaskGroupId.NO_DATE,
    null,
    -> ""
}

fun validateTaskDraft(draft: TaskDraft): String? {
    if (draft.title.isBlank()) {
        return "Task title is required."
    }

    if (draft.repeatUntil.isNotBlank()) {
        if (draft.repeat == TaskRepeat.NONE) {
            return "Choose a repeat schedule before setting a repeat end date."
        }

        if (draft.dueDate.isBlank()) {
            return "Choose a due date before setting a repeat end date."
        }

        val dueDate = parseLocalDateString(draft.dueDate)
        val repeatUntil = parseLocalDateString(draft.repeatUntil)
        if (dueDate != null && repeatUntil != null && repeatUntil.isBefore(dueDate)) {
            return "Repeat end date cannot be earlier than the due date."
        }
    }

    if (draft.dueDate.isBlank() && draft.repeat != TaskRepeat.NONE) {
        return "Choose a due date before setting a repeat schedule."
    }

    if (draft.reminderTime.isNotBlank() && draft.dueDate.isBlank()) {
        return "Choose a due date before setting a reminder."
    }

    return null
}

fun buildTaskCreatePayloadFromDraft(
    draft: TaskDraft,
    subtasks: List<EditableSubtaskDraft> = emptyList(),
): ApiTaskCreatePayload {
    val normalizedBlocks = stripDescriptionBlocks(descriptionTextToBlocks(draft.description))
    return ApiTaskCreatePayload(
        title = draft.title.trim(),
        description = descriptionBlocksToText(normalizedBlocks),
        descriptionBlocks = normalizedBlocks.map(DescriptionBlock::toApi),
        dueDate = draft.dueDate.takeIf { it.isNotBlank() },
        reminderTime = draft.reminderTime.takeIf { draft.dueDate.isNotBlank() && it.isNotBlank() },
        repeatUntil = draft.repeatUntil.takeIf { draft.repeat != TaskRepeat.NONE && it.isNotBlank() },
        isDone = draft.isDone,
        isPinned = draft.isPinned,
        priority = draft.priority.wire,
        repeat = draft.repeat.wire,
        listId = draft.listId,
        subtasks = subtasks
            .map { subtask ->
                ApiTaskCreateSubtaskPayload(
                    title = subtask.title.trim(),
                    description = null,
                    descriptionBlocks = emptyList(),
                    dueDate = null,
                    reminderTime = null,
                    isDone = subtask.isDone,
                )
            }
            .filter { it.title.isNotBlank() },
    )
}

fun buildTaskUpdatePayloadJson(
    draft: TaskDraft,
): JsonObject {
    val normalizedBlocks = stripDescriptionBlocks(descriptionTextToBlocks(draft.description))
    return buildJsonObject {
        put("title", JsonPrimitive(draft.title.trim()))
        put("description", descriptionBlocksToText(normalizedBlocks)?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "description_blocks",
            JsonArray(
                normalizedBlocks.map { block ->
                    when (block) {
                        is DescriptionBlock.Text -> buildJsonObject {
                            put("kind", JsonPrimitive("text"))
                            put("text", JsonPrimitive(block.text))
                        }
                        is DescriptionBlock.Checkbox -> buildJsonObject {
                            put("kind", JsonPrimitive("checkbox"))
                            put("text", JsonPrimitive(block.text))
                            put("checked", JsonPrimitive(block.checked))
                        }
                    }
                },
            ),
        )
        put("due_date", draft.dueDate.takeIf { it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull)
        put(
            "reminder_time",
            draft.reminderTime.takeIf { draft.dueDate.isNotBlank() && it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull,
        )
        put(
            "repeat_until",
            draft.repeatUntil.takeIf { draft.repeat != TaskRepeat.NONE && it.isNotBlank() }?.let(::JsonPrimitive) ?: JsonNull,
        )
        put("is_done", JsonPrimitive(draft.isDone))
        put("is_pinned", JsonPrimitive(draft.isPinned))
        put("priority", JsonPrimitive(draft.priority.wire))
        put("repeat", JsonPrimitive(draft.repeat.wire))
        put("list_id", draft.listId?.let(::JsonPrimitive) ?: JsonNull)
    }
}

fun updateDraftDate(draft: TaskDraft, nextDate: String): TaskDraft = draft.copy(
    dueDate = nextDate,
    reminderTime = if (nextDate.isBlank()) "" else draft.reminderTime,
    repeatUntil = if (nextDate.isBlank() || (draft.repeatUntil.isNotBlank() && draft.repeatUntil < nextDate)) "" else draft.repeatUntil,
    repeat = if (nextDate.isNotBlank()) draft.repeat else TaskRepeat.NONE,
)

fun updateDraftRepeat(draft: TaskDraft, repeat: TaskRepeat): TaskDraft = draft.copy(
    repeat = repeat,
    repeatUntil = if (repeat == TaskRepeat.NONE) "" else draft.repeatUntil,
)
