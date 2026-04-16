package com.taskmanager.android.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.taskmanager.android.data.api.ApiException
import com.taskmanager.android.data.api.ApiListPayload
import com.taskmanager.android.data.api.ApiListReorderPayload
import com.taskmanager.android.data.api.ApiLiveEvent
import com.taskmanager.android.data.api.ApiServiceFactory
import com.taskmanager.android.data.api.ApiSubtaskReorderPayload
import com.taskmanager.android.data.api.ApiTask
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.data.api.ApiTaskMovePayload
import com.taskmanager.android.data.api.ApiTopLevelTaskReorderPayload
import com.taskmanager.android.data.api.ApiTaskUpdatePayload
import com.taskmanager.android.data.api.mapUnexpectedApiFailure
import com.taskmanager.android.data.api.toApi
import com.taskmanager.android.data.api.toDomain
import com.taskmanager.android.data.local.CachedTaskEntity
import com.taskmanager.android.data.local.LocalCacheMapper
import com.taskmanager.android.data.local.PendingSyncOperationEntity
import com.taskmanager.android.data.local.PendingSyncOperationType
import com.taskmanager.android.data.local.SyncStateEntity
import com.taskmanager.android.data.local.TaskManagerDao
import com.taskmanager.android.data.local.TaskManagerDatabase
import com.taskmanager.android.data.notifications.TaskNotificationManager
import com.taskmanager.android.data.preferences.AppPreferencesStore
import com.taskmanager.android.data.sync.StoredTaskCompletionPayload
import com.taskmanager.android.data.sync.StoredTaskCreatePayload
import com.taskmanager.android.data.sync.StoredTaskCreateSubtaskPayload
import com.taskmanager.android.data.sync.StoredTaskDeletePayload
import com.taskmanager.android.data.sync.StoredSubtaskReorderPayload
import com.taskmanager.android.data.sync.StoredTaskUpdatePayload
import com.taskmanager.android.data.sync.TaskSyncRunResult
import com.taskmanager.android.data.sync.TaskSyncScheduler
import com.taskmanager.android.data.sync.TaskSyncStatus
import com.taskmanager.android.data.sync.toStored
import com.taskmanager.android.domain.buildTopLevelTaskReorderScopeForTask
import com.taskmanager.android.domain.getTopLevelTaskIdsForScope
import com.taskmanager.android.domain.insertOrderedId
import com.taskmanager.android.domain.taskMatchesTopLevelReorderScope
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.NewTaskPlacement
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskSubtask
import com.taskmanager.android.model.TaskTopLevelReorderScope
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import retrofit2.HttpException

