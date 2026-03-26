package com.taskmanager.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskmanager.android.BuildConfig
import com.taskmanager.android.data.api.ApiException
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.data.preferences.AppPreferencesStore
import com.taskmanager.android.data.repository.TaskManagerRepository
import com.taskmanager.android.data.sync.TaskSyncStatus
import com.taskmanager.android.data.sync.TaskSyncVisualState
import com.taskmanager.android.domain.getLocalDateString
import com.taskmanager.android.domain.getLocalTimeZoneId
import com.taskmanager.android.domain.getMillisecondsUntilNextLocalMidnight
import com.taskmanager.android.domain.getTomorrowDateString
import com.taskmanager.android.model.ActiveTaskMove
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.AppPreferences
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.NewTaskPlacement
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskTopLevelReorderScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class TaskManagerUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val syncVisualState: TaskSyncVisualState = TaskSyncVisualState.IDLE,
    val hasPendingSync: Boolean = false,
    val pendingSyncCount: Int = 0,
    val lastSyncError: String? = null,
    val lastSyncAt: Long? = null,
    val lists: List<ListItem> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val preferences: AppPreferences = AppPreferences(baseUrl = BuildConfig.DEFAULT_API_BASE_URL),
    val selectedAllGroup: AllTaskGroupId? = null,
    val collapsedSections: Set<String> = emptySet(),
    val collapsedTaskIds: Set<Int> = emptySet(),
    val expandedSubtaskPreviewIds: Set<Int> = emptySet(),
    val activeMoveTask: ActiveTaskMove? = null,
    val todayString: String = getLocalDateString(),
    val tomorrowString: String = getTomorrowDateString(),
    val timeZoneId: String = getLocalTimeZoneId(),
    val errorMessage: String? = null,
)

