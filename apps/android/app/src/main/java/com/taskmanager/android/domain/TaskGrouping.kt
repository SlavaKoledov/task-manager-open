package com.taskmanager.android.domain

import com.taskmanager.android.model.AllTaskGroup
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.BaseTask
import com.taskmanager.android.model.SidebarCounts
import com.taskmanager.android.model.TaskCollection
import com.taskmanager.android.model.TaskInsertDirection
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskSection
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.model.TaskTopLevelReorderScope
import com.taskmanager.android.model.TaskViewTarget
import java.time.LocalDate

private val priorityOrder = mapOf(
    TaskPriority.URGENT_IMPORTANT to 0,
    TaskPriority.NOT_URGENT_IMPORTANT to 1,
    TaskPriority.URGENT_UNIMPORTANT to 2,
    TaskPriority.NOT_URGENT_UNIMPORTANT to 3,
)

fun compareTasks(left: TaskItem, right: TaskItem): Int {
    if (left.isPinned != right.isPinned) return if (left.isPinned) -1 else 1
    if (left.isDone != right.isDone) return if (left.isDone) 1 else -1
    val priorityDelta = (priorityOrder[left.priority] ?: 4) - (priorityOrder[right.priority] ?: 4)
    if (priorityDelta != 0) return priorityDelta
    if (left.position != right.position) return left.position - right.position
    val createdAtDelta = right.createdAt.compareTo(left.createdAt)
    if (createdAtDelta != 0) return createdAtDelta
    return right.id - left.id
}

fun filterTasksForView(
    tasks: List<TaskItem>,
    viewTarget: TaskViewTarget,
    todayString: String,
    tomorrowString: String,
): List<TaskItem> = when (viewTarget) {
    TaskViewTarget.All -> tasks
    TaskViewTarget.Calendar -> tasks
    TaskViewTarget.Today -> tasks.filter { it.dueDate != null && it.dueDate <= todayString }
    TaskViewTarget.Tomorrow -> tasks.filter { it.dueDate == tomorrowString }
    TaskViewTarget.Inbox -> tasks.filter { it.dueDate == null }
    is TaskViewTarget.ListView -> tasks.filter { it.listId == viewTarget.listId }
}

fun buildTaskSectionCollapseKey(groupKey: String, sectionId: TaskSectionId): String = "$groupKey:${sectionId.wire}"

fun isDueDateOverdue(dueDate: String?, todayString: String): Boolean {
    val today = parseLocalDateString(todayString) ?: return false
    val due = dueDate?.let(::parseLocalDateString) ?: return false
    return due.isBefore(today)
}

fun isTaskOverdue(task: BaseTask, todayString: String): Boolean = !task.isDone && isDueDateOverdue(task.dueDate, todayString)

fun classifyTaskDueDateGroup(dueDate: String?, todayString: String): AllTaskGroupId {
    if (dueDate == null) return AllTaskGroupId.NO_DATE
    if (isDueDateOverdue(dueDate, todayString)) return AllTaskGroupId.OVERDUE

    val today = parseLocalDateString(todayString) ?: return AllTaskGroupId.LATER
    val due = parseLocalDateString(dueDate) ?: return AllTaskGroupId.LATER
    if (due == today) return AllTaskGroupId.TODAY
    if (dueDate == getTomorrowDateString(today)) return AllTaskGroupId.TOMORROW
    if (due.isAfter(today.plusDays(1)) && !due.isAfter(today.plusDays(7))) return AllTaskGroupId.NEXT_7_DAYS
    return AllTaskGroupId.LATER
}

fun groupTasksByPriority(tasks: List<TaskItem>): List<TaskSection> {
    val sorted = tasks.sortedWith(::compareTasks)
    val sections = mutableListOf<TaskSection>()
    val pinned = sorted.filter { it.isPinned }
    val regular = sorted.filterNot { it.isPinned }

    if (pinned.isNotEmpty()) {
        sections += TaskSection(TaskSectionId.PINNED, TaskSectionId.PINNED.title, pinned)
    }

    listOf(
        TaskSectionId.URGENT_IMPORTANT,
        TaskSectionId.NOT_URGENT_IMPORTANT,
        TaskSectionId.URGENT_UNIMPORTANT,
        TaskSectionId.NOT_URGENT_UNIMPORTANT,
    ).forEach { sectionId ->
        val sectionTasks = regular.filter { it.priority == sectionId.toPriorityOrNull() }
        if (sectionTasks.isNotEmpty()) {
            sections += TaskSection(sectionId, sectionId.title, sectionTasks)
        }
    }

    return sections
}