@Singleton
class TaskManagerRepository @Inject constructor(
    private val apiServiceFactory: ApiServiceFactory,
    private val preferencesStore: AppPreferencesStore,
    private val json: Json,
    private val okHttpClient: OkHttpClient,
    private val database: TaskManagerDatabase,
    private val dao: TaskManagerDao,
    private val localCacheMapper: LocalCacheMapper,
    private val notificationManager: TaskNotificationManager,
    private val syncScheduler: TaskSyncScheduler,
    private val clock: Clock,
) {
    private companion object {
        const val TAG = "TaskManagerRepository"
    }

    private val syncMutex = Mutex()
    private val syncingBaseUrls = MutableStateFlow(emptySet<String>())
    private val storedCreateSerializer = StoredTaskCreatePayload.serializer()
    private val storedUpdateSerializer = StoredTaskUpdatePayload.serializer()
    private val storedCompletionSerializer = StoredTaskCompletionPayload.serializer()
    private val storedSubtaskReorderSerializer = StoredSubtaskReorderPayload.serializer()
    private val storedDeleteSerializer = StoredTaskDeletePayload.serializer()
    private val descriptionBlockListSerializer = ListSerializer(com.taskmanager.android.data.api.ApiDescriptionBlock.serializer())

    fun observeLists(): Flow<List<ListItem>> =
        currentBaseUrlFlow().flatMapLatest { baseUrl ->
            dao.observeLists(baseUrl)
                .map(localCacheMapper::toListItems)
                .distinctUntilChanged()
        }

    fun observeTasks(): Flow<List<TaskItem>> =
        currentBaseUrlFlow().flatMapLatest { baseUrl ->
            dao.observeTasks(baseUrl)
                .map(localCacheMapper::toTaskItems)
                .distinctUntilChanged()
        }

    fun observeSyncStatus(): Flow<TaskSyncStatus> =
        currentBaseUrlFlow().flatMapLatest { baseUrl ->
            combine(
                dao.observePendingOperationCount(baseUrl),
                dao.observeSyncState(baseUrl),
                syncingBaseUrls.map { baseUrl in it },
            ) { pendingCount, state, isSyncing ->
                TaskSyncStatus(
                    isSyncing = isSyncing,
                    hasPendingChanges = pendingCount > 0,
                    pendingChangeCount = pendingCount,
                    lastSyncAtEpochMs = state?.lastSyncSuccessAtEpochMs,
                    lastSyncError = state?.lastSyncError,
                )
            }
        }

    suspend fun createTask(
        payload: ApiTaskCreatePayload,
        editorContext: TaskEditorContext,
        todayString: String,
        tomorrowString: String,
        newTaskPlacement: NewTaskPlacement,
    ) {
        val baseUrl = currentBaseUrl()
        if (payload.parentId != null && payload.parentId < 0) {
            appendSubtaskToPendingCreate(baseUrl, payload)
            syncScheduler.enqueuePendingSync()
            return
        }

        val allocatedIds = dao.allocateLocalTaskIds(baseUrl, 1 + payload.subtasks.size)
        val rootLocalId = allocatedIds.first()
        val localSubtaskIds = allocatedIds.drop(1)
        val rootPreview = buildLocalPreviewTask(rootLocalId, payload, localSubtaskIds)
        val reorderScope = if (payload.parentId == null && newTaskPlacement == NewTaskPlacement.START) {
            buildTopLevelTaskReorderScopeForTask(
                task = rootPreview,
                viewTarget = editorContext.viewTarget,
                todayString = todayString,
                tomorrowString = tomorrowString,
            )?.toStored()
        } else {
            null
        }
        val storedPayload = StoredTaskCreatePayload(
            clientRequestId = UUID.randomUUID().toString(),
            localTaskId = rootLocalId,
            title = payload.title,
            description = payload.description,
            descriptionBlocks = payload.descriptionBlocks,
            dueDate = payload.dueDate,
            startTime = payload.startTime,
            endTime = payload.endTime,
            reminderTime = payload.reminderTime,
            repeatConfig = payload.repeatConfig?.toDomain(),
            repeatUntil = payload.repeatUntil,
            isDone = payload.isDone,
            isPinned = payload.isPinned,
            priority = payload.priority,
            repeat = payload.repeat,
            parentId = payload.parentId,
            listId = payload.listId,
            subtasks = payload.subtasks.mapIndexed { index, subtask ->
                StoredTaskCreateSubtaskPayload(
                    localId = localSubtaskIds[index],
                    title = subtask.title,
                    description = subtask.description,
                    descriptionBlocks = subtask.descriptionBlocks,
                    dueDate = subtask.dueDate,
                    startTime = subtask.startTime,
                    endTime = subtask.endTime,
                    reminderTime = subtask.reminderTime,
                    isDone = subtask.isDone,
                )
            },
            reorderScope = reorderScope,
            placement = reorderScope?.let { newTaskPlacement.wire },
        )
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)
        val position = resolveLocalCreatePosition(
            baseUrl = baseUrl,
            parentId = payload.parentId,
            reorderScope = reorderScope?.toDomain(),
            placement = newTaskPlacement,
        )
        val localEntities = localCacheMapper.toTaskEntities(
            baseUrl = baseUrl,
            payload = storedPayload,
            createdAt = nowIso,
            position = position,
        )

        database.withTransaction {
            dao.upsertTasks(localEntities)
            dao.insertPendingOperation(
                PendingSyncOperationEntity(
                    baseUrl = baseUrl,
                    operationType = PendingSyncOperationType.CREATE_TASK.wire,
                    targetTaskId = rootLocalId,
                    payloadJson = json.encodeToString(storedCreateSerializer, storedPayload),
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                ),
            )
            clearSyncError(baseUrl)
        }

        refreshNotificationSchedulesSafely()
        syncScheduler.enqueuePendingSync()
    }

    suspend fun updateTask(taskId: Int, payload: JsonObject): TaskItem {
        val baseUrl = currentBaseUrl()
        val patch = decodeTaskUpdatePatch(payload)
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)
        val updateResult = applyTaskUpdatePatch(baseUrl, taskId, patch, nowIso)
        val updatedEntities = updateResult.updatedEntities
        val updatedTaskEntity = updateResult.updatedEntity
        val shouldQueueCompletion =
            patch.hasIsDone &&
                patch.isDone != null &&
                patch.isDone != updateResult.originalEntity.isDone

        database.withTransaction {
            dao.upsertTasks(updatedEntities)
            if (taskId < 0) {
                updatePendingCreateFromEntity(baseUrl, updatedTaskEntity, nowMs)
            } else {
                upsertPendingTaskUpdate(baseUrl, patch, updatedTaskEntity, nowMs)
            }
            if (shouldQueueCompletion && taskId > 0) {
                upsertPendingTaskCompletion(baseUrl, taskId, requireNotNull(patch.isDone), nowMs)
            }
            clearSyncError(baseUrl)
        }

        refreshNotificationSchedulesSafely()
        syncScheduler.enqueuePendingSync()
        return localCacheMapper.toTaskItems(updatedEntities).first { it.id == taskId }
    }

    suspend fun deleteTask(taskId: Int) {
        val baseUrl = currentBaseUrl()
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)
        val cachedTasks = dao.getTasks(baseUrl)
        val task = cachedTasks.firstOrNull { it.id == taskId && it.deletedAt == null } ?: throw ApiException("Task not found.", 404)

        database.withTransaction {
            if (taskId < 0) {
                deleteUnsyncedTaskLocally(
                    baseUrl = baseUrl,
                    cachedTasks = cachedTasks,
                    task = task,
                    nowMs = nowMs,
                    nowIso = nowIso,
                )
            } else {
                deleteSyncedTaskLocally(
                    baseUrl = baseUrl,
                    cachedTasks = cachedTasks,
                    task = task,
                    nowMs = nowMs,
                    nowIso = nowIso,
                )
            }
            clearSyncError(baseUrl)
        }

        refreshNotificationSchedulesSafely()
        if (dao.getPendingSyncOperations(baseUrl).isNotEmpty()) {
            syncScheduler.enqueuePendingSync()
        }
    }

    suspend fun toggleTask(taskId: Int) {
        val baseUrl = currentBaseUrl()
        val localTask = dao.getTask(baseUrl, taskId) ?: throw ApiException("Task not found.", 404)

        if (taskId < 0) {
            updatePendingCreateCompletion(baseUrl, taskId, !localTask.isDone)
            syncScheduler.enqueuePendingSync()
            return
        }

        val nextIsDone = !localTask.isDone
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)

        database.withTransaction {
            dao.upsertTasks(listOf(localTask.copy(isDone = nextIsDone, updatedAt = nowIso)))
            upsertPendingTaskCompletion(baseUrl, taskId, nextIsDone, nowMs)
            clearSyncError(baseUrl)
        }

        refreshNotificationSchedulesSafely()
        syncScheduler.enqueuePendingSync()
    }

    suspend fun createList(name: String, color: String): ListItem {
        val baseUrl = currentBaseUrl()
        val createdList = executeApiCall(baseUrl) { createList(ApiListPayload(name = name, color = color)) }
        refreshRemoteCache(baseUrl)
        return localCacheMapper.toListItems(localCacheMapper.toListEntities(baseUrl, listOf(createdList))).first()
    }

    suspend fun updateList(listId: Int, name: String, color: String): ListItem {
        val baseUrl = currentBaseUrl()
        val updatedList = executeApiCall(baseUrl) {
            updateList(listId = listId, payload = ApiListPayload(name = name, color = color))
        }
        refreshRemoteCache(baseUrl)
        return localCacheMapper.toListItems(localCacheMapper.toListEntities(baseUrl, listOf(updatedList))).first()
    }

    suspend fun deleteList(listId: Int) {
        val baseUrl = currentBaseUrl()
        executeApiCall(baseUrl) { deleteList(listId) }
        refreshRemoteCache(baseUrl)
    }

    suspend fun reorderLists(listIds: List<Int>): List<ListItem> {
        val baseUrl = currentBaseUrl()
        val reorderedLists = executeApiCall(baseUrl) { reorderLists(ApiListReorderPayload(listIds)) }
        refreshRemoteCache(baseUrl)
        return localCacheMapper.toListItems(localCacheMapper.toListEntities(baseUrl, reorderedLists))
    }

    suspend fun reorderTopLevelTasks(scope: TaskTopLevelReorderScope, taskIds: List<Int>): List<TaskItem> {
        require(taskIds.none { it < 0 }) { "Unsynced local tasks cannot be reordered before sync." }

        val baseUrl = currentBaseUrl()
        executeApiCall(baseUrl) {
            reorderTopLevelTasks(
                ApiTopLevelTaskReorderPayload(
                    taskIds = taskIds,
                    scope = scope.toApi(),
                ),
            )
        }
        refreshRemoteCache(baseUrl)
        return dao.getTasks(baseUrl).let(localCacheMapper::toTaskItems)
    }

    suspend fun reorderSubtasks(parentTaskId: Int, subtaskIds: List<Int>): TaskItem {
        val baseUrl = currentBaseUrl()
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)
        val cachedTasks = dao.getTasks(baseUrl)
        val parentTask =
            cachedTasks.firstOrNull { it.id == parentTaskId && it.deletedAt == null } ?: throw ApiException("Task not found.", 404)
        if (parentTask.parentId != null) {
            throw ApiException("Only top-level tasks can reorder subtasks.", 400)
        }

        val currentSubtasks = cachedTasks
            .filter { it.parentId == parentTaskId && it.deletedAt == null }
            .sortedWith(compareBy<CachedTaskEntity>({ it.position }, { it.createdAt }, { it.id }))
        val currentIds = currentSubtasks.map(CachedTaskEntity::id)
        if (currentIds.sorted() != subtaskIds.sorted()) {
            throw ApiException("Subtask reorder payload must contain the exact current subtask ids.", 400)
        }

        val positionsById = subtaskIds.mapIndexed { index, subtaskId -> subtaskId to index }.toMap()
        val reorderedSubtasks = currentSubtasks.map { subtask ->
            val nextPosition = positionsById.getValue(subtask.id)
            if (subtask.position == nextPosition) {
                subtask
            } else {
                subtask.copy(position = nextPosition, updatedAt = nowIso)
            }
        }
        val updatedTasks = cachedTasks.map { entity ->
            reorderedSubtasks.firstOrNull { it.id == entity.id } ?: entity
        }

        database.withTransaction {
            if (reorderedSubtasks.isNotEmpty()) {
                dao.upsertTasks(reorderedSubtasks)
            }
            if (parentTaskId < 0) {
                updatePendingCreateSubtaskOrder(baseUrl, parentTaskId, subtaskIds, nowMs)
            } else {
                upsertPendingSubtaskReorder(baseUrl, parentTaskId, subtaskIds, nowMs)
            }
            clearSyncError(baseUrl)
        }

        syncScheduler.enqueuePendingSync()
        return localCacheMapper.toTaskItems(updatedTasks).first { it.id == parentTaskId }
    }

    suspend fun moveTaskToParent(taskId: Int, parentTaskId: Int, orderedIds: List<Int>) {
        require(taskId > 0 && parentTaskId > 0 && orderedIds.none { it < 0 }) {
            "Unsynced local tasks cannot be moved before sync."
        }

        val baseUrl = currentBaseUrl()
        executeApiCall(baseUrl) {
            moveTask(
                taskId = taskId,
                payload = ApiTaskMovePayload(
                    destinationParentId = parentTaskId,
                    orderedIds = orderedIds,
                ),
            )
        }
        refreshRemoteCache(baseUrl)
    }

    suspend fun moveTaskToScope(taskId: Int, scope: TaskTopLevelReorderScope, orderedIds: List<Int>) {
        require(taskId > 0 && orderedIds.none { it < 0 }) { "Unsynced local tasks cannot be moved before sync." }

        val baseUrl = currentBaseUrl()
        executeApiCall(baseUrl) {
            moveTask(
                taskId = taskId,
                payload = ApiTaskMovePayload(
                    destinationScope = scope.toApi(),
                    orderedIds = orderedIds,
                ),
            )
        }
        refreshRemoteCache(baseUrl)
    }

    suspend fun syncCurrentBaseUrl(forceRefresh: Boolean = true): TaskSyncRunResult = syncBaseUrl(
        baseUrl = currentBaseUrl(),
        forceRefresh = forceRefresh,
    )

    suspend fun syncAllPendingBaseUrls(): TaskSyncRunResult {
        val baseUrls = dao.getPendingSyncBaseUrls()
        if (baseUrls.isEmpty()) {
            return TaskSyncRunResult(success = true, shouldRetry = false)
        }

        var anyRetryableFailure = false
        var lastError: String? = null
        for (baseUrl in baseUrls) {
            val result = syncBaseUrl(baseUrl, forceRefresh = true)
            if (!result.success) {
                anyRetryableFailure = anyRetryableFailure || result.shouldRetry
                lastError = result.errorMessage ?: lastError
                if (result.shouldRetry) {
                    break
                }
            }
        }

        return if (lastError == null) {
            TaskSyncRunResult(success = true, shouldRetry = false)
        } else {
            TaskSyncRunResult(success = false, shouldRetry = anyRetryableFailure, errorMessage = lastError)
        }
    }

    suspend fun scheduleBackgroundSyncIfPending() {
        if (dao.getPendingSyncBaseUrls().isNotEmpty()) {
            syncScheduler.enqueuePendingSync()
        }
    }

    suspend fun refreshNotificationSchedules() {
        notificationManager.refreshSchedules()
    }

    fun observeLiveEvents(baseUrl: String): Flow<Unit> = callbackFlow {
        val normalizedBaseUrl = ApiServiceFactory.normalizeBaseUrl(baseUrl)
        val request = Request.Builder()
            .url("${normalizedBaseUrl}events")
            .build()
        val eventSource = EventSources.createFactory(okHttpClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    val parsedEvent = runCatching { json.decodeFromString<ApiLiveEvent>(data) }.getOrNull() ?: return
                    if (parsedEvent.entityType == "task" || parsedEvent.entityType == "list") {
                        trySend(Unit)
                    }
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    close(t)
                }
            },
        )

        awaitClose { eventSource.cancel() }
    }

    private suspend fun syncBaseUrl(baseUrl: String, forceRefresh: Boolean): TaskSyncRunResult {
        return syncMutex.withLock {
            markSyncStart(baseUrl)
            syncingBaseUrls.update { it + baseUrl }

            try {
                val pendingOperations = dao.getPendingSyncOperations(baseUrl)
                if (pendingOperations.isEmpty()) {
                    if (!forceRefresh) {
                        markSyncSuccess(baseUrl)
                        return@withLock TaskSyncRunResult(success = true, shouldRetry = false)
                    }

                    return@withLock try {
                        refreshRemoteCache(baseUrl)
                        markSyncSuccess(baseUrl)
                        TaskSyncRunResult(success = true, shouldRetry = false)
                    } catch (error: ApiException) {
                        markSyncFailure(baseUrl, error.message)
                        TaskSyncRunResult(success = false, shouldRetry = shouldRetry(error), errorMessage = error.message)
                    }
                }

                val remoteTasks = fetchRemoteTasks(baseUrl)
                val remoteTaskById = flattenRemoteTasksById(remoteTasks).toMutableMap()
                var anyConfirmedOperation = false
                var syncFailure: ApiException? = null
                var retryableFailure = false
                var skipRefreshAfterFailure = false

                for (operation in pendingOperations) {
                    val operationResult = when (operation.operationType) {
                        PendingSyncOperationType.CREATE_TASK.wire -> syncCreateTaskOperation(baseUrl, operation)
                        PendingSyncOperationType.UPDATE_TASK.wire ->
                            syncTaskUpdateOperation(baseUrl, operation, remoteTaskById)
                        PendingSyncOperationType.SET_TASK_COMPLETION.wire ->
                            syncTaskCompletionOperation(baseUrl, operation, remoteTaskById)
                        PendingSyncOperationType.REORDER_SUBTASKS.wire ->
                            syncSubtaskReorderOperation(baseUrl, operation, remoteTaskById)
                        PendingSyncOperationType.DELETE_TASK.wire ->
                            syncTaskDeleteOperation(baseUrl, operation, remoteTaskById)
                        else -> SyncOperationResult.failure(
                            ApiException("Unknown sync operation: ${operation.operationType}"),
                            shouldRetry = false,
                        )
                    }

                    anyConfirmedOperation = anyConfirmedOperation || operationResult.confirmed
                    skipRefreshAfterFailure = skipRefreshAfterFailure || operationResult.skipRefreshOnFailure
                    operationResult.updatedRemoteTask?.let { updatedTask ->
                        remoteTaskById[updatedTask.id] = updatedTask
                    }
                    if (operationResult.failure != null) {
                        syncFailure = operationResult.failure
                        retryableFailure = operationResult.shouldRetry
                        break
                    }
                }

                if (!skipRefreshAfterFailure && (forceRefresh || anyConfirmedOperation || syncFailure == null)) {
                    try {
                        refreshRemoteCache(baseUrl)
                    } catch (error: ApiException) {
                        if (syncFailure == null) {
                            syncFailure = error
                            retryableFailure = shouldRetry(error)
                        }
                    }
                }

                if (syncFailure == null) {
                    markSyncSuccess(baseUrl)
                    TaskSyncRunResult(success = true, shouldRetry = false)
                } else {
                    markSyncFailure(baseUrl, syncFailure.message)
                    TaskSyncRunResult(
                        success = false,
                        shouldRetry = retryableFailure,
                        errorMessage = syncFailure.message,
                    )
                }
            } catch (error: ApiException) {
                markSyncFailure(baseUrl, error.message)
                TaskSyncRunResult(success = false, shouldRetry = shouldRetry(error), errorMessage = error.message)
            } finally {
                syncingBaseUrls.update { it - baseUrl }
            }
        }
    }

    private suspend fun syncCreateTaskOperation(
        baseUrl: String,
        operation: PendingSyncOperationEntity,
    ): SyncOperationResult {
        val payload = decodeStoredCreatePayload(operation.payloadJson)
        val createdTask = try {
            executeApiCall(baseUrl) { createTask(payload.toApiPayload()) }
        } catch (error: ApiException) {
            if (!shouldRetry(error)) {
                database.withTransaction {
                    dao.deleteTasksByIds(baseUrl, payload.localTaskIds())
                    dao.deletePendingOperation(operation.id)
                }
                return SyncOperationResult.failure(error, shouldRetry = false)
            }

            updateOperationFailure(operation, error.message)
            return SyncOperationResult.failure(error, shouldRetry = true, skipRefreshOnFailure = true)
        }

        database.withTransaction {
            val localPreviewTask = dao.getTask(baseUrl, payload.localTaskId)
            val syncedEntities = localCacheMapper.toTaskEntities(baseUrl, listOf(createdTask)).map { entity ->
                if (localPreviewTask != null && entity.parentId == createdTask.parentId && entity.id == createdTask.id) {
                    entity.copy(position = localPreviewTask.position)
                } else {
                    entity
                }
            }
            dao.upsertTasks(syncedEntities)
            dao.deleteTasksByIds(baseUrl, payload.localTaskIds())
            dao.deletePendingOperation(operation.id)
        }

        applyCreatePlacementIfNeeded(baseUrl, payload, createdTask.id)
        return SyncOperationResult.confirmed(createdTask)
    }

    private suspend fun syncTaskCompletionOperation(
        baseUrl: String,
        operation: PendingSyncOperationEntity,
        remoteTaskById: MutableMap<Int, ApiTask>,
    ): SyncOperationResult {
        val payload = json.decodeFromString(storedCompletionSerializer, operation.payloadJson)
        val remoteTask = remoteTaskById[operation.targetTaskId]
        if (remoteTask == null) {
            database.withTransaction {
                softDeleteTaskFamily(
                    baseUrl = baseUrl,
                    taskId = operation.targetTaskId,
                    nowIso = nowIsoString(clock.millis()),
                )
                dao.deletePendingOperation(operation.id)
            }
            return SyncOperationResult.confirmed()
        }

        if (remoteTask.isDone == payload.desiredIsDone) {
            database.withTransaction {
                dao.upsertTasks(localCacheMapper.toTaskEntities(baseUrl, listOf(remoteTask)))
                dao.deletePendingOperation(operation.id)
            }
            return SyncOperationResult.confirmed(remoteTask)
        }

        val toggledTask = try {
            executeApiCall(baseUrl) { toggleTask(operation.targetTaskId) }
        } catch (error: ApiException) {
            if (!shouldRetry(error)) {
                database.withTransaction {
                    dao.deletePendingOperation(operation.id)
                }
                return SyncOperationResult.failure(error, shouldRetry = false)
            }

            updateOperationFailure(operation, error.message)
            return SyncOperationResult.failure(error, shouldRetry = true)
        }

        if (toggledTask.isDone != payload.desiredIsDone) {
            updateOperationFailure(operation, "Server returned an unexpected completion state.")
            return SyncOperationResult.failure(
                error = ApiException("Server returned an unexpected completion state."),
                shouldRetry = true,
            )
        }

        database.withTransaction {
            dao.upsertTasks(localCacheMapper.toTaskEntities(baseUrl, listOf(toggledTask)))
            dao.deletePendingOperation(operation.id)
        }
        return SyncOperationResult.confirmed(toggledTask)
    }

    private suspend fun syncSubtaskReorderOperation(
        baseUrl: String,
        operation: PendingSyncOperationEntity,
        remoteTaskById: MutableMap<Int, ApiTask>,
    ): SyncOperationResult {
        val parentTask = remoteTaskById[operation.targetTaskId]
        if (parentTask == null) {
            database.withTransaction {
                softDeleteTaskFamily(
                    baseUrl = baseUrl,
                    taskId = operation.targetTaskId,
                    nowIso = nowIsoString(clock.millis()),
                )
                dao.deletePendingOperation(operation.id)
            }
            return SyncOperationResult.confirmed()
        }

        val localSubtaskIds = dao.getTasks(baseUrl)
            .asSequence()
            .filter { it.parentId == operation.targetTaskId && it.deletedAt == null }
            .sortedWith(compareBy<CachedTaskEntity>({ it.position }, { it.createdAt }, { it.id }))
            .map(CachedTaskEntity::id)
            .filter { it > 0 }
            .toList()

        if (localSubtaskIds.size <= 1 || localSubtaskIds == parentTask.subtasks.map(ApiTask::id)) {
            database.withTransaction {
                dao.upsertTasks(localCacheMapper.toTaskEntities(baseUrl, listOf(parentTask)))
                dao.deletePendingOperation(operation.id)
            }
            return SyncOperationResult.confirmed(parentTask)
        }

        val reorderedTask = try {
            executeApiCall(baseUrl) { reorderSubtasks(operation.targetTaskId, ApiSubtaskReorderPayload(localSubtaskIds)) }
        } catch (error: ApiException) {
            if (!shouldRetry(error)) {
                database.withTransaction {
                    dao.deletePendingOperation(operation.id)
                }
                return SyncOperationResult.failure(error, shouldRetry = false)
            }

            updateOperationFailure(operation, error.message)
            return SyncOperationResult.failure(error, shouldRetry = true)
        }

        database.withTransaction {
            dao.upsertTasks(localCacheMapper.toTaskEntities(baseUrl, listOf(reorderedTask)))
            dao.deletePendingOperation(operation.id)
        }
        return SyncOperationResult.confirmed(reorderedTask)
    }

    private suspend fun syncTaskDeleteOperation(
        baseUrl: String,
        operation: PendingSyncOperationEntity,
        remoteTaskById: MutableMap<Int, ApiTask>,
    ): SyncOperationResult {
        if (remoteTaskById[operation.targetTaskId] == null) {
            database.withTransaction {
                dao.deletePendingOperation(operation.id)
            }
            return SyncOperationResult.confirmed()
        }

        try {
            executeApiCall(baseUrl) { deleteTask(operation.targetTaskId) }
        } catch (error: ApiException) {
            if (error.statusCode == 404) {
                database.withTransaction {
                    dao.deletePendingOperation(operation.id)
                }
                return SyncOperationResult.confirmed()
            }

            if (!shouldRetry(error)) {
                updateOperationFailure(operation, error.message)
                return SyncOperationResult.failure(error, shouldRetry = false)
            }

            updateOperationFailure(operation, error.message)
            return SyncOperationResult.failure(error, shouldRetry = true)
        }

        database.withTransaction {
            dao.deletePendingOperation(operation.id)
        }
        return SyncOperationResult.confirmed()
    }

    private suspend fun syncTaskUpdateOperation(
        baseUrl: String,
        operation: PendingSyncOperationEntity,
        remoteTaskById: MutableMap<Int, ApiTask>,
    ): SyncOperationResult {
        val remoteTask = remoteTaskById[operation.targetTaskId]
        if (remoteTask == null) {
            database.withTransaction {
                softDeleteTaskFamily(
                    baseUrl = baseUrl,
                    taskId = operation.targetTaskId,
                    nowIso = nowIsoString(clock.millis()),
                )
                dao.deletePendingOperation(operation.id)
            }
            return SyncOperationResult.confirmed()
        }

        val payload = decodeStoredUpdatePayload(operation.payloadJson)
        val updatedTask = try {
            executeApiCall(baseUrl) { updateTask(operation.targetTaskId, payload.toApiPayload()) }
        } catch (error: ApiException) {
            if (!shouldRetry(error)) {
                database.withTransaction {
                    dao.deletePendingOperation(operation.id)
                }
                return SyncOperationResult.failure(error, shouldRetry = false)
            }

            updateOperationFailure(operation, error.message)
            return SyncOperationResult.failure(error, shouldRetry = true)
        }

        database.withTransaction {
            dao.upsertTasks(localCacheMapper.toTaskEntities(baseUrl, listOf(updatedTask)))
            dao.deletePendingOperation(operation.id)
        }
        return SyncOperationResult.confirmed(updatedTask)
    }

    private suspend fun applyCreatePlacementIfNeeded(
        baseUrl: String,
        payload: StoredTaskCreatePayload,
        createdTaskId: Int,
    ) {
        val reorderScope = payload.reorderScope ?: return
        if (payload.placementOrNull() != NewTaskPlacement.START) {
            return
        }

        val remoteTasks = executeApiCall(baseUrl) { getTasks() }
        val taskIds = getTopLevelTaskIdsForScope(
            tasks = localCacheMapper.toTaskItems(localCacheMapper.toTaskEntities(baseUrl, remoteTasks)),
            scope = reorderScope.toDomain(),
        ).filterNot { it == createdTaskId }
        val reorderedIds = insertOrderedId(taskIds, createdTaskId, NewTaskPlacement.START)
        if (reorderedIds.size <= 1) {
            return
        }

        val reorderedTasks = executeApiCall(baseUrl) {
            reorderTopLevelTasks(
                ApiTopLevelTaskReorderPayload(
                    taskIds = reorderedIds,
                    scope = reorderScope.toDomain().toApi(),
                ),
            )
        }
        database.withTransaction {
            dao.upsertTasks(localCacheMapper.toTaskEntities(baseUrl, reorderedTasks))
        }
    }

    private suspend fun refreshRemoteCache(baseUrl: String) {
        val (lists, tasks) = coroutineScope {
            val listsDeferred = async { executeApiCall(baseUrl) { getLists() } }
            val tasksDeferred = async { executeApiCall(baseUrl) { getTasks() } }
            listsDeferred.await() to tasksDeferred.await()
        }
        val localTasks = dao.getTasks(baseUrl)
        val pendingUpdateOverrides = loadPendingUpdateOverrides(baseUrl)
        val pendingCompletionOverrides = loadPendingCompletionOverrides(baseUrl)
        val pendingSubtaskReorderParents = loadPendingSubtaskReorderParents(baseUrl)
        val remoteTaskEntities = applyPendingCompletionOverrides(
            applyPendingSubtaskReorderOverrides(
                localTasks = localTasks,
                entities = applyPendingUpdateOverrides(localCacheMapper.toTaskEntities(baseUrl, tasks), pendingUpdateOverrides),
                reorderedParentIds = pendingSubtaskReorderParents,
            ),
            pendingCompletionOverrides,
        )
        val refreshedTaskEntities = mergeRemoteTasksWithLocalState(
            localTasks = localTasks,
            remoteEntities = remoteTaskEntities,
            refreshedAt = nowIsoString(clock.millis()),
        )

        database.withTransaction {
            dao.replaceLists(baseUrl, localCacheMapper.toListEntities(baseUrl, lists))
            dao.replaceTasks(baseUrl, refreshedTaskEntities)
        }
        refreshNotificationSchedulesSafely()
    }

    private suspend fun fetchRemoteTasks(baseUrl: String): List<ApiTask> =
        executeApiCall(baseUrl) { getTasks() }

    private suspend fun appendSubtaskToPendingCreate(baseUrl: String, payload: ApiTaskCreatePayload) {
        val parentLocalId = requireNotNull(payload.parentId)
        val existingParent = dao.getTask(baseUrl, parentLocalId)
            ?: throw ApiException("Parent task is no longer available offline.", 404)
        if (existingParent.parentId != null) {
            throw ApiException("Only one level of subtasks is supported.", 400)
        }

        val pendingCreate = findPendingCreateForLocalTask(baseUrl, parentLocalId)
            ?: throw ApiException("Parent task must sync before adding more subtasks.", 400)
        if (pendingCreate.payload.localTaskId != parentLocalId) {
            throw ApiException("Only pending parent tasks can accept offline subtasks.", 400)
        }

        val localId = dao.allocateLocalTaskIds(baseUrl, 1).first()
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)
        val updatedPayload = pendingCreate.payload.copy(
            subtasks = pendingCreate.payload.subtasks + StoredTaskCreateSubtaskPayload(
                localId = localId,
                title = payload.title,
                description = payload.description,
                descriptionBlocks = payload.descriptionBlocks,
                dueDate = payload.dueDate,
                startTime = payload.startTime,
                endTime = payload.endTime,
                reminderTime = payload.reminderTime,
                isDone = payload.isDone,
            ),
        )
        val position = dao.getTasks(baseUrl).count { it.parentId == parentLocalId && it.deletedAt == null }
        val localEntity = CachedTaskEntity(
            baseUrl = baseUrl,
            id = localId,
            parentId = parentLocalId,
            title = payload.title,
            description = payload.description,
            descriptionBlocksJson = localCacheMapper.encodeDescriptionBlocks(payload.descriptionBlocks),
            dueDate = payload.dueDate,
            startTime = payload.startTime,
            endTime = payload.endTime,
            reminderTime = payload.reminderTime,
            repeatConfigJson = null,
            repeatUntil = null,
            isDone = payload.isDone,
            isPinned = false,
            priority = existingParent.priority,
            repeat = TaskRepeat.NONE.wire,
            position = position,
            listId = existingParent.listId,
            createdAt = nowIso,
            updatedAt = nowIso,
        )

        database.withTransaction {
            dao.upsertTasks(listOf(localEntity))
            dao.updatePendingOperation(
                pendingCreate.operation.copy(
                    payloadJson = json.encodeToString(storedCreateSerializer, updatedPayload),
                    updatedAtEpochMs = nowMs,
                    lastErrorMessage = null,
                ),
            )
            clearSyncError(baseUrl)
        }
    }

    private suspend fun updatePendingCreateCompletion(baseUrl: String, taskId: Int, desiredIsDone: Boolean) {
        val pendingCreate = findPendingCreateForLocalTask(baseUrl, taskId)
            ?: throw ApiException("Unsynced task could not be updated.", 404)
        val nowMs = clock.millis()
        val nowIso = nowIsoString(nowMs)
        val updatedPayload = if (pendingCreate.payload.localTaskId == taskId) {
            pendingCreate.payload.copy(isDone = desiredIsDone)
        } else {
            pendingCreate.payload.copy(
                subtasks = pendingCreate.payload.subtasks.map { subtask ->
                    if (subtask.localId == taskId) {
                        subtask.copy(isDone = desiredIsDone)
                    } else {
                        subtask
                    }
                },
            )
        }
        val localTask = dao.getTask(baseUrl, taskId) ?: throw ApiException("Task not found.", 404)

        database.withTransaction {
            dao.upsertTasks(listOf(localTask.copy(isDone = desiredIsDone, updatedAt = nowIso)))
            dao.updatePendingOperation(
                pendingCreate.operation.copy(
                    payloadJson = json.encodeToString(storedCreateSerializer, updatedPayload),
                    updatedAtEpochMs = nowMs,
                    lastErrorMessage = null,
                ),
            )
            clearSyncError(baseUrl)
        }
    }

    private suspend fun deleteUnsyncedTaskLocally(
        baseUrl: String,
        cachedTasks: List<CachedTaskEntity>,
        task: CachedTaskEntity,
        nowMs: Long,
        nowIso: String,
    ) {
        val pendingCreate = findPendingCreateForLocalTask(baseUrl, task.id)
            ?: throw ApiException("Unsynced task could not be deleted.", 404)

        if (pendingCreate.payload.localTaskId == task.id) {
            dao.deleteTasksByIds(baseUrl, pendingCreate.payload.localTaskIds())
            dao.deletePendingOperation(pendingCreate.operation.id)
            return
        }

        val updatedSubtasks = pendingCreate.payload.subtasks
            .filterNot { it.localId == task.id }
        val updatedPayload = pendingCreate.payload.copy(subtasks = updatedSubtasks)
        val siblingPositions = updatedPayload.subtasks.mapIndexed { index, subtask -> subtask.localId to index }.toMap()
        val reindexedSiblings = cachedTasks
            .filter { it.parentId == pendingCreate.payload.localTaskId && it.id != task.id && it.deletedAt == null }
            .mapNotNull { sibling ->
                val nextPosition = siblingPositions[sibling.id] ?: return@mapNotNull null
                if (sibling.position == nextPosition) {
                    null
                } else {
                    sibling.copy(position = nextPosition, updatedAt = nowIso)
                }
            }

        dao.deleteTask(baseUrl, task.id)
        if (reindexedSiblings.isNotEmpty()) {
            dao.upsertTasks(reindexedSiblings)
        }
        dao.updatePendingOperation(
            pendingCreate.operation.copy(
                payloadJson = json.encodeToString(storedCreateSerializer, updatedPayload),
                updatedAtEpochMs = nowMs,
                lastErrorMessage = null,
            ),
        )
    }

    private suspend fun deleteSyncedTaskLocally(
        baseUrl: String,
        cachedTasks: List<CachedTaskEntity>,
        task: CachedTaskEntity,
        nowMs: Long,
        nowIso: String,
    ) {
        val softDeletedEntities = buildSoftDeletedTaskFamily(
            cachedTasks = cachedTasks,
            taskId = task.id,
            nowIso = nowIso,
        )

        if (softDeletedEntities.isEmpty()) {
            return
        }

        val affectedTaskIds = collectTaskFamily(cachedTasks, task.id).map(CachedTaskEntity::id)
        val localOnlyTaskIds = affectedTaskIds.filter { it < 0 }
        if (localOnlyTaskIds.isNotEmpty()) {
            dao.deleteTasksByIds(baseUrl, localOnlyTaskIds)
        }
        dao.upsertTasks(softDeletedEntities)
        if (affectedTaskIds.isNotEmpty()) {
            dao.deletePendingOperationsForTaskIds(baseUrl, affectedTaskIds)
        }
        upsertPendingTaskDelete(baseUrl, task.id, nowMs, nowIso)
    }

    private suspend fun softDeleteTaskFamily(
        baseUrl: String,
        taskId: Int,
        nowIso: String,
    ) {
        val cachedTasks = dao.getTasks(baseUrl)
        val familyIds = collectTaskFamily(cachedTasks, taskId).map(CachedTaskEntity::id)
        val updates = buildSoftDeletedTaskFamily(cachedTasks, taskId, nowIso)
        val localOnlyTaskIds = familyIds.filter { it < 0 }
        if (localOnlyTaskIds.isNotEmpty()) {
            dao.deleteTasksByIds(baseUrl, localOnlyTaskIds)
        }
        if (updates.isNotEmpty()) {
            dao.upsertTasks(updates)
        }
    }

    private fun buildSoftDeletedTaskFamily(
        cachedTasks: List<CachedTaskEntity>,
        taskId: Int,
        nowIso: String,
    ): List<CachedTaskEntity> {
        val targetTask = cachedTasks.firstOrNull { it.id == taskId } ?: return emptyList()
        val familyIds = collectTaskFamily(cachedTasks, taskId).map(CachedTaskEntity::id).toSet()
        val updates = linkedMapOf<Int, CachedTaskEntity>()

        cachedTasks
            .asSequence()
            .filter { it.id in familyIds && it.id > 0 && it.deletedAt == null }
            .forEach { entity ->
                updates[entity.id] = entity.copy(
                    deletedAt = nowIso,
                    updatedAt = nowIso,
                )
            }

        if (targetTask.parentId != null) {
            cachedTasks
                .filter { sibling ->
                    sibling.parentId == targetTask.parentId &&
                        sibling.id !in familyIds &&
                        sibling.deletedAt == null
                }
                .sortedWith(compareBy<CachedTaskEntity>({ it.position }, { it.createdAt }, { it.id }))
                .forEachIndexed { index, sibling ->
                    if (sibling.position != index) {
                        updates[sibling.id] = sibling.copy(position = index, updatedAt = nowIso)
                    }
                }
        }

        return updates.values.toList()
    }

    private fun collectTaskFamily(
        cachedTasks: List<CachedTaskEntity>,
        taskId: Int,
    ): List<CachedTaskEntity> {
        val task = cachedTasks.firstOrNull { it.id == taskId } ?: return emptyList()
        if (task.parentId != null) {
            return listOf(task)
        }

        return buildList {
            add(task)
            addAll(cachedTasks.filter { it.parentId == taskId })
        }
    }

    private fun mergeRemoteTasksWithLocalState(
        localTasks: List<CachedTaskEntity>,
        remoteEntities: List<CachedTaskEntity>,
        refreshedAt: String,
    ): List<CachedTaskEntity> {
        val finalEntities = linkedMapOf<Int, CachedTaskEntity>()
        val localTombstones = localTasks
            .filter { it.deletedAt != null }
            .associateBy(CachedTaskEntity::id)
        val remoteById = remoteEntities.associateBy(CachedTaskEntity::id)

        remoteEntities.forEach { remoteEntity ->
            finalEntities[remoteEntity.id] = localTombstones[remoteEntity.id] ?: remoteEntity
        }

        localTasks
            .filter { it.id < 0 && it.deletedAt == null }
            .forEach { localEntity ->
                finalEntities[localEntity.id] = localEntity
            }

        localTombstones.values.forEach { tombstone ->
            finalEntities[tombstone.id] = tombstone
        }

        localTasks
            .filter { localEntity ->
                localEntity.id > 0 &&
                    localEntity.deletedAt == null &&
                    localEntity.id !in remoteById
            }
            .forEach { missingRemoteEntity ->
                finalEntities[missingRemoteEntity.id] = missingRemoteEntity.copy(
                    deletedAt = refreshedAt,
                    updatedAt = refreshedAt,
                )
            }

        return finalEntities.values
            .sortedWith(
                compareBy<CachedTaskEntity>(
                    { if (it.parentId == null) 0 else 1 },
                    { it.parentId ?: it.id },
                    { it.position },
                    { it.createdAt },
                    { it.id },
                ),
            )
    }

    private suspend fun resolveLocalCreatePosition(
        baseUrl: String,
        parentId: Int?,
        reorderScope: TaskTopLevelReorderScope?,
        placement: NewTaskPlacement,
    ): Int {
        val cachedTasks = dao.getTasks(baseUrl)
        if (parentId != null) {
            return (cachedTasks
                .filter { it.parentId == parentId && it.deletedAt == null }
                .maxOfOrNull(CachedTaskEntity::position) ?: -1) + 1
        }
        if (placement == NewTaskPlacement.START && reorderScope != null) {
            val matchingPositions = localCacheMapper.toTaskItems(cachedTasks)
                .filter { taskMatchesTopLevelReorderScope(it, reorderScope) }
                .map(TaskItem::position)
            return (matchingPositions.minOrNull() ?: 0) - 1
        }

        return (cachedTasks
            .filter { it.parentId == null && it.deletedAt == null }
            .maxOfOrNull(CachedTaskEntity::position) ?: -1) + 1
    }

    private fun buildLocalPreviewTask(
        localTaskId: Int,
        payload: ApiTaskCreatePayload,
        localSubtaskIds: List<Int>,
    ): TaskItem = TaskItem(
        id = localTaskId,
        title = payload.title,
        description = payload.description,
        descriptionBlocks = payload.descriptionBlocks.map { it.toDomain() },
        dueDate = payload.dueDate,
        startTime = payload.startTime,
        endTime = payload.endTime,
        reminderTime = payload.reminderTime,
        repeatConfig = payload.repeatConfig?.toDomain(),
        repeatUntil = payload.repeatUntil,
        isDone = payload.isDone,
        isPinned = payload.isPinned,
        priority = TaskPriority.fromWire(payload.priority),
        repeat = TaskRepeat.fromWire(payload.repeat),
        parentId = payload.parentId,
        position = 0,
        listId = payload.listId,
        createdAt = nowIsoString(clock.millis()),
        updatedAt = nowIsoString(clock.millis()),
        subtasks = payload.subtasks.mapIndexed { index, subtask ->
            TaskSubtask(
                id = localSubtaskIds[index],
                title = subtask.title,
                description = subtask.description,
                descriptionBlocks = subtask.descriptionBlocks.map { it.toDomain() },
                dueDate = subtask.dueDate,
                startTime = subtask.startTime,
                endTime = subtask.endTime,
                reminderTime = subtask.reminderTime,
                repeatConfig = null,
                repeatUntil = null,
                isDone = subtask.isDone,
                isPinned = false,
                priority = TaskPriority.fromWire(payload.priority),
                repeat = TaskRepeat.NONE,
                parentId = localTaskId,
                position = index,
                listId = payload.listId,
                createdAt = nowIsoString(clock.millis()),
                updatedAt = nowIsoString(clock.millis()),
            )
        },
    )

    private suspend fun findPendingCreateForLocalTask(
        baseUrl: String,
        taskId: Int,
    ): PendingCreateOperationMatch? =
        dao.getPendingSyncOperations(baseUrl)
            .asSequence()
            .filter { it.operationType == PendingSyncOperationType.CREATE_TASK.wire }
            .mapNotNull { operation ->
                runCatching { decodeStoredCreatePayload(operation.payloadJson) }
                    .getOrNull()
                    ?.takeIf { payload ->
                        payload.localTaskId == taskId || payload.subtasks.any { it.localId == taskId }
                    }
                    ?.let { payload -> PendingCreateOperationMatch(operation, payload) }
            }
            .firstOrNull()

    private suspend fun applyTaskUpdatePatch(
        baseUrl: String,
        taskId: Int,
        patch: TaskUpdatePatch,
        nowIso: String,
    ): TaskUpdateApplicationResult {
        val cachedTasks = dao.getTasks(baseUrl)
        val originalEntity =
            cachedTasks.firstOrNull { it.id == taskId && it.deletedAt == null } ?: throw ApiException("Task not found.", 404)
        val parentEntity = originalEntity.parentId?.let { parentId ->
            cachedTasks.firstOrNull { it.id == parentId && it.deletedAt == null }
        }

        val nextDueDate = if (patch.hasDueDate) patch.dueDate else originalEntity.dueDate
        val originalRepeatConfig = localCacheMapper.decodeRepeatConfig(originalEntity.repeatConfigJson)
        val nextStartTime = if (nextDueDate == null) {
            null
        } else if (patch.hasStartTime) {
            patch.startTime
        } else {
            originalEntity.startTime
        }
        val nextEndTime = if (nextDueDate == null || nextStartTime == null) {
            null
        } else if (patch.hasEndTime) {
            patch.endTime
        } else {
            originalEntity.endTime
        }
        val nextReminderTime = if (nextDueDate == null) {
            null
        } else if (patch.hasReminderTime) {
            patch.reminderTime
        } else {
            originalEntity.reminderTime
        }
        val nextRepeat = if (patch.hasRepeat) patch.repeat ?: originalEntity.repeat else originalEntity.repeat
        val nextRepeatConfig = when {
            nextRepeat != TaskRepeat.CUSTOM.wire -> null
            patch.hasRepeatConfig -> patch.repeatConfig ?: originalRepeatConfig
            else -> originalRepeatConfig
        }
        val nextRepeatUntil = if (nextRepeat == TaskRepeat.NONE.wire) {
            null
        } else if (patch.hasRepeatUntil) {
            patch.repeatUntil
        } else {
            originalEntity.repeatUntil
        }
        val nextIsDone = if (patch.hasIsDone) patch.isDone ?: originalEntity.isDone else originalEntity.isDone
        val nextListId = if (patch.hasListId) {
            if (originalEntity.parentId != null) {
                parentEntity?.listId ?: originalEntity.listId
            } else {
                patch.listId
            }
        } else {
            originalEntity.listId
        }

        val updatedEntity = originalEntity.copy(
            title = if (patch.hasTitle) {
                patch.title?.trim()?.takeIf(String::isNotBlank) ?: originalEntity.title
            } else {
                originalEntity.title
            },
            description = if (patch.hasDescription) patch.description else originalEntity.description,
            descriptionBlocksJson = if (patch.hasDescriptionBlocks) {
                localCacheMapper.encodeDescriptionBlocks(patch.descriptionBlocks.orEmpty())
            } else {
                originalEntity.descriptionBlocksJson
            },
            dueDate = nextDueDate,
            startTime = nextStartTime,
            endTime = nextEndTime,
            reminderTime = nextReminderTime,
            repeatConfigJson = localCacheMapper.encodeRepeatConfig(nextRepeatConfig),
            repeatUntil = nextRepeatUntil,
            isDone = nextIsDone,
            isPinned = if (originalEntity.parentId != null) {
                false
            } else if (patch.hasIsPinned) {
                patch.isPinned ?: originalEntity.isPinned
            } else {
                originalEntity.isPinned
            },
            priority = if (patch.hasPriority) patch.priority ?: originalEntity.priority else originalEntity.priority,
            repeat = nextRepeat,
            listId = nextListId,
            updatedAt = nowIso,
        )

        val updatedEntities = cachedTasks.map { entity ->
            when {
                entity.id == taskId -> updatedEntity
                updatedEntity.parentId == null && entity.parentId == taskId && entity.listId != nextListId ->
                    entity.copy(listId = nextListId, updatedAt = nowIso)
                else -> entity
            }
        }

        return TaskUpdateApplicationResult(
            originalEntity = originalEntity,
            updatedEntity = updatedEntity,
            updatedEntities = updatedEntities,
        )
    }

    private suspend fun updatePendingCreateFromEntity(
        baseUrl: String,
        updatedEntity: CachedTaskEntity,
        nowMs: Long,
    ) {
        val pendingCreate = findPendingCreateForLocalTask(baseUrl, updatedEntity.id)
            ?: throw ApiException("Unsynced task could not be updated.", 404)
        val updatedPayload = if (pendingCreate.payload.localTaskId == updatedEntity.id) {
            pendingCreate.payload.copy(
                title = updatedEntity.title,
                description = updatedEntity.description,
                descriptionBlocks = localCacheMapper.decodeDescriptionBlocks(updatedEntity.descriptionBlocksJson),
                dueDate = updatedEntity.dueDate,
                startTime = updatedEntity.startTime,
                endTime = updatedEntity.endTime,
                reminderTime = updatedEntity.reminderTime,
                repeatConfig = localCacheMapper.decodeRepeatConfig(updatedEntity.repeatConfigJson),
                repeatUntil = updatedEntity.repeatUntil,
                isDone = updatedEntity.isDone,
                isPinned = updatedEntity.isPinned,
                priority = updatedEntity.priority,
                repeat = updatedEntity.repeat,
                listId = updatedEntity.listId,
            )
        } else {
            pendingCreate.payload.copy(
                subtasks = pendingCreate.payload.subtasks.map { subtask ->
                    if (subtask.localId == updatedEntity.id) {
                        subtask.copy(
                            title = updatedEntity.title,
                            description = updatedEntity.description,
                            descriptionBlocks = localCacheMapper.decodeDescriptionBlocks(updatedEntity.descriptionBlocksJson),
                            dueDate = updatedEntity.dueDate,
                            startTime = updatedEntity.startTime,
                            endTime = updatedEntity.endTime,
                            reminderTime = updatedEntity.reminderTime,
                            isDone = updatedEntity.isDone,
                        )
                    } else {
                        subtask
                    }
                },
            )
        }

        dao.updatePendingOperation(
            pendingCreate.operation.copy(
                payloadJson = json.encodeToString(storedCreateSerializer, updatedPayload),
                updatedAtEpochMs = nowMs,
                lastErrorMessage = null,
            ),
        )
    }

    private suspend fun updatePendingCreateSubtaskOrder(
        baseUrl: String,
        parentTaskId: Int,
        subtaskIds: List<Int>,
        nowMs: Long,
    ) {
        val pendingCreate = findPendingCreateForLocalTask(baseUrl, parentTaskId)
            ?: throw ApiException("Unsynced task could not be reordered.", 404)
        if (pendingCreate.payload.localTaskId != parentTaskId) {
            throw ApiException("Only pending parent tasks can reorder offline subtasks.", 400)
        }

        val subtasksById = pendingCreate.payload.subtasks.associateBy(StoredTaskCreateSubtaskPayload::localId)
        val reorderedSubtasks = subtaskIds.map { subtaskId ->
            subtasksById[subtaskId] ?: throw ApiException("Subtask reorder payload must contain the exact current subtask ids.", 400)
        }
        dao.updatePendingOperation(
            pendingCreate.operation.copy(
                payloadJson = json.encodeToString(
                    storedCreateSerializer,
                    pendingCreate.payload.copy(subtasks = reorderedSubtasks),
                ),
                updatedAtEpochMs = nowMs,
                lastErrorMessage = null,
            ),
        )
    }

    private suspend fun upsertPendingTaskUpdate(
        baseUrl: String,
        patch: TaskUpdatePatch,
        updatedEntity: CachedTaskEntity,
        nowMs: Long,
    ) {
        val existingOperation = dao.getPendingOperationForTask(
            baseUrl = baseUrl,
            operationType = PendingSyncOperationType.UPDATE_TASK.wire,
            taskId = updatedEntity.id,
        )
        val payload = existingOperation
            ?.let { decodeStoredUpdatePayload(it.payloadJson) }
            ?.merge(buildStoredTaskUpdatePayload(patch, updatedEntity))
            ?: buildStoredTaskUpdatePayload(patch, updatedEntity)
        val payloadJson = json.encodeToString(storedUpdateSerializer, payload)
        if (existingOperation == null) {
            dao.insertPendingOperation(
                PendingSyncOperationEntity(
                    baseUrl = baseUrl,
                    operationType = PendingSyncOperationType.UPDATE_TASK.wire,
                    targetTaskId = updatedEntity.id,
                    payloadJson = payloadJson,
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                ),
            )
        } else {
            dao.updatePendingOperation(
                existingOperation.copy(
                    payloadJson = payloadJson,
                    updatedAtEpochMs = nowMs,
                    lastErrorMessage = null,
                ),
            )
        }
    }

    private suspend fun upsertPendingTaskCompletion(
        baseUrl: String,
        taskId: Int,
        desiredIsDone: Boolean,
        nowMs: Long,
    ) {
        val payloadJson = json.encodeToString(
            storedCompletionSerializer,
            StoredTaskCompletionPayload(desiredIsDone = desiredIsDone),
        )
        val existingOperation = dao.getPendingOperationForTask(
            baseUrl = baseUrl,
            operationType = PendingSyncOperationType.SET_TASK_COMPLETION.wire,
            taskId = taskId,
        )
        if (existingOperation == null) {
            dao.insertPendingOperation(
                PendingSyncOperationEntity(
                    baseUrl = baseUrl,
                    operationType = PendingSyncOperationType.SET_TASK_COMPLETION.wire,
                    targetTaskId = taskId,
                    payloadJson = payloadJson,
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                ),
            )
        } else {
            dao.updatePendingOperation(
                existingOperation.copy(
                    payloadJson = payloadJson,
                    updatedAtEpochMs = nowMs,
                    lastErrorMessage = null,
                ),
            )
        }
    }

    private suspend fun upsertPendingSubtaskReorder(
        baseUrl: String,
        parentTaskId: Int,
        subtaskIds: List<Int>,
        nowMs: Long,
    ) {
        val payloadJson = json.encodeToString(
            storedSubtaskReorderSerializer,
            StoredSubtaskReorderPayload(orderedSubtaskIds = subtaskIds),
        )
        val existingOperation = dao.getPendingOperationForTask(
            baseUrl = baseUrl,
            operationType = PendingSyncOperationType.REORDER_SUBTASKS.wire,
            taskId = parentTaskId,
        )
        if (existingOperation == null) {
            dao.insertPendingOperation(
                PendingSyncOperationEntity(
                    baseUrl = baseUrl,
                    operationType = PendingSyncOperationType.REORDER_SUBTASKS.wire,
                    targetTaskId = parentTaskId,
                    payloadJson = payloadJson,
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                ),
            )
        } else {
            dao.updatePendingOperation(
                existingOperation.copy(
                    payloadJson = payloadJson,
                    updatedAtEpochMs = nowMs,
                    lastErrorMessage = null,
                ),
            )
        }
    }

    private suspend fun upsertPendingTaskDelete(
        baseUrl: String,
        taskId: Int,
        nowMs: Long,
        deletedAt: String,
    ) {
        val payloadJson = json.encodeToString(
            storedDeleteSerializer,
            StoredTaskDeletePayload(deletedAt = deletedAt),
        )
        val existingOperation = dao.getPendingOperationForTask(
            baseUrl = baseUrl,
            operationType = PendingSyncOperationType.DELETE_TASK.wire,
            taskId = taskId,
        )
        if (existingOperation == null) {
            dao.insertPendingOperation(
                PendingSyncOperationEntity(
                    baseUrl = baseUrl,
                    operationType = PendingSyncOperationType.DELETE_TASK.wire,
                    targetTaskId = taskId,
                    payloadJson = payloadJson,
                    createdAtEpochMs = nowMs,
                    updatedAtEpochMs = nowMs,
                ),
            )
        } else {
            dao.updatePendingOperation(
                existingOperation.copy(
                    payloadJson = payloadJson,
                    updatedAtEpochMs = nowMs,
                    lastErrorMessage = null,
                ),
            )
        }
    }

    private fun buildStoredTaskUpdatePayload(
        patch: TaskUpdatePatch,
        entity: CachedTaskEntity,
    ): StoredTaskUpdatePayload = StoredTaskUpdatePayload(
        title = entity.title.takeIf { patch.hasTitle },
        hasTitle = patch.hasTitle,
        description = if (patch.hasDescription) entity.description else null,
        hasDescription = patch.hasDescription,
        descriptionBlocks = if (patch.hasDescriptionBlocks) {
            localCacheMapper.decodeDescriptionBlocks(entity.descriptionBlocksJson)
        } else {
            null
        },
        hasDescriptionBlocks = patch.hasDescriptionBlocks,
        dueDate = if (patch.hasDueDate) entity.dueDate else null,
        hasDueDate = patch.hasDueDate,
        startTime = if (patch.hasStartTime) entity.startTime else null,
        hasStartTime = patch.hasStartTime,
        endTime = if (patch.hasEndTime) entity.endTime else null,
        hasEndTime = patch.hasEndTime,
        reminderTime = if (patch.hasReminderTime) entity.reminderTime else null,
        hasReminderTime = patch.hasReminderTime,
        repeatConfig = if (patch.hasRepeatConfig) localCacheMapper.decodeRepeatConfig(entity.repeatConfigJson) else null,
        hasRepeatConfig = patch.hasRepeatConfig,
        repeatUntil = if (patch.hasRepeatUntil) entity.repeatUntil else null,
        hasRepeatUntil = patch.hasRepeatUntil,
        isPinned = if (patch.hasIsPinned) entity.isPinned else null,
        hasIsPinned = patch.hasIsPinned,
        priority = entity.priority.takeIf { patch.hasPriority },
        hasPriority = patch.hasPriority,
        repeat = entity.repeat.takeIf { patch.hasRepeat },
        hasRepeat = patch.hasRepeat,
        listId = if (patch.hasListId) entity.listId else null,
        hasListId = patch.hasListId,
    )

    private suspend fun updateOperationFailure(operation: PendingSyncOperationEntity, message: String) {
        database.withTransaction {
            dao.updatePendingOperation(
                operation.copy(
                    updatedAtEpochMs = clock.millis(),
                    lastAttemptAtEpochMs = clock.millis(),
                    lastErrorMessage = message,
                ),
            )
        }
    }

    private suspend fun markSyncStart(baseUrl: String) {
        val state = dao.getSyncState(baseUrl) ?: SyncStateEntity(baseUrl = baseUrl)
        dao.upsertSyncState(state.copy(lastSyncAttemptAtEpochMs = clock.millis()))
    }

    private suspend fun markSyncSuccess(baseUrl: String) {
        val state = dao.getSyncState(baseUrl) ?: SyncStateEntity(baseUrl = baseUrl)
        dao.upsertSyncState(
            state.copy(
                lastSyncAttemptAtEpochMs = clock.millis(),
                lastSyncSuccessAtEpochMs = clock.millis(),
                lastSyncError = null,
            ),
        )
    }

    private suspend fun markSyncFailure(baseUrl: String, message: String) {
        val state = dao.getSyncState(baseUrl) ?: SyncStateEntity(baseUrl = baseUrl)
        dao.upsertSyncState(
            state.copy(
                lastSyncAttemptAtEpochMs = clock.millis(),
                lastSyncFailureAtEpochMs = clock.millis(),
                lastSyncError = message,
            ),
        )
    }

    private suspend fun clearSyncError(baseUrl: String) {
        val state = dao.getSyncState(baseUrl) ?: SyncStateEntity(baseUrl = baseUrl)
        if (state.lastSyncError == null) {
            return
        }
        dao.upsertSyncState(state.copy(lastSyncError = null))
    }

    private suspend fun currentBaseUrl(): String = preferencesStore.currentPreferences().baseUrl

    private suspend fun loadPendingUpdateOverrides(baseUrl: String): Map<Int, StoredTaskUpdatePayload> =
        dao.getPendingSyncOperations(baseUrl)
            .asSequence()
            .filter { it.operationType == PendingSyncOperationType.UPDATE_TASK.wire }
            .associate { operation ->
                operation.targetTaskId to decodeStoredUpdatePayload(operation.payloadJson)
            }
            .toMap()

    private suspend fun loadPendingCompletionOverrides(baseUrl: String): Map<Int, Boolean> =
        dao.getPendingSyncOperations(baseUrl)
            .asSequence()
            .filter { it.operationType == PendingSyncOperationType.SET_TASK_COMPLETION.wire }
            .associate { operation ->
                operation.targetTaskId to json.decodeFromString(storedCompletionSerializer, operation.payloadJson).desiredIsDone
            }
            .toMap()

    private suspend fun loadPendingSubtaskReorderParents(baseUrl: String): Set<Int> =
        dao.getPendingSyncOperations(baseUrl)
            .asSequence()
            .filter { it.operationType == PendingSyncOperationType.REORDER_SUBTASKS.wire }
            .map(PendingSyncOperationEntity::targetTaskId)
            .toSet()

    private fun applyPendingUpdateOverrides(
        entities: List<CachedTaskEntity>,
        overrides: Map<Int, StoredTaskUpdatePayload>,
    ): List<CachedTaskEntity> {
        if (overrides.isEmpty()) {
            return entities
        }

        return entities.map { entity ->
            val override = overrides[entity.id] ?: return@map entity
            entity.copy(
                title = if (override.hasTitle) override.title ?: entity.title else entity.title,
                description = if (override.hasDescription) override.description else entity.description,
                descriptionBlocksJson = if (override.hasDescriptionBlocks) {
                    localCacheMapper.encodeDescriptionBlocks(override.descriptionBlocks.orEmpty())
                } else {
                    entity.descriptionBlocksJson
                },
                dueDate = if (override.hasDueDate) override.dueDate else entity.dueDate,
                startTime = if (override.hasStartTime) override.startTime else entity.startTime,
                endTime = if (override.hasEndTime) override.endTime else entity.endTime,
                reminderTime = if (override.hasReminderTime) override.reminderTime else entity.reminderTime,
                repeatConfigJson = if (override.hasRepeatConfig) {
                    localCacheMapper.encodeRepeatConfig(override.repeatConfig)
                } else {
                    entity.repeatConfigJson
                },
                repeatUntil = if (override.hasRepeatUntil) override.repeatUntil else entity.repeatUntil,
                isPinned = if (entity.parentId != null) {
                    false
                } else if (override.hasIsPinned) {
                    override.isPinned ?: entity.isPinned
                } else {
                    entity.isPinned
                },
                priority = if (override.hasPriority) override.priority ?: entity.priority else entity.priority,
                repeat = if (override.hasRepeat) override.repeat ?: entity.repeat else entity.repeat,
                listId = if (override.hasListId) override.listId else entity.listId,
            )
        }.let { updatedEntities ->
            overrides.entries.fold(updatedEntities) { currentEntities, (taskId, override) ->
                currentEntities.map { entity ->
                    if (override.hasListId && entity.parentId == taskId && entity.listId != override.listId) {
                        entity.copy(listId = override.listId)
                    } else {
                        entity
                    }
                }
            }
        }
    }

    private fun applyPendingCompletionOverrides(
        entities: List<CachedTaskEntity>,
        overrides: Map<Int, Boolean>,
    ): List<CachedTaskEntity> = entities.map { entity ->
        overrides[entity.id]?.let { desiredIsDone ->
            entity.copy(isDone = desiredIsDone)
        } ?: entity
    }

    private fun applyPendingSubtaskReorderOverrides(
        localTasks: List<CachedTaskEntity>,
        entities: List<CachedTaskEntity>,
        reorderedParentIds: Set<Int>,
    ): List<CachedTaskEntity> {
        if (reorderedParentIds.isEmpty()) {
            return entities
        }

        val localPositionsById = localTasks
            .asSequence()
            .filter { it.parentId in reorderedParentIds && it.deletedAt == null }
            .associate { it.id to it.position }

        return entities.map { entity ->
            localPositionsById[entity.id]?.let { localPosition ->
                entity.copy(position = localPosition)
            } ?: entity
        }
    }

    private fun currentBaseUrlFlow(): Flow<String> =
        preferencesStore.preferences
            .map { it.baseUrl }
            .distinctUntilChanged()

    private suspend fun <T> executeApiCall(
        baseUrl: String,
        block: suspend com.taskmanager.android.data.api.TaskManagerApi.() -> T,
    ): T {
        val service = apiServiceFactory.create(baseUrl)
        return try {
            service.block()
        } catch (error: HttpException) {
            val detail = error.response()?.errorBody()?.string()?.let(::extractDetail)
            val apiException = ApiException(
                detail ?: "Request failed.",
                error.code(),
                technicalMessage = error.message(),
                cause = error,
            )
            logRequestFailure(baseUrl, apiException)
            throw apiException
        } catch (error: ApiException) {
            logRequestFailure(baseUrl, error)
            throw error
        } catch (error: Exception) {
            val apiException = mapUnexpectedApiFailure(error)
            logRequestFailure(baseUrl, apiException)
            throw apiException
        }
    }

    private fun decodeStoredCreatePayload(payloadJson: String): StoredTaskCreatePayload =
        json.decodeFromString(storedCreateSerializer, payloadJson)

    private fun decodeStoredUpdatePayload(payloadJson: String): StoredTaskUpdatePayload =
        json.decodeFromString(storedUpdateSerializer, payloadJson)

    private fun decodeTaskUpdatePatch(payload: JsonObject): TaskUpdatePatch = TaskUpdatePatch(
        title = payload["title"]?.jsonPrimitive?.contentOrNull,
        hasTitle = "title" in payload,
        description = payload["description"]?.jsonPrimitive?.contentOrNull,
        hasDescription = "description" in payload,
        descriptionBlocks = payload["description_blocks"]?.takeUnless { it is JsonNull }?.let { element ->
            json.decodeFromJsonElement(descriptionBlockListSerializer, element)
        },
        hasDescriptionBlocks = "description_blocks" in payload,
        dueDate = payload["due_date"]?.jsonPrimitive?.contentOrNull,
        hasDueDate = "due_date" in payload,
        startTime = payload["start_time"]?.jsonPrimitive?.contentOrNull,
        hasStartTime = "start_time" in payload,
        endTime = payload["end_time"]?.jsonPrimitive?.contentOrNull,
        hasEndTime = "end_time" in payload,
        reminderTime = payload["reminder_time"]?.jsonPrimitive?.contentOrNull,
        hasReminderTime = "reminder_time" in payload,
        repeatConfig = payload["repeat_config"]?.takeUnless { it is JsonNull }?.let { element ->
            json.decodeFromJsonElement(TaskCustomRepeatConfig.serializer(), element)
        },
        hasRepeatConfig = "repeat_config" in payload,
        repeatUntil = payload["repeat_until"]?.jsonPrimitive?.contentOrNull,
        hasRepeatUntil = "repeat_until" in payload,
        isDone = payload["is_done"]?.jsonPrimitive?.booleanOrNull,
        hasIsDone = "is_done" in payload,
        isPinned = payload["is_pinned"]?.jsonPrimitive?.booleanOrNull,
        hasIsPinned = "is_pinned" in payload,
        priority = payload["priority"]?.jsonPrimitive?.contentOrNull,
        hasPriority = "priority" in payload,
        repeat = payload["repeat"]?.jsonPrimitive?.contentOrNull,
        hasRepeat = "repeat" in payload,
        listId = payload["list_id"]?.jsonPrimitive?.intOrNull,
        hasListId = "list_id" in payload,
    )

    private fun extractDetail(rawBody: String): String? = runCatching {
        json.parseToJsonElement(rawBody).jsonObject["detail"]?.toString()?.trim('"')
    }.getOrNull()

    private fun logRequestFailure(baseUrl: String, error: ApiException) {
        Log.e(
            TAG,
            "API request failed for baseUrl=$baseUrl, status=${error.statusCode}, technical=${error.technicalMessage ?: error.cause?.message}",
            error.cause ?: error,
        )
    }

    private suspend fun refreshNotificationSchedulesSafely() {
        runCatching { notificationManager.refreshSchedules() }
            .onFailure { error -> Log.w(TAG, "Failed to refresh notification schedules", error) }
    }

    private fun shouldRetry(error: ApiException): Boolean {
        val statusCode = error.statusCode ?: return true
        return statusCode >= 500 || statusCode == 408 || statusCode == 429
    }

    private fun nowIsoString(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    private data class PendingCreateOperationMatch(
        val operation: PendingSyncOperationEntity,
        val payload: StoredTaskCreatePayload,
    )

    private data class TaskUpdateApplicationResult(
        val originalEntity: CachedTaskEntity,
        val updatedEntity: CachedTaskEntity,
        val updatedEntities: List<CachedTaskEntity>,
    )

    private data class TaskUpdatePatch(
        val title: String? = null,
        val hasTitle: Boolean = false,
        val description: String? = null,
        val hasDescription: Boolean = false,
        val descriptionBlocks: List<com.taskmanager.android.data.api.ApiDescriptionBlock>? = null,
        val hasDescriptionBlocks: Boolean = false,
        val dueDate: String? = null,
        val hasDueDate: Boolean = false,
        val startTime: String? = null,
        val hasStartTime: Boolean = false,
        val endTime: String? = null,
        val hasEndTime: Boolean = false,
        val reminderTime: String? = null,
        val hasReminderTime: Boolean = false,
        val repeatConfig: TaskCustomRepeatConfig? = null,
        val hasRepeatConfig: Boolean = false,
        val repeatUntil: String? = null,
        val hasRepeatUntil: Boolean = false,
        val isDone: Boolean? = null,
        val hasIsDone: Boolean = false,
        val isPinned: Boolean? = null,
        val hasIsPinned: Boolean = false,
        val priority: String? = null,
        val hasPriority: Boolean = false,
        val repeat: String? = null,
        val hasRepeat: Boolean = false,
        val listId: Int? = null,
        val hasListId: Boolean = false,
    )

    private data class SyncOperationResult(
        val confirmed: Boolean,
        val shouldRetry: Boolean,
        val failure: ApiException? = null,
        val updatedRemoteTask: ApiTask? = null,
        val skipRefreshOnFailure: Boolean = false,
    ) {
        companion object {
            fun confirmed(updatedRemoteTask: ApiTask? = null): SyncOperationResult = SyncOperationResult(
                confirmed = true,
                shouldRetry = false,
                updatedRemoteTask = updatedRemoteTask,
            )

            fun failure(
                error: ApiException,
                shouldRetry: Boolean,
                skipRefreshOnFailure: Boolean = false,
            ): SyncOperationResult = SyncOperationResult(
                confirmed = false,
                shouldRetry = shouldRetry,
                failure = error,
                skipRefreshOnFailure = skipRefreshOnFailure,
            )
        }
    }
}

private fun StoredTaskCreatePayload.localTaskIds(): List<Int> = buildList {
    add(localTaskId)
    subtasks.forEach { add(it.localId) }
}

private fun flattenRemoteTasksById(tasks: List<ApiTask>): Map<Int, ApiTask> {
    val flattened = linkedMapOf<Int, ApiTask>()

    fun visit(task: ApiTask) {
        flattened[task.id] = task
        task.subtasks.forEach(::visit)
    }

    tasks.forEach(::visit)
    return flattened
}
