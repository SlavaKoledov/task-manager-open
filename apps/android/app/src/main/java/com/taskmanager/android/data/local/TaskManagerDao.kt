package com.taskmanager.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskManagerDao {
    @Query(
        """
        SELECT * FROM cached_lists
        WHERE base_url = :baseUrl
        ORDER BY position ASC, id ASC
        """,
    )
    abstract fun observeLists(baseUrl: String): Flow<List<CachedListEntity>>

    @Query(
        """
        SELECT * FROM cached_tasks
        WHERE base_url = :baseUrl
        AND deleted_at IS NULL
        ORDER BY CASE WHEN parent_id IS NULL THEN 0 ELSE 1 END ASC,
                 COALESCE(parent_id, id) ASC,
                 position ASC,
                 created_at ASC,
                 id ASC
        """,
    )
    abstract fun observeTasks(baseUrl: String): Flow<List<CachedTaskEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM pending_sync_operations
        WHERE base_url = :baseUrl
        """,
    )
    abstract fun observePendingOperationCount(baseUrl: String): Flow<Int>

    @Query(
        """
        SELECT * FROM sync_state
        WHERE base_url = :baseUrl
        LIMIT 1
        """,
    )
    abstract fun observeSyncState(baseUrl: String): Flow<SyncStateEntity?>

    @Query(
        """
        SELECT * FROM cached_tasks
        WHERE base_url = :baseUrl
        AND id = :taskId
        AND deleted_at IS NULL
        LIMIT 1
        """,
    )
    abstract suspend fun getTask(baseUrl: String, taskId: Int): CachedTaskEntity?

    @Query(
        """
        SELECT * FROM cached_tasks
        WHERE base_url = :baseUrl
        ORDER BY id ASC
        """,
    )
    abstract suspend fun getTasks(baseUrl: String): List<CachedTaskEntity>

    @Query(
        """
        SELECT * FROM pending_sync_operations
        WHERE base_url = :baseUrl
        ORDER BY id ASC
        """,
    )
    abstract suspend fun getPendingSyncOperations(baseUrl: String): List<PendingSyncOperationEntity>

    @Query(
        """
        SELECT DISTINCT base_url
        FROM pending_sync_operations
        ORDER BY base_url ASC
        """,
    )
    abstract suspend fun getPendingSyncBaseUrls(): List<String>

    @Query(
        """
        SELECT * FROM pending_sync_operations
        WHERE base_url = :baseUrl
        AND operation_type = :operationType
        AND target_task_id = :taskId
        LIMIT 1
        """,
    )
    abstract suspend fun getPendingOperationForTask(
        baseUrl: String,
        operationType: String,
        taskId: Int,
    ): PendingSyncOperationEntity?

    @Query(
        """
        SELECT * FROM sync_state
        WHERE base_url = :baseUrl
        LIMIT 1
        """,
    )
    abstract suspend fun getSyncState(baseUrl: String): SyncStateEntity?

    @Query("SELECT COUNT(*) FROM cached_tasks WHERE base_url = :baseUrl")
    abstract suspend fun getCachedTaskCount(baseUrl: String): Int

    @Query("SELECT COUNT(*) FROM cached_lists WHERE base_url = :baseUrl")
    abstract suspend fun getCachedListCount(baseUrl: String): Int

    @Upsert
    abstract suspend fun upsertLists(entities: List<CachedListEntity>)

    @Upsert
    abstract suspend fun upsertTasks(entities: List<CachedTaskEntity>)

    @Upsert
    abstract suspend fun upsertSyncState(entity: SyncStateEntity)

    @Insert
    abstract suspend fun insertPendingOperation(entity: PendingSyncOperationEntity): Long

    @Update
    abstract suspend fun updatePendingOperation(entity: PendingSyncOperationEntity)

    @Query("DELETE FROM pending_sync_operations WHERE id = :operationId")
    abstract suspend fun deletePendingOperation(operationId: Long)

    @Query(
        """
        DELETE FROM pending_sync_operations
        WHERE base_url = :baseUrl
        AND target_task_id IN (:taskIds)
        """,
    )
    abstract suspend fun deletePendingOperationsForTaskIds(baseUrl: String, taskIds: List<Int>)

    @Query("DELETE FROM cached_lists WHERE base_url = :baseUrl")
    abstract suspend fun deleteLists(baseUrl: String)

    @Query("DELETE FROM cached_tasks WHERE base_url = :baseUrl")
    abstract suspend fun deleteAllTasks(baseUrl: String)

    @Query("DELETE FROM cached_tasks WHERE base_url = :baseUrl AND id > 0")
    abstract suspend fun deleteRemoteTasks(baseUrl: String)

    @Query("DELETE FROM cached_tasks WHERE base_url = :baseUrl AND id IN (:taskIds)")
    abstract suspend fun deleteTasksByIds(baseUrl: String, taskIds: List<Int>)

    @Query("DELETE FROM cached_tasks WHERE base_url = :baseUrl AND id = :taskId")
    abstract suspend fun deleteTask(baseUrl: String, taskId: Int)

    @Transaction
    open suspend fun replaceLists(baseUrl: String, entities: List<CachedListEntity>) {
        deleteLists(baseUrl)
        if (entities.isNotEmpty()) {
            upsertLists(entities)
        }
    }

    @Transaction
    open suspend fun replaceRemoteTasks(baseUrl: String, entities: List<CachedTaskEntity>) {
        deleteRemoteTasks(baseUrl)
        if (entities.isNotEmpty()) {
            upsertTasks(entities)
        }
    }

    @Transaction
    open suspend fun replaceTasks(baseUrl: String, entities: List<CachedTaskEntity>) {
        deleteAllTasks(baseUrl)
        if (entities.isNotEmpty()) {
            upsertTasks(entities)
        }
    }

    @Transaction
    open suspend fun allocateLocalTaskIds(baseUrl: String, count: Int): List<Int> {
        require(count > 0) { "count must be positive." }

        val currentState = getSyncState(baseUrl) ?: SyncStateEntity(baseUrl = baseUrl)
        val start = currentState.nextLocalTaskId
        val ids = (0 until count).map { offset -> start - offset }
        upsertSyncState(currentState.copy(nextLocalTaskId = start - count))
        return ids
    }
}
