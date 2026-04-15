package com.taskmanager.android.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_lists",
    primaryKeys = ["base_url", "id"],
)
data class CachedListEntity(
    @ColumnInfo(name = "base_url") val baseUrl: String,
    val id: Int,
    val name: String,
    val color: String,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(
    tableName = "cached_tasks",
    primaryKeys = ["base_url", "id"],
    indices = [
        Index(value = ["base_url", "parent_id"]),
        Index(value = ["base_url", "list_id"]),
    ],
)
data class CachedTaskEntity(
    @ColumnInfo(name = "base_url") val baseUrl: String,
    val id: Int,
    @ColumnInfo(name = "parent_id") val parentId: Int?,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "description_blocks_json") val descriptionBlocksJson: String,
    @ColumnInfo(name = "due_date") val dueDate: String?,
    @ColumnInfo(name = "reminder_time") val reminderTime: String?,
    @ColumnInfo(name = "repeat_config_json") val repeatConfigJson: String?,
    @ColumnInfo(name = "repeat_until") val repeatUntil: String?,
    @ColumnInfo(name = "is_done") val isDone: Boolean,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    val priority: String,
    val repeat: String,
    val position: Int,
    @ColumnInfo(name = "list_id") val listId: Int?,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: String? = null,
)

@Entity(
    tableName = "pending_sync_operations",
    indices = [
        Index(value = ["base_url"]),
        Index(value = ["base_url", "operation_type", "target_task_id"]),
    ],
)
data class PendingSyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "operation_type") val operationType: String,
    @ColumnInfo(name = "target_task_id") val targetTaskId: Int,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
    @ColumnInfo(name = "last_attempt_at_epoch_ms") val lastAttemptAtEpochMs: Long? = null,
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String? = null,
)

@Entity(
    tableName = "sync_state",
    primaryKeys = ["base_url"],
)
data class SyncStateEntity(
    @ColumnInfo(name = "base_url") val baseUrl: String,
    @ColumnInfo(name = "next_local_task_id") val nextLocalTaskId: Int = -1,
    @ColumnInfo(name = "last_sync_attempt_at_epoch_ms") val lastSyncAttemptAtEpochMs: Long? = null,
    @ColumnInfo(name = "last_sync_success_at_epoch_ms") val lastSyncSuccessAtEpochMs: Long? = null,
    @ColumnInfo(name = "last_sync_failure_at_epoch_ms") val lastSyncFailureAtEpochMs: Long? = null,
    @ColumnInfo(name = "last_sync_error") val lastSyncError: String? = null,
)

enum class PendingSyncOperationType(val wire: String) {
    CREATE_TASK("create_task"),
    UPDATE_TASK("update_task"),
    SET_TASK_COMPLETION("set_task_completion"),
    REORDER_SUBTASKS("reorder_subtasks"),
    DELETE_TASK("delete_task"),
    ;
}
