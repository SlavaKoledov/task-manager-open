package com.taskmanager.android.data.sync

import com.taskmanager.android.data.api.ApiDescriptionBlock
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.data.api.ApiTaskCreateSubtaskPayload
import com.taskmanager.android.data.api.ApiTaskUpdatePayload
import com.taskmanager.android.data.api.toApi
import com.taskmanager.android.model.NewTaskPlacement
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskTopLevelReorderScope
import com.taskmanager.android.model.ViewMode
import kotlinx.serialization.Serializable

@Serializable
data class StoredTaskCreatePayload(
    val clientRequestId: String,
    val localTaskId: Int,
    val title: String,
    val description: String? = null,
    val descriptionBlocks: List<ApiDescriptionBlock> = emptyList(),
    val dueDate: String? = null,
    val reminderTime: String? = null,
    val repeatConfig: TaskCustomRepeatConfig? = null,
    val repeatUntil: String? = null,
    val isDone: Boolean = false,
    val isPinned: Boolean = false,
    val priority: String,
    val repeat: String,
    val parentId: Int? = null,
    val listId: Int? = null,
    val subtasks: List<StoredTaskCreateSubtaskPayload> = emptyList(),
    val reorderScope: StoredTaskReorderScope? = null,
    val placement: String? = null,
) {
    fun toApiPayload(): ApiTaskCreatePayload = ApiTaskCreatePayload(
        title = title,
        description = description,
        descriptionBlocks = descriptionBlocks,
        dueDate = dueDate,
        reminderTime = reminderTime,
        repeatConfig = repeatConfig?.toApi(),
        repeatUntil = repeatUntil,
        isDone = isDone,
        isPinned = isPinned,
        priority = priority,
        repeat = repeat,
        parentId = parentId,
        listId = listId,
        subtasks = subtasks.map { subtask ->
            ApiTaskCreateSubtaskPayload(
                title = subtask.title,
                description = subtask.description,
                descriptionBlocks = subtask.descriptionBlocks,
                dueDate = subtask.dueDate,
                isDone = subtask.isDone,
            )
        },
        clientRequestId = clientRequestId,
    )

    fun placementOrNull(): NewTaskPlacement? = placement?.let(NewTaskPlacement::fromWire)
}

@Serializable
data class StoredTaskCreateSubtaskPayload(
    val localId: Int,
    val title: String,
    val description: String? = null,
    val descriptionBlocks: List<ApiDescriptionBlock> = emptyList(),
    val dueDate: String? = null,
    val reminderTime: String? = null,
    val isDone: Boolean = false,
)

@Serializable
data class StoredTaskCompletionPayload(
    val desiredIsDone: Boolean,
)

@Serializable
data class StoredSubtaskReorderPayload(
    val orderedSubtaskIds: List<Int> = emptyList(),
)

@Serializable
data class StoredTaskDeletePayload(
    val deletedAt: String? = null,
)

