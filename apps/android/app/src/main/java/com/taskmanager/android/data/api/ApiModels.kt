package com.taskmanager.android.data.api

import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.DescriptionBlock
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskCustomRepeatUnit
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskInsertDirection
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.model.TaskSubtask
import com.taskmanager.android.model.TaskTopLevelReorderScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiListItem(
    val id: Int,
    val name: String,
    val color: String,
    val position: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
sealed interface ApiDescriptionBlock {
    val text: String

    @Serializable
    @SerialName("text")
    data class Text(
        override val text: String = "",
    ) : ApiDescriptionBlock

    @Serializable
    @SerialName("checkbox")
    data class Checkbox(
        override val text: String = "",
        val checked: Boolean = false,
    ) : ApiDescriptionBlock
}

@Serializable
data class ApiTaskCustomRepeatConfig(
    val interval: Int = 1,
    val unit: String,
    @SerialName("skip_weekends") val skipWeekends: Boolean = false,
    val weekdays: List<Int> = emptyList(),
    @SerialName("month_day") val monthDay: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class ApiTask(
    val id: Int,
    val title: String,
    val description: String? = null,
    @SerialName("description_blocks") val descriptionBlocks: List<ApiDescriptionBlock> = emptyList(),
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("repeat_config") val repeatConfig: ApiTaskCustomRepeatConfig? = null,
    @SerialName("repeat_until") val repeatUntil: String? = null,
    @SerialName("is_done") val isDone: Boolean,
    @SerialName("is_pinned") val isPinned: Boolean,
    val priority: String,
    val repeat: String,
    @SerialName("parent_id") val parentId: Int? = null,
    val position: Int,
    @SerialName("list_id") val listId: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val subtasks: List<ApiTask> = emptyList(),
)

@Serializable
data class ApiListPayload(
    val name: String,
    val color: String,
)

@Serializable
data class ApiTaskCreateSubtaskPayload(
    val title: String,
    val description: String? = null,
    @SerialName("description_blocks") val descriptionBlocks: List<ApiDescriptionBlock> = emptyList(),
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("is_done") val isDone: Boolean = false,
)

@Serializable
data class ApiTaskCreatePayload(
    val title: String,
    val description: String? = null,
    @SerialName("description_blocks") val descriptionBlocks: List<ApiDescriptionBlock> = emptyList(),
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("repeat_config") val repeatConfig: ApiTaskCustomRepeatConfig? = null,
    @SerialName("repeat_until") val repeatUntil: String? = null,
    @SerialName("is_done") val isDone: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    val priority: String,
    val repeat: String,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("list_id") val listId: Int? = null,
    val subtasks: List<ApiTaskCreateSubtaskPayload> = emptyList(),
    @SerialName("client_request_id") val clientRequestId: String? = null,
)

@Serializable
data class ApiTaskUpdatePayload(
    val title: String? = null,
    val description: String? = null,
    @SerialName("description_blocks") val descriptionBlocks: List<ApiDescriptionBlock>? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("repeat_config") val repeatConfig: ApiTaskCustomRepeatConfig? = null,
    @SerialName("repeat_until") val repeatUntil: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean? = null,
    val priority: String? = null,
    val repeat: String? = null,
    @SerialName("list_id") val listId: Int? = null,
)

@Serializable
data class ApiListReorderPayload(
    @SerialName("list_ids") val listIds: List<Int>,
)

@Serializable
data class ApiSubtaskReorderPayload(
    @SerialName("subtask_ids") val subtaskIds: List<Int>,
)

@Serializable
data class ApiTopLevelTaskReorderPayload(
    @SerialName("task_ids") val taskIds: List<Int>,
    val scope: ApiTopLevelTaskReorderScope,
)

@Serializable
data class ApiTopLevelTaskReorderScope(
    val view: String,
    @SerialName("section_id") val sectionId: String,
    @SerialName("list_id") val listId: Int? = null,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("reference_date") val referenceDate: String? = null,
)

@Serializable
data class ApiLiveEvent(
    val version: Int,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_ids") val entityIds: List<Int>,
    @SerialName("changed_at") val changedAt: String,
)

@Serializable
data class ApiTaskMovePayload(
    @SerialName("destination_parent_id") val destinationParentId: Int? = null,
    @SerialName("destination_scope") val destinationScope: ApiTopLevelTaskReorderScope? = null,
    @SerialName("ordered_ids") val orderedIds: List<Int>,
)

@Serializable
data class ApiTaskMoveResult(
    val task: ApiTask,
    @SerialName("affected_tasks") val affectedTasks: List<ApiTask> = emptyList(),
    @SerialName("removed_top_level_task_ids") val removedTopLevelTaskIds: List<Int> = emptyList(),
)

fun ApiListItem.toDomain(): ListItem = ListItem(
    id = id,
    name = name,
    color = color,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ApiDescriptionBlock.toDomain(): DescriptionBlock = when (this) {
    is ApiDescriptionBlock.Text -> DescriptionBlock.Text(text = text)
    is ApiDescriptionBlock.Checkbox -> DescriptionBlock.Checkbox(text = text, checked = checked)
}

fun DescriptionBlock.toApi(): ApiDescriptionBlock = when (this) {
    is DescriptionBlock.Text -> ApiDescriptionBlock.Text(text = text)
    is DescriptionBlock.Checkbox -> ApiDescriptionBlock.Checkbox(text = text, checked = checked)
}

fun ApiTaskCustomRepeatConfig.toDomain(): TaskCustomRepeatConfig = TaskCustomRepeatConfig(
    interval = interval,
    unit = TaskCustomRepeatUnit.fromWire(unit),
    skipWeekends = skipWeekends,
    weekdays = weekdays,
    monthDay = monthDay,
    month = month,
    day = day,
)

fun TaskCustomRepeatConfig.toApi(): ApiTaskCustomRepeatConfig = ApiTaskCustomRepeatConfig(
    interval = interval,
    unit = unit.wire,
    skipWeekends = skipWeekends,
    weekdays = weekdays,
    monthDay = monthDay,
    month = month,
    day = day,
)

private fun ApiTask.toDomainSubtask(): TaskSubtask = TaskSubtask(
    id = id,
    title = title,
    description = description,
    descriptionBlocks = descriptionBlocks.map(ApiDescriptionBlock::toDomain),
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatConfig = repeatConfig?.toDomain(),
    repeatUntil = repeatUntil,
    isDone = isDone,
    isPinned = isPinned,
    priority = TaskPriority.fromWire(priority),
    repeat = TaskRepeat.fromWire(repeat),
    parentId = parentId,
    position = position,
    listId = listId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ApiTask.toDomain(): TaskItem = TaskItem(
    id = id,
    title = title,
    description = description,
    descriptionBlocks = descriptionBlocks.map(ApiDescriptionBlock::toDomain),
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatConfig = repeatConfig?.toDomain(),
    repeatUntil = repeatUntil,
    isDone = isDone,
    isPinned = isPinned,
    priority = TaskPriority.fromWire(priority),
    repeat = TaskRepeat.fromWire(repeat),
    parentId = parentId,
    position = position,
    listId = listId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    subtasks = subtasks.map(ApiTask::toDomainSubtask),
)

fun TaskTopLevelReorderScope.toApi(): ApiTopLevelTaskReorderScope = when (this) {
    is TaskTopLevelReorderScope.ListScope -> ApiTopLevelTaskReorderScope(
        view = "list",
        listId = listId,
        sectionId = sectionId.wire,
    )
    is TaskTopLevelReorderScope.InboxScope -> ApiTopLevelTaskReorderScope(
        view = "inbox",
        sectionId = sectionId.wire,
    )
    is TaskTopLevelReorderScope.DateScope -> ApiTopLevelTaskReorderScope(
        view = view.name.lowercase(),
        sectionId = sectionId.wire,
        targetDate = targetDate,
    )
    is TaskTopLevelReorderScope.AllScope -> ApiTopLevelTaskReorderScope(
        view = "all",
        sectionId = sectionId.wire,
        groupId = groupId.wire,
        referenceDate = referenceDate,
    )
}
