package com.taskmanager.android.data.sync

data class TaskSyncStatus(
    val isSyncing: Boolean = false,
    val hasPendingChanges: Boolean = false,
    val pendingChangeCount: Int = 0,
    val lastSyncAtEpochMs: Long? = null,
    val lastSyncError: String? = null,
)

enum class TaskSyncVisualState {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED,
    PENDING,
}

data class TaskSyncRunResult(
    val success: Boolean,
    val shouldRetry: Boolean,
    val errorMessage: String? = null,
)
