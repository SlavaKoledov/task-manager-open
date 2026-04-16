package com.taskmanager.android.data.local

import com.taskmanager.android.data.api.ApiDescriptionBlock
import com.taskmanager.android.data.api.ApiListItem
import com.taskmanager.android.data.api.ApiTask
import com.taskmanager.android.data.api.toDomain
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskSubtask
import com.taskmanager.android.data.sync.StoredTaskCreatePayload
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class LocalCacheMapper @Inject constructor(
    private val json: Json,
) {
    private val descriptionBlockListSerializer = ListSerializer(ApiDescriptionBlock.serializer())
    private val customRepeatSerializer = TaskCustomRepeatConfig.serializer()

    fun toListEntities(baseUrl: String, items: List<ApiListItem>): List<CachedListEntity> =
        items.map { item ->
            CachedListEntity(
                baseUrl = baseUrl,
                id = item.id,
                name = item.name,
                color = item.color,
                position = item.position,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
            )
        }

    fun toListItems(entities: List<CachedListEntity>): List<ListItem> = entities.map { entity ->
        ListItem(
            id = entity.id,
            name = entity.name,
            color = entity.color,
            position = entity.position,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    fun toTaskEntities(baseUrl: String, tasks: List<ApiTask>): List<CachedTaskEntity> = buildList {
        tasks.forEach { task -> addAll(flattenTask(baseUrl, task)) }
    }

    fun toTaskEntities(
        baseUrl: String,
        payload: StoredTaskCreatePayload,
        createdAt: String,
        position: Int,
    ): List<CachedTaskEntity> = buildList {
        add(
            CachedTaskEntity(
                baseUrl = baseUrl,
                id = payload.localTaskId,
                parentId = payload.parentId,
                title = payload.title,
                description = payload.description,
                descriptionBlocksJson = encodeDescriptionBlocks(payload.descriptionBlocks),
                dueDate = payload.dueDate,
                startTime = payload.startTime,
                endTime = payload.endTime,
                reminderTime = payload.reminderTime,
                repeatConfigJson = encodeRepeatConfig(payload.repeatConfig),
                repeatUntil = payload.repeatUntil,
                isDone = payload.isDone,
                isPinned = payload.isPinned,
                priority = payload.priority,
                repeat = payload.repeat,
                position = position,
                listId = payload.listId,
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )

        payload.subtasks.forEachIndexed { index, subtask ->
            add(
                CachedTaskEntity(
                    baseUrl = baseUrl,
                    id = subtask.localId,
                    parentId = payload.localTaskId,
                    title = subtask.title,
                    description = subtask.description,
                    descriptionBlocksJson = encodeDescriptionBlocks(subtask.descriptionBlocks),
                    dueDate = subtask.dueDate,
                    startTime = subtask.startTime,
                    endTime = subtask.endTime,
                    reminderTime = subtask.reminderTime,
                    repeatConfigJson = null,
                    repeatUntil = null,
                    isDone = subtask.isDone,
                    isPinned = false,
                    priority = payload.priority,
                    repeat = TaskRepeat.NONE.wire,
                    position = index,
                    listId = payload.listId,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )
        }
    }

    fun toTaskItems(entities: List<CachedTaskEntity>): List<TaskItem> {
        val activeEntities = entities.filter { it.deletedAt == null }
        val subtasksByParentId = activeEntities
            .filter { it.parentId != null }
            .groupBy { requireNotNull(it.parentId) }

        return activeEntities
            .filter { it.parentId == null }
            .sortedWith(compareBy<CachedTaskEntity>({ it.position }, { it.createdAt }, { it.id }))
            .map { entity ->
                TaskItem(
                    id = entity.id,
                    title = entity.title,
                    description = entity.description,
                    descriptionBlocks = decodeDescriptionBlocks(entity.descriptionBlocksJson).map(ApiDescriptionBlock::toDomain),
                    dueDate = entity.dueDate,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    reminderTime = entity.reminderTime,
                    repeatConfig = decodeRepeatConfig(entity.repeatConfigJson),
                    repeatUntil = entity.repeatUntil,
                    isDone = entity.isDone,
                    isPinned = entity.isPinned,
                    priority = TaskPriority.fromWire(entity.priority),
                    repeat = TaskRepeat.fromWire(entity.repeat),
                    parentId = entity.parentId,
                    position = entity.position,
                    listId = entity.listId,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    subtasks = subtasksByParentId[entity.id]
                        .orEmpty()
                        .sortedWith(compareBy<CachedTaskEntity>({ it.position }, { it.createdAt }, { it.id }))
                        .map { subtask ->
                            TaskSubtask(
                                id = subtask.id,
                                title = subtask.title,
                                description = subtask.description,
                                descriptionBlocks = decodeDescriptionBlocks(subtask.descriptionBlocksJson).map(ApiDescriptionBlock::toDomain),
                                dueDate = subtask.dueDate,
                                startTime = subtask.startTime,
                                endTime = subtask.endTime,
                                reminderTime = subtask.reminderTime,
                                repeatConfig = decodeRepeatConfig(subtask.repeatConfigJson),
                                repeatUntil = subtask.repeatUntil,
                                isDone = subtask.isDone,
                                isPinned = subtask.isPinned,
                                priority = TaskPriority.fromWire(subtask.priority),
                                repeat = TaskRepeat.fromWire(subtask.repeat),
                                parentId = subtask.parentId,
                                position = subtask.position,
                                listId = subtask.listId,
                                createdAt = subtask.createdAt,
                                updatedAt = subtask.updatedAt,
                            )
                        },
                )
            }
    }

    fun encodeDescriptionBlocks(blocks: List<ApiDescriptionBlock>): String =
        json.encodeToString(descriptionBlockListSerializer, blocks)

    fun decodeDescriptionBlocks(rawJson: String): List<ApiDescriptionBlock> =
        json.decodeFromString(descriptionBlockListSerializer, rawJson)

    fun encodeRepeatConfig(config: TaskCustomRepeatConfig?): String? =
        config?.let { json.encodeToString(customRepeatSerializer, it) }

    fun decodeRepeatConfig(rawJson: String?): TaskCustomRepeatConfig? =
        rawJson?.let { json.decodeFromString(customRepeatSerializer, it) }

    private fun flattenTask(baseUrl: String, task: ApiTask): List<CachedTaskEntity> = buildList {
        add(
            CachedTaskEntity(
                baseUrl = baseUrl,
                id = task.id,
                parentId = task.parentId,
                title = task.title,
                description = task.description,
                descriptionBlocksJson = encodeDescriptionBlocks(task.descriptionBlocks),
                dueDate = task.dueDate,
                startTime = task.startTime,
                endTime = task.endTime,
                reminderTime = task.reminderTime,
                repeatConfigJson = encodeRepeatConfig(task.repeatConfig?.toDomain()),
                repeatUntil = task.repeatUntil,
                isDone = task.isDone,
                isPinned = task.isPinned,
                priority = task.priority,
                repeat = task.repeat,
                position = task.position,
                listId = task.listId,
                createdAt = task.createdAt,
                updatedAt = task.updatedAt,
            ),
        )
        task.subtasks.forEach { subtask ->
            addAll(flattenTask(baseUrl, subtask))
        }
    }
}