fun groupTasksWithDone(tasks: List<TaskItem>): TaskCollection {
    val activeTasks = tasks.filterNot { it.isDone }.sortedWith(::compareTasks)
    val doneTasks = tasks.filter { it.isDone }.sortedWith(::compareTasks)
    return TaskCollection(
        sections = groupTasksByPriority(activeTasks),
        doneTasks = doneTasks,
        activeCount = activeTasks.size,
        doneCount = doneTasks.size,
        totalCount = tasks.size,
    )
}

fun filterVisibleTasks(tasks: List<TaskItem>, showCompleted: Boolean): List<TaskItem> =
    if (showCompleted) tasks else tasks.filterNot { it.isDone }

fun groupTasksForAllView(tasks: List<TaskItem>, todayString: String): List<AllTaskGroup> {
    val buckets = AllTaskGroupId.entries.associateWith { mutableListOf<TaskItem>() }
    tasks.sortedWith(::compareTasks).forEach { task ->
        buckets.getValue(classifyTaskDueDateGroup(task.dueDate, todayString)) += task
    }

    return AllTaskGroupId.entries.map { groupId ->
        val groupedTasks = buckets.getValue(groupId)
        val collection = groupTasksWithDone(groupedTasks)
        AllTaskGroup(
            id = groupId,
            title = groupId.title,
            emptyDescription = groupId.emptyDescription,
            tasks = groupedTasks,
            sections = collection.sections,
            doneTasks = collection.doneTasks,
            activeCount = collection.activeCount,
            doneCount = collection.doneCount,
            totalCount = collection.totalCount,
        )
    }
}

fun getVisibleTaskCollection(tasks: List<TaskItem>, showCompleted: Boolean): TaskCollection =
    groupTasksWithDone(filterVisibleTasks(tasks, showCompleted))

fun getVisibleAllTaskGroups(tasks: List<TaskItem>, todayString: String, showCompleted: Boolean): List<AllTaskGroup> {
    val groups = groupTasksForAllView(filterVisibleTasks(tasks, showCompleted), todayString)
    return if (showCompleted) groups else groups.filter { it.activeCount > 0 }
}

fun getVisibleTodayTaskGroups(tasks: List<TaskItem>, todayString: String, showCompleted: Boolean): List<AllTaskGroup> =
    getVisibleAllTaskGroups(tasks, todayString, showCompleted)
        .filter { it.id == AllTaskGroupId.OVERDUE || it.id == AllTaskGroupId.TODAY }

fun buildSidebarCounts(
    tasks: List<TaskItem>,
    listIds: List<Int>,
    todayString: String,
    tomorrowString: String,
): SidebarCounts {
    val listTaskCounts = listIds.associateWith { 0 }.toMutableMap()
    var all = 0
    var today = 0
    var tomorrow = 0
    var inbox = 0

    tasks.forEach { task ->
        if (task.isDone) return@forEach
        all += 1
        if (task.dueDate != null && task.dueDate <= todayString) today += 1
        if (task.dueDate == tomorrowString) tomorrow += 1
        if (task.dueDate == null) inbox += 1
        task.listId?.let { listId -> listTaskCounts[listId] = (listTaskCounts[listId] ?: 0) + 1 }
    }

    return SidebarCounts(
        all = all,
        today = today,
        tomorrow = tomorrow,
        inbox = inbox,
        listTaskCounts = listTaskCounts,
    )
}

