package com.taskmanager.android.domain

import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.NewTaskPlacement
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.model.TaskInsertDirection
import com.taskmanager.android.model.TaskViewTarget
import com.taskmanager.android.testTaskItem
import org.junit.Test

class TaskGroupingTest {
    @Test
    fun `get visible all task groups hides empty groups when completed are hidden`() {
        val tasks = listOf(
            testTaskItem(
                id = 1,
                title = "Today active",
                dueDate = "2026-03-15",
                priority = TaskPriority.URGENT_IMPORTANT,
            ),
            testTaskItem(
                id = 2,
                title = "Tomorrow done",
                dueDate = "2026-03-16",
                isDone = true,
            ),
        )

        val hiddenCompletedGroups = getVisibleAllTaskGroups(
            tasks = tasks,
            todayString = "2026-03-15",
            showCompleted = false,
        )
        val visibleCompletedGroups = getVisibleAllTaskGroups(
            tasks = tasks,
            todayString = "2026-03-15",
            showCompleted = true,
        )

        assertThat(hiddenCompletedGroups.map { it.id }).containsExactly(AllTaskGroupId.TODAY)
        assertThat(visibleCompletedGroups.map { it.id }).containsAtLeast(AllTaskGroupId.TODAY, AllTaskGroupId.TOMORROW)
    }

    @Test
    fun `build top level reorder scope follows current view semantics`() {
        val task = testTaskItem(
            id = 11,
            title = "Scoped task",
            dueDate = "2026-03-16",
            listId = 7,
            priority = TaskPriority.NOT_URGENT_IMPORTANT,
        )

        val todayScope = buildTopLevelTaskReorderScopeForTask(
            task = task.copy(dueDate = "2026-03-15"),
            viewTarget = TaskViewTarget.Today,
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )
        val listScope = buildTopLevelTaskReorderScopeForTask(
            task = task,
            viewTarget = TaskViewTarget.ListView(7, "Personal"),
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )
        val allScope = buildTopLevelTaskReorderScopeForTask(
            task = task,
            viewTarget = TaskViewTarget.All,
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )

        assertThat(todayScope).isEqualTo(
            com.taskmanager.android.model.TaskTopLevelReorderScope.DateScope(
                view = com.taskmanager.android.model.ViewMode.TODAY,
                targetDate = "2026-03-15",
                sectionId = TaskSectionId.NOT_URGENT_IMPORTANT,
            ),
        )
        assertThat(listScope).isEqualTo(
            com.taskmanager.android.model.TaskTopLevelReorderScope.ListScope(
                listId = 7,
                sectionId = TaskSectionId.NOT_URGENT_IMPORTANT,
            ),
        )
        assertThat(allScope).isEqualTo(
            com.taskmanager.android.model.TaskTopLevelReorderScope.AllScope(
                groupId = AllTaskGroupId.TOMORROW,
                referenceDate = "2026-03-15",
                sectionId = TaskSectionId.NOT_URGENT_IMPORTANT,
            ),
        )
    }

    @Test
    fun `today view includes overdue tasks in a dedicated group`() {
        val groups = getVisibleTodayTaskGroups(
            tasks = listOf(
                testTaskItem(id = 1, title = "Overdue", dueDate = "2026-03-14"),
                testTaskItem(id = 2, title = "Today", dueDate = "2026-03-15"),
            ),
            todayString = "2026-03-15",
            showCompleted = true,
        )

        assertThat(groups.map { it.id }).containsExactly(AllTaskGroupId.OVERDUE, AllTaskGroupId.TODAY).inOrder()
        assertThat(groups.first().tasks.map { it.id }).containsExactly(1)
        assertThat(groups.last().tasks.map { it.id }).containsExactly(2)
    }

    @Test
    fun `group tasks with done sorts timed tasks before untimed tasks within sections`() {
        val grouped = groupTasksWithDone(
            listOf(
                testTaskItem(
                    id = 1,
                    title = "Untimed",
                    priority = TaskPriority.URGENT_IMPORTANT,
                    position = 0,
                ),
                testTaskItem(
                    id = 2,
                    title = "Late",
                    priority = TaskPriority.URGENT_IMPORTANT,
                    startTime = "11:00",
                    position = 1,
                ),
                testTaskItem(
                    id = 3,
                    title = "Early short",
                    priority = TaskPriority.URGENT_IMPORTANT,
                    startTime = "09:00",
                    endTime = "09:30",
                    position = 2,
                ),
                testTaskItem(
                    id = 4,
                    title = "Early long",
                    priority = TaskPriority.URGENT_IMPORTANT,
                    startTime = "09:00",
                    endTime = "10:00",
                    position = 3,
                ),
                testTaskItem(
                    id = 5,
                    title = "Done timed",
                    priority = TaskPriority.URGENT_IMPORTANT,
                    isDone = true,
                    startTime = "10:00",
                    position = 4,
                ),
                testTaskItem(
                    id = 6,
                    title = "Done untimed",
                    priority = TaskPriority.URGENT_IMPORTANT,
                    isDone = true,
                    position = 5,
                ),
            ),
        )

        assertThat(grouped.sections.single().tasks.map { it.id }).containsExactly(3, 4, 2, 1).inOrder()
        assertThat(grouped.doneTasks.map { it.id }).containsExactly(5, 6).inOrder()
    }

    @Test
    fun `task progress rounds instead of truncating`() {
        val summary = getSubtaskProgressSummary(done = 2, total = 3)

        assertThat(summary.done).isEqualTo(2)
        assertThat(summary.total).isEqualTo(3)
        assertThat(summary.percent).isEqualTo(67)
    }

    @Test
    fun `insert ordered id honors placement preference`() {
        assertThat(insertOrderedId(listOf(2, 3), 1, NewTaskPlacement.START)).containsExactly(1, 2, 3).inOrder()
        assertThat(insertOrderedId(listOf(1, 2), 3, NewTaskPlacement.END)).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `sidebar today count includes overdue tasks`() {
        val counts = buildSidebarCounts(
            tasks = listOf(
                testTaskItem(id = 1, title = "Overdue", dueDate = "2026-03-14"),
                testTaskItem(id = 2, title = "Today", dueDate = "2026-03-15"),
            ),
            listIds = emptyList(),
            todayString = "2026-03-15",
            tomorrowString = "2026-03-16",
        )

        assertThat(counts.today).isEqualTo(2)
    }

    @Test
    fun `relative insert and move helpers preserve intended ordering`() {
        assertThat(
            insertOrderedIdRelative(
                ids = listOf(10, 20, 30),
                insertedId = 99,
                targetId = 20,
                direction = TaskInsertDirection.BEFORE,
            ),
        ).containsExactly(10, 99, 20, 30).inOrder()

        assertThat(
            moveOrderedIds(
                ids = listOf(10, 20, 30, 40),
                movedId = 10,
                targetId = 30,
                direction = TaskInsertDirection.AFTER,
            ),
        ).containsExactly(20, 30, 10, 40).inOrder()
    }
}