@Serializable
data class StoredTaskUpdatePayload(
    val title: String? = null,
    val hasTitle: Boolean = false,
    val description: String? = null,
    val hasDescription: Boolean = false,
    val descriptionBlocks: List<ApiDescriptionBlock>? = null,
    val hasDescriptionBlocks: Boolean = false,
    val dueDate: String? = null,
    val hasDueDate: Boolean = false,
    val reminderTime: String? = null,
    val hasReminderTime: Boolean = false,
    val repeatConfig: TaskCustomRepeatConfig? = null,
    val hasRepeatConfig: Boolean = false,
    val repeatUntil: String? = null,
    val hasRepeatUntil: Boolean = false,
    val isPinned: Boolean? = null,
    val hasIsPinned: Boolean = false,
    val priority: String? = null,
    val hasPriority: Boolean = false,
    val repeat: String? = null,
    val hasRepeat: Boolean = false,
    val listId: Int? = null,
    val hasListId: Boolean = false,
) {
    fun toApiPayload(): ApiTaskUpdatePayload = ApiTaskUpdatePayload(
        title = title.takeIf { hasTitle },
        description = if (hasDescription) description else null,
        descriptionBlocks = if (hasDescriptionBlocks) descriptionBlocks else null,
        dueDate = if (hasDueDate) dueDate else null,
        reminderTime = if (hasReminderTime) reminderTime else null,
        repeatConfig = if (hasRepeatConfig) repeatConfig?.toApi() else null,
        repeatUntil = if (hasRepeatUntil) repeatUntil else null,
        isPinned = if (hasIsPinned) isPinned else null,
        priority = priority.takeIf { hasPriority },
        repeat = repeat.takeIf { hasRepeat },
        listId = if (hasListId) listId else null,
    )

    fun merge(newer: StoredTaskUpdatePayload): StoredTaskUpdatePayload = copy(
        title = if (newer.hasTitle) newer.title else title,
        hasTitle = hasTitle || newer.hasTitle,
        description = if (newer.hasDescription) newer.description else description,
        hasDescription = hasDescription || newer.hasDescription,
        descriptionBlocks = if (newer.hasDescriptionBlocks) newer.descriptionBlocks else descriptionBlocks,
        hasDescriptionBlocks = hasDescriptionBlocks || newer.hasDescriptionBlocks,
        dueDate = if (newer.hasDueDate) newer.dueDate else dueDate,
        hasDueDate = hasDueDate || newer.hasDueDate,
        reminderTime = if (newer.hasReminderTime) newer.reminderTime else reminderTime,
        hasReminderTime = hasReminderTime || newer.hasReminderTime,
        repeatConfig = if (newer.hasRepeatConfig) newer.repeatConfig else repeatConfig,
        hasRepeatConfig = hasRepeatConfig || newer.hasRepeatConfig,
        repeatUntil = if (newer.hasRepeatUntil) newer.repeatUntil else repeatUntil,
        hasRepeatUntil = hasRepeatUntil || newer.hasRepeatUntil,
        isPinned = if (newer.hasIsPinned) newer.isPinned else isPinned,
        hasIsPinned = hasIsPinned || newer.hasIsPinned,
        priority = if (newer.hasPriority) newer.priority else priority,
        hasPriority = hasPriority || newer.hasPriority,
        repeat = if (newer.hasRepeat) newer.repeat else repeat,
        hasRepeat = hasRepeat || newer.hasRepeat,
        listId = if (newer.hasListId) newer.listId else listId,
        hasListId = hasListId || newer.hasListId,
    )
}

@Serializable
data class StoredTaskReorderScope(
    val view: String,
    val sectionId: String,
    val listId: Int? = null,
    val targetDate: String? = null,
    val groupId: String? = null,
    val referenceDate: String? = null,
) {
    fun toDomain(): TaskTopLevelReorderScope = when (view) {
        "list" -> TaskTopLevelReorderScope.ListScope(
            listId = requireNotNull(listId),
            sectionId = com.taskmanager.android.model.TaskSectionId.fromWire(sectionId),
        )
        "inbox" -> TaskTopLevelReorderScope.InboxScope(
            sectionId = com.taskmanager.android.model.TaskSectionId.fromWire(sectionId),
        )
        "today" -> TaskTopLevelReorderScope.DateScope(
            view = ViewMode.TODAY,
            targetDate = requireNotNull(targetDate),
            sectionId = com.taskmanager.android.model.TaskSectionId.fromWire(sectionId),
        )
        "tomorrow" -> TaskTopLevelReorderScope.DateScope(
            view = ViewMode.TOMORROW,
            targetDate = requireNotNull(targetDate),
            sectionId = com.taskmanager.android.model.TaskSectionId.fromWire(sectionId),
        )
        else -> TaskTopLevelReorderScope.AllScope(
            groupId = com.taskmanager.android.model.AllTaskGroupId.fromWire(requireNotNull(groupId)),
            referenceDate = requireNotNull(referenceDate),
            sectionId = com.taskmanager.android.model.TaskSectionId.fromWire(sectionId),
        )
    }
}

fun TaskTopLevelReorderScope.toStored(): StoredTaskReorderScope = when (this) {
    is TaskTopLevelReorderScope.ListScope -> StoredTaskReorderScope(
        view = "list",
        sectionId = sectionId.wire,
        listId = listId,
    )
    is TaskTopLevelReorderScope.InboxScope -> StoredTaskReorderScope(
        view = "inbox",
        sectionId = sectionId.wire,
    )
    is TaskTopLevelReorderScope.DateScope -> StoredTaskReorderScope(
        view = view.name.lowercase(),
        sectionId = sectionId.wire,
        targetDate = targetDate,
    )
    is TaskTopLevelReorderScope.AllScope -> StoredTaskReorderScope(
        view = "all",
        sectionId = sectionId.wire,
        groupId = groupId.wire,
        referenceDate = referenceDate,
    )
}