fun taskMatchesTopLevelReorderScope(task: TaskItem, scope: TaskTopLevelReorderScope): Boolean {
    if (task.parentId != null || task.isDone) return false
    if (TaskSectionId.fromTask(task) != scope.sectionId) return false

    return when (scope) {
        is TaskTopLevelReorderScope.ListScope -> task.listId == scope.listId
        is TaskTopLevelReorderScope.InboxScope -> task.dueDate == null
        is TaskTopLevelReorderScope.DateScope -> task.dueDate == scope.targetDate
        is TaskTopLevelReorderScope.AllScope -> classifyTaskDueDateGroup(task.dueDate, scope.referenceDate) == scope.groupId
    }
}

fun getTopLevelTaskIdsForScope(tasks: List<TaskItem>, scope: TaskTopLevelReorderScope): List<Int> =
    tasks.filter { taskMatchesTopLevelReorderScope(it, scope) }.map { it.id }

fun buildTopLevelTaskReorderScopeForTask(
    task: TaskItem,
    viewTarget: TaskViewTarget,
    todayString: String,
    tomorrowString: String,
): TaskTopLevelReorderScope? {
    if (task.parentId != null || task.isDone) return null
    val sectionId = TaskSectionId.fromTask(task)

    return when (viewTarget) {
        TaskViewTarget.All -> TaskTopLevelReorderScope.AllScope(
            groupId = classifyTaskDueDateGroup(task.dueDate, todayString),
            referenceDate = todayString,
            sectionId = sectionId,
        )
        TaskViewTarget.Calendar -> TaskTopLevelReorderScope.AllScope(
            groupId = classifyTaskDueDateGroup(task.dueDate, todayString),
            referenceDate = todayString,
            sectionId = sectionId,
        )
        TaskViewTarget.Inbox ->
            if (task.dueDate == null) TaskTopLevelReorderScope.InboxScope(sectionId) else null
        TaskViewTarget.Today ->
            if (task.dueDate == todayString) {
                TaskTopLevelReorderScope.DateScope(
                    view = com.taskmanager.android.model.ViewMode.TODAY,
                    targetDate = todayString,
                    sectionId = sectionId,
                )
            } else null
        TaskViewTarget.Tomorrow ->
            if (task.dueDate == tomorrowString) {
                TaskTopLevelReorderScope.DateScope(
                    view = com.taskmanager.android.model.ViewMode.TOMORROW,
                    targetDate = tomorrowString,
                    sectionId = sectionId,
                )
            } else null
        is TaskViewTarget.ListView ->
            if (task.listId == viewTarget.listId) {
                TaskTopLevelReorderScope.ListScope(
                    listId = viewTarget.listId,
                    sectionId = sectionId,
                )
            } else null
    }
}

fun insertOrderedId(ids: List<Int>, insertedId: Int, placement: com.taskmanager.android.model.NewTaskPlacement): List<Int> =
    when (placement) {
        com.taskmanager.android.model.NewTaskPlacement.START -> listOf(insertedId) + ids
        com.taskmanager.android.model.NewTaskPlacement.END -> ids + insertedId
    }

fun insertOrderedIdRelative(
    ids: List<Int>,
    insertedId: Int,
    targetId: Int,
    direction: TaskInsertDirection,
): List<Int> {
    if (insertedId == targetId) {
        return ids
    }

    val targetIndex = ids.indexOf(targetId)
    if (targetIndex == -1) {
        return ids
    }

    val nextIds = ids.toMutableList()
    val insertIndex = if (direction == TaskInsertDirection.BEFORE) targetIndex else targetIndex + 1
    nextIds.add(insertIndex.coerceIn(0, nextIds.size), insertedId)
    return nextIds
}

fun moveOrderedIds(
    ids: List<Int>,
    movedId: Int,
    targetId: Int,
    direction: TaskInsertDirection,
): List<Int> {
    if (movedId == targetId) {
        return ids
    }

    val movedIndex = ids.indexOf(movedId)
    val targetIndex = ids.indexOf(targetId)
    if (movedIndex == -1 || targetIndex == -1) {
        return ids
    }

    val remainingIds = ids.filterNot { it == movedId }.toMutableList()
    val adjustedTargetIndex = remainingIds.indexOf(targetId)
    val insertIndex = if (direction == TaskInsertDirection.BEFORE) adjustedTargetIndex else adjustedTargetIndex + 1
    remainingIds.add(insertIndex.coerceIn(0, remainingIds.size), movedId)
    return remainingIds
}
