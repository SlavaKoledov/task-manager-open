package com.taskmanager.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taskmanager.android.domain.buildTaskSectionCollapseKey
import com.taskmanager.android.domain.filterTasksForView
import com.taskmanager.android.domain.getVisibleAllTaskGroups
import com.taskmanager.android.domain.getVisibleTodayTaskGroups
import com.taskmanager.android.domain.getVisibleTaskCollection
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskSubtask
import com.taskmanager.android.model.TaskTopLevelReorderScope
import com.taskmanager.android.model.TaskViewTarget
import com.taskmanager.android.ui.TaskManagerUiState
import com.taskmanager.android.data.sync.TaskSyncVisualState
import com.taskmanager.android.ui.components.TaskSectionCard

@Composable
fun HomeScreen(
    viewTarget: TaskViewTarget,
    uiState: TaskManagerUiState,
    onSelectAllGroup: (AllTaskGroupId?) -> Unit,
    onCreateTask: (TaskEditorContext) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onRequestDeleteTask: (TaskItem) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleSubtask: (TaskSubtask) -> Unit,
    onToggleSection: (String) -> Unit,
    onToggleTaskSubtasks: (Int) -> Unit,
    onToggleExpandedSubtaskPreview: (Int) -> Unit,
    onStartTaskMove: (Int, Int?, String, Boolean) -> Unit,
    onCancelTaskMove: () -> Unit,
    onReorderTasks: (TaskTopLevelReorderScope, List<Int>) -> Unit,
    onMoveTaskToParent: (Int, Int, List<Int>) -> Unit,
    onMoveTaskToScope: (Int, TaskTopLevelReorderScope, List<Int>) -> Unit,
) {
    val todayString = uiState.todayString
    val tomorrowString = uiState.tomorrowString
    val showCompleted = uiState.preferences.showCompleted
    val listById = remember(uiState.lists) { uiState.lists.associateBy(ListItem::id) }
    val tasksForView = remember(uiState.tasks, viewTarget, todayString, tomorrowString) {
        filterTasksForView(uiState.tasks, viewTarget, todayString, tomorrowString)
    }
    val allGroups = remember(tasksForView, todayString, showCompleted, viewTarget) {
        if (viewTarget == TaskViewTarget.All) {
            getVisibleAllTaskGroups(tasksForView, todayString, showCompleted)
        } else {
            emptyList()
        }
    }
    val todayGroups = remember(tasksForView, todayString, showCompleted, viewTarget) {
        if (viewTarget == TaskViewTarget.Today) {
            getVisibleTodayTaskGroups(tasksForView, todayString, showCompleted)
        } else {
            emptyList()
        }
    }
    val collection = remember(tasksForView, showCompleted, viewTarget) {
        if (viewTarget == TaskViewTarget.All || viewTarget == TaskViewTarget.Today) {
            null
        } else {
            getVisibleTaskCollection(tasksForView, showCompleted)
        }
    }
    val displayedAllGroups = remember(allGroups, uiState.selectedAllGroup) {
        uiState.selectedAllGroup?.let { selected ->
            allGroups.filter { it.id == selected }
        } ?: allGroups
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        uiState.activeMoveTask?.let { movingTask ->
            item(key = "move-banner", contentType = "move-banner") {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Moving ${movingTask.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Long-press starts move mode. Tap a blue target to drop into a task, after a subtask, or into a section.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onCancelTaskMove) {
                            Text("Cancel move")
                        }
                    }
                }
            }
        }

        if (
            uiState.syncVisualState == TaskSyncVisualState.SYNCING ||
            uiState.syncVisualState == TaskSyncVisualState.PENDING ||
            uiState.syncVisualState == TaskSyncVisualState.FAILED
        ) {
            item(key = "sync-banner", contentType = "sync-banner") {
                SyncStatusBanner(uiState = uiState)
            }
        }

        if (viewTarget == TaskViewTarget.All) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all-group-filter:all", contentType = "all-group-filter") {
                        FilterChip(
                            selected = uiState.selectedAllGroup == null,
                            onClick = { onSelectAllGroup(null) },
                            label = { Text("All") },
                        )
                    }
                    items(
                        items = AllTaskGroupId.entries,
                        key = { it.wire },
                        contentType = { "all-group-filter" },
                    ) { groupId ->
                        FilterChip(
                            selected = uiState.selectedAllGroup == groupId,
                            onClick = { onSelectAllGroup(groupId) },
                            label = { Text(groupId.title) },
                        )
                    }
                }
            }

            if (displayedAllGroups.isEmpty()) {
                item {
                    EmptyState(text = "No tasks match the selected filter.")
                }
            }

            items(
                items = displayedAllGroups,
                key = { it.id.wire },
                contentType = { "all-group" },
            ) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (group.totalCount == 0) {
                        EmptyState(text = group.emptyDescription)
                    } else {
                        group.sections.forEach { section ->
                            TaskSectionCard(
                                title = section.title,
                                sectionId = section.id,
                                tasks = section.tasks,
                                collapsed = buildTaskSectionCollapseKey(group.id.wire, section.id) in uiState.collapsedSections,
                                listById = listById,
                                collapsedTaskIds = uiState.collapsedTaskIds,
                                onToggleTask = onToggleTask,
                                onToggleSubtask = onToggleSubtask,
                                onEditTask = onEditTask,
                                onRequestDeleteTask = onRequestDeleteTask,
                                onToggleSubtasks = onToggleTaskSubtasks,
                                onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                                onStartTaskMove = onStartTaskMove,
                                onToggleCollapsed = { onToggleSection(buildTaskSectionCollapseKey(group.id.wire, section.id)) },
                                todayString = todayString,
                                tomorrowString = tomorrowString,
                                expandedSubtaskPreviewIds = uiState.expandedSubtaskPreviewIds,
                                activeMoveTask = uiState.activeMoveTask,
                                onCreateTaskInSection = {
                                    onCreateTask(
                                        TaskEditorContext(
                                            viewTarget = viewTarget,
                                            groupId = group.id,
                                            sectionId = section.id,
                                        ),
                                    )
                                },
                                reorderScope = TaskTopLevelReorderScope.AllScope(
                                    groupId = group.id,
                                    referenceDate = todayString,
                                    sectionId = section.id,
                                ),
                                onReorderTasks = onReorderTasks,
                                onMoveTaskToParent = onMoveTaskToParent,
                                onMoveTaskToScope = onMoveTaskToScope,
                            )
                        }

                        if (showCompleted && group.doneTasks.isNotEmpty()) {
                            TaskSectionCard(
                                title = "Done",
                                sectionId = com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT,
                                tasks = group.doneTasks,
                                collapsed = buildTaskSectionCollapseKey("${group.id.wire}:done", com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT) in uiState.collapsedSections,
                                listById = listById,
                                collapsedTaskIds = uiState.collapsedTaskIds,
                                onToggleTask = onToggleTask,
                                onToggleSubtask = onToggleSubtask,
                                onEditTask = onEditTask,
                                onRequestDeleteTask = onRequestDeleteTask,
                                onToggleSubtasks = onToggleTaskSubtasks,
                                onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                                onStartTaskMove = onStartTaskMove,
                                onToggleCollapsed = {
                                    onToggleSection(
                                        buildTaskSectionCollapseKey("${group.id.wire}:done", com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT),
                                    )
                                },
                                todayString = todayString,
                                tomorrowString = tomorrowString,
                                expandedSubtaskPreviewIds = uiState.expandedSubtaskPreviewIds,
                                activeMoveTask = uiState.activeMoveTask,
                            )
                        }
                    }
                }
            }
        } else if (viewTarget == TaskViewTarget.Today) {
            if (todayGroups.isEmpty()) {
                item {
                    EmptyState(text = "No tasks in today.")
                }
            } else {
                items(
                    items = todayGroups,
                    key = { "today-group:${it.id.wire}" },
                    contentType = { "today-group" },
                ) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (group.totalCount == 0) {
                            EmptyState(text = group.emptyDescription)
                        } else {
                            group.sections.forEach { section ->
                                TaskSectionCard(
                                    title = section.title,
                                    sectionId = section.id,
                                    tasks = section.tasks,
                                    collapsed = buildTaskSectionCollapseKey(group.id.wire, section.id) in uiState.collapsedSections,
                                    listById = listById,
                                    collapsedTaskIds = uiState.collapsedTaskIds,
                                    onToggleTask = onToggleTask,
                                    onToggleSubtask = onToggleSubtask,
                                    onEditTask = onEditTask,
                                    onRequestDeleteTask = onRequestDeleteTask,
                                    onToggleSubtasks = onToggleTaskSubtasks,
                                    onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                                    onStartTaskMove = onStartTaskMove,
                                    onToggleCollapsed = { onToggleSection(buildTaskSectionCollapseKey(group.id.wire, section.id)) },
                                    todayString = todayString,
                                    tomorrowString = tomorrowString,
                                    expandedSubtaskPreviewIds = uiState.expandedSubtaskPreviewIds,
                                    activeMoveTask = uiState.activeMoveTask,
                                    onCreateTaskInSection = if (group.id == AllTaskGroupId.TODAY) {
                                        { onCreateTask(TaskEditorContext(viewTarget = viewTarget, sectionId = section.id)) }
                                    } else {
                                        null
                                    },
                                    reorderScope = if (group.id == AllTaskGroupId.OVERDUE) {
                                        TaskTopLevelReorderScope.AllScope(
                                            groupId = AllTaskGroupId.OVERDUE,
                                            referenceDate = todayString,
                                            sectionId = section.id,
                                        )
                                    } else {
                                        TaskTopLevelReorderScope.DateScope(
                                            view = com.taskmanager.android.model.ViewMode.TODAY,
                                            targetDate = todayString,
                                            sectionId = section.id,
                                        )
                                    },
                                    onReorderTasks = onReorderTasks,
                                    onMoveTaskToParent = onMoveTaskToParent,
                                    onMoveTaskToScope = onMoveTaskToScope,
                                )
                            }

                            if (showCompleted && group.doneTasks.isNotEmpty()) {
                                TaskSectionCard(
                                    title = "Done",
                                    sectionId = com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT,
                                    tasks = group.doneTasks,
                                    collapsed = buildTaskSectionCollapseKey("${group.id.wire}:done", com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT) in uiState.collapsedSections,
                                    listById = listById,
                                    collapsedTaskIds = uiState.collapsedTaskIds,
                                    onToggleTask = onToggleTask,
                                    onToggleSubtask = onToggleSubtask,
                                    onEditTask = onEditTask,
                                    onRequestDeleteTask = onRequestDeleteTask,
                                    onToggleSubtasks = onToggleTaskSubtasks,
                                    onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                                    onStartTaskMove = onStartTaskMove,
                                    onToggleCollapsed = {
                                        onToggleSection(
                                            buildTaskSectionCollapseKey("${group.id.wire}:done", com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT),
                                        )
                                    },
                                    todayString = todayString,
                                    tomorrowString = tomorrowString,
                                    expandedSubtaskPreviewIds = uiState.expandedSubtaskPreviewIds,
                                    activeMoveTask = uiState.activeMoveTask,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val visibleCollection = collection

            if (visibleCollection == null || visibleCollection.totalCount == 0) {
                item {
                    EmptyState(text = "No tasks in ${viewTarget.title.lowercase()}.")
                }
            } else {
                items(
                    items = visibleCollection.sections,
                    key = { it.id.wire },
                    contentType = { "task-section" },
                ) { section ->
                    val sectionScope = when (viewTarget) {
                        TaskViewTarget.Inbox -> TaskTopLevelReorderScope.InboxScope(section.id)
                        TaskViewTarget.Today -> TaskTopLevelReorderScope.DateScope(
                            view = com.taskmanager.android.model.ViewMode.TODAY,
                            targetDate = todayString,
                            sectionId = section.id,
                        )
                        TaskViewTarget.Tomorrow -> TaskTopLevelReorderScope.DateScope(
                            view = com.taskmanager.android.model.ViewMode.TOMORROW,
                            targetDate = tomorrowString,
                            sectionId = section.id,
                        )
                        is TaskViewTarget.ListView -> TaskTopLevelReorderScope.ListScope(
                            listId = viewTarget.listId,
                            sectionId = section.id,
                        )
                        TaskViewTarget.All -> null
                        TaskViewTarget.Calendar -> null
                    }

                    TaskSectionCard(
                        title = section.title,
                        sectionId = section.id,
                        tasks = section.tasks,
                        collapsed = buildTaskSectionCollapseKey(viewTarget.mode.name.lowercase(), section.id) in uiState.collapsedSections,
                        listById = listById,
                        collapsedTaskIds = uiState.collapsedTaskIds,
                        onToggleTask = onToggleTask,
                        onToggleSubtask = onToggleSubtask,
                        onEditTask = onEditTask,
                        onRequestDeleteTask = onRequestDeleteTask,
                        onToggleSubtasks = onToggleTaskSubtasks,
                        onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                        onStartTaskMove = onStartTaskMove,
                        onToggleCollapsed = { onToggleSection(buildTaskSectionCollapseKey(viewTarget.mode.name.lowercase(), section.id)) },
                        todayString = todayString,
                        tomorrowString = tomorrowString,
                        expandedSubtaskPreviewIds = uiState.expandedSubtaskPreviewIds,
                        activeMoveTask = uiState.activeMoveTask,
                        onCreateTaskInSection = {
                            onCreateTask(TaskEditorContext(viewTarget = viewTarget, sectionId = section.id))
                        },
                        reorderScope = sectionScope,
                        onReorderTasks = onReorderTasks,
                        onMoveTaskToParent = onMoveTaskToParent,
                        onMoveTaskToScope = onMoveTaskToScope,
                    )
                }

                if (showCompleted && visibleCollection.doneTasks.isNotEmpty()) {
                    item {
                        TaskSectionCard(
                            title = "Done",
                            sectionId = com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT,
                            tasks = visibleCollection.doneTasks,
                            collapsed = buildTaskSectionCollapseKey("${viewTarget.mode.name.lowercase()}:done", com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT) in uiState.collapsedSections,
                            listById = listById,
                            collapsedTaskIds = uiState.collapsedTaskIds,
                            onToggleTask = onToggleTask,
                            onToggleSubtask = onToggleSubtask,
                            onEditTask = onEditTask,
                            onRequestDeleteTask = onRequestDeleteTask,
                            onToggleSubtasks = onToggleTaskSubtasks,
                            onToggleExpandedSubtaskPreview = onToggleExpandedSubtaskPreview,
                            onStartTaskMove = onStartTaskMove,
                            onToggleCollapsed = {
                                onToggleSection(
                                    buildTaskSectionCollapseKey("${viewTarget.mode.name.lowercase()}:done", com.taskmanager.android.model.TaskSectionId.NOT_URGENT_UNIMPORTANT),
                                )
                            },
                            todayString = todayString,
                            tomorrowString = tomorrowString,
                            expandedSubtaskPreviewIds = uiState.expandedSubtaskPreviewIds,
                            activeMoveTask = uiState.activeMoveTask,
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SyncStatusBanner(uiState: TaskManagerUiState) {
    val (title, color) = when (uiState.syncVisualState) {
        TaskSyncVisualState.SYNCING -> Pair("Syncing", MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
        TaskSyncVisualState.PENDING -> Pair("Pending changes", MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f))
        TaskSyncVisualState.FAILED -> Pair("Sync paused", MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
        else -> Pair("", MaterialTheme.colorScheme.surface)
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
}