@HiltViewModel
class TaskManagerViewModel @Inject constructor(
    private val repository: TaskManagerRepository,
    private val preferencesStore: AppPreferencesStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TaskManagerUiState())
    val uiState: StateFlow<TaskManagerUiState> = _uiState.asStateFlow()

    private var lastBaseUrl: String? = null
    private var liveSyncJob: Job? = null
    private var midnightMonitorJob: Job? = null
    private var hasLoadedLists = false
    private var hasLoadedTasks = false

    init {
        startMidnightMonitor()
        observePreferences()
        observeLists()
        observeTasks()
        observeSyncStatus()
    }

    fun refresh() {
        launchSync(forceRefresh = true, showErrors = true)
    }

    fun onAppForegrounded() {
        handleTimeContextChanged()
        viewModelScope.launch {
            repository.scheduleBackgroundSyncIfPending()
        }
        if (uiState.value.hasPendingSync || (uiState.value.tasks.isEmpty() && uiState.value.lists.isEmpty())) {
            launchSync(forceRefresh = true, showErrors = false)
        }
    }

    fun onAppBackgrounded() {
        viewModelScope.launch {
            repository.scheduleBackgroundSyncIfPending()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setSelectedAllGroup(groupId: AllTaskGroupId?) {
        _uiState.update { it.copy(selectedAllGroup = groupId) }
    }

    fun toggleSectionCollapse(key: String) {
        _uiState.update { current ->
            current.copy(
                collapsedSections = current.collapsedSections.toMutableSet().apply {
                    if (!add(key)) remove(key)
                },
            )
        }
    }

    fun toggleTaskSubtasks(taskId: Int) {
        _uiState.update { current ->
            current.copy(
                collapsedTaskIds = current.collapsedTaskIds.toMutableSet().apply {
                    if (!add(taskId)) remove(taskId)
                },
            )
        }
    }

    fun toggleExpandedSubtaskPreview(taskId: Int) {
        _uiState.update { current ->
            current.copy(
                expandedSubtaskPreviewIds = current.expandedSubtaskPreviewIds.toMutableSet().apply {
                    if (!add(taskId)) remove(taskId)
                },
            )
        }
    }

    fun startTaskMove(taskId: Int, parentId: Int?, title: String, hasSubtasks: Boolean) {
        _uiState.update {
            it.copy(
                activeMoveTask = ActiveTaskMove(
                    taskId = taskId,
                    parentId = parentId,
                    title = title,
                    hasSubtasks = hasSubtasks,
                ),
            )
        }
    }

    fun cancelTaskMove() {
        _uiState.update { it.copy(activeMoveTask = null) }
    }

    fun findTask(taskId: Int): TaskItem? = uiState.value.tasks.firstOrNull { it.id == taskId }

    fun findList(listId: Int): ListItem? = uiState.value.lists.firstOrNull { it.id == listId }

    fun handleTimeContextChanged() {
        val previousState = uiState.value
        val nextTodayString = getLocalDateString()
        val nextTomorrowString = getTomorrowDateString()
        val nextTimeZoneId = getLocalTimeZoneId()
        val changed =
            nextTodayString != previousState.todayString ||
                nextTomorrowString != previousState.tomorrowString ||
                nextTimeZoneId != previousState.timeZoneId

        if (!changed) {
            return
        }

        _uiState.update {
            it.copy(
                todayString = nextTodayString,
                tomorrowString = nextTomorrowString,
                timeZoneId = nextTimeZoneId,
            )
        }

        if (uiState.value.tasks.isNotEmpty()) {
            launchSync(forceRefresh = true, showErrors = false)
        }
    }

    suspend fun createTask(payload: ApiTaskCreatePayload, editorContext: TaskEditorContext): Result<Unit> = runAction {
        repository.createTask(
            payload = payload,
            editorContext = editorContext,
            todayString = uiState.value.todayString,
            tomorrowString = uiState.value.tomorrowString,
            newTaskPlacement = uiState.value.preferences.newTaskPlacement,
        )
        launchSync(forceRefresh = true, showErrors = false)
    }

    suspend fun updateTask(taskId: Int, payload: JsonObject): Result<Unit> = runAction {
        repository.updateTask(taskId, payload)
    }

    suspend fun deleteTask(taskId: Int): Result<Unit> = runAction {
        repository.deleteTask(taskId)
    }

    suspend fun toggleTask(taskId: Int): Result<Unit> = runAction {
        repository.toggleTask(taskId)
        launchSync(forceRefresh = true, showErrors = false)
    }

    suspend fun reorderTopLevelTasks(scope: TaskTopLevelReorderScope, taskIds: List<Int>): Result<Unit> = runAction {
        repository.reorderTopLevelTasks(scope, taskIds)
    }

    suspend fun reorderSubtasks(parentTaskId: Int, subtaskIds: List<Int>): Result<Unit> = runAction {
        repository.reorderSubtasks(parentTaskId, subtaskIds)
    }

    suspend fun moveTaskToParent(taskId: Int, parentTaskId: Int, orderedIds: List<Int>): Result<Unit> = runAction {
        repository.moveTaskToParent(taskId, parentTaskId, orderedIds)
        cancelTaskMove()
    }

    suspend fun moveTaskToScope(taskId: Int, scope: TaskTopLevelReorderScope, orderedIds: List<Int>): Result<Unit> = runAction {
        repository.moveTaskToScope(taskId, scope, orderedIds)
        cancelTaskMove()
    }

    suspend fun createList(name: String, color: String): Result<Unit> = runAction {
        repository.createList(name, color)
    }

    suspend fun updateList(listId: Int, name: String, color: String): Result<Unit> = runAction {
        repository.updateList(listId, name, color)
    }

    suspend fun deleteList(listId: Int): Result<Unit> = runAction {
        repository.deleteList(listId)
    }

    suspend fun reorderLists(listIds: List<Int>): Result<Unit> = runAction {
        repository.reorderLists(listIds)
    }

    suspend fun setShowCompleted(value: Boolean): Result<Unit> = runAction {
        preferencesStore.setShowCompleted(value)
    }

    suspend fun setNewTaskPlacement(value: NewTaskPlacement): Result<Unit> = runAction {
        preferencesStore.setNewTaskPlacement(value)
    }

    suspend fun setDailyNotificationEnabled(value: Boolean): Result<Unit> = runAction {
        preferencesStore.setDailyNotificationEnabled(value)
        repository.refreshNotificationSchedules()
    }

    suspend fun setDailyNotificationTime(value: String): Result<Unit> = runAction {
        preferencesStore.setDailyNotificationTime(value)
        repository.refreshNotificationSchedules()
    }

    suspend fun setBaseUrl(value: String): Result<Unit> = runAction {
        preferencesStore.setBaseUrl(value)
        repository.refreshNotificationSchedules()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesStore.preferences.collect { preferences ->
                val previousBaseUrl = lastBaseUrl
                lastBaseUrl = preferences.baseUrl
                _uiState.update { current -> current.copy(preferences = preferences) }
                repository.refreshNotificationSchedules()

                if (previousBaseUrl == null || previousBaseUrl != preferences.baseUrl) {
                    restartLiveSync(preferences.baseUrl)
                    launchSync(forceRefresh = true, showErrors = false)
                }
            }
        }
    }

    private fun observeLists() {
        viewModelScope.launch {
            repository.observeLists().collect { lists ->
                hasLoadedLists = true
                _uiState.update { current ->
                    current.copy(
                        lists = lists,
                        isLoading = shouldShowLoading(),
                    )
                }
            }
        }
    }

    private fun observeTasks() {
        viewModelScope.launch {
            repository.observeTasks().collect { tasks ->
                hasLoadedTasks = true
                _uiState.update { current ->
                    val activeMoveTask = current.activeMoveTask?.takeIf { movingTask ->
                        tasks.any { it.id == movingTask.taskId } ||
                            tasks.any { task -> task.subtasks.any { subtask -> subtask.id == movingTask.taskId } }
                    }
                    current.copy(
                        tasks = tasks,
                        activeMoveTask = activeMoveTask,
                        isLoading = shouldShowLoading(),
                    )
                }
            }
        }
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            repository.observeSyncStatus().collect { status ->
                _uiState.update { current ->
                    current.copy(
                        isRefreshing = status.isSyncing,
                        isSyncing = status.isSyncing,
                        hasPendingSync = status.hasPendingChanges,
                        pendingSyncCount = status.pendingChangeCount,
                        lastSyncError = status.lastSyncError,
                        lastSyncAt = status.lastSyncAtEpochMs,
                        syncVisualState = deriveSyncVisualState(status),
                    )
                }
            }
        }
    }

    private suspend fun runAction(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (error: Exception) {
        val message = (error as? ApiException)?.message ?: error.message ?: "Request failed."
        _uiState.update { it.copy(errorMessage = message) }
        Result.failure(error)
    }

    private fun launchSync(forceRefresh: Boolean, showErrors: Boolean) {
        viewModelScope.launch {
            repository.syncCurrentBaseUrl(forceRefresh = forceRefresh)
        }
    }

    private fun restartLiveSync(baseUrl: String) {
        liveSyncJob?.cancel()
        liveSyncJob = viewModelScope.launch {
            while (true) {
                try {
                    repository.observeLiveEvents(baseUrl).collect {
                        if (!uiState.value.isSyncing) {
                            launchSync(forceRefresh = true, showErrors = false)
                        }
                    }
                } catch (_: Exception) {
                    delay(2_000)
                }
            }
        }
    }

    private fun startMidnightMonitor() {
        midnightMonitorJob?.cancel()
        midnightMonitorJob = viewModelScope.launch {
            while (true) {
                delay(getMillisecondsUntilNextLocalMidnight() + 100)
                handleTimeContextChanged()
            }
        }
    }

    private fun shouldShowLoading(): Boolean = !hasLoadedLists || !hasLoadedTasks

    private fun deriveSyncVisualState(status: TaskSyncStatus): TaskSyncVisualState = when {
        status.isSyncing -> TaskSyncVisualState.SYNCING
        status.lastSyncError != null -> TaskSyncVisualState.FAILED
        status.hasPendingChanges -> TaskSyncVisualState.PENDING
        status.lastSyncAtEpochMs != null -> TaskSyncVisualState.SUCCESS
        else -> TaskSyncVisualState.IDLE
    }
}
