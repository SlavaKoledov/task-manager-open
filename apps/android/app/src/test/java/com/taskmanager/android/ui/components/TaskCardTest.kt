package com.taskmanager.android.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.testListItem
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.testTaskSubtask
import com.taskmanager.android.ui.theme.TaskManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `task card renders rounded progress percent and task metadata`() {
        val task = testTaskItem(
            id = 100,
            title = "Prepare launch checklist",
            listId = 7,
            subtasks = listOf(
                testTaskSubtask(id = 201, title = "Design review", isDone = true, position = 0),
                testTaskSubtask(id = 202, title = "QA signoff", isDone = true, position = 1),
                testTaskSubtask(id = 203, title = "Publish release notes", position = 2),
            ),
        )

        composeRule.setContent {
            TaskManagerTheme {
                TaskCard(
                    task = task,
                    list = testListItem(id = 7, name = "Launch"),
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                    isSubtasksCollapsed = false,
                    isExpandedSubtaskPreview = false,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onRequestDeleteTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                )
            }
        }

        composeRule.onNodeWithText("3 subtasks").assertIsDisplayed()
        composeRule.onNodeWithText("Launch").assertIsDisplayed()
    }

    @Test
    fun `task card expands hidden subtasks when plus more is tapped`() {
        var expanded by mutableStateOf(false)
        val task = testTaskItem(
            id = 101,
            title = "Deep task",
            subtasks = listOf(
                testTaskSubtask(id = 301, title = "One", position = 0),
                testTaskSubtask(id = 302, title = "Two", position = 1),
                testTaskSubtask(id = 303, title = "Three", position = 2),
                testTaskSubtask(id = 304, title = "Four", position = 3),
                testTaskSubtask(id = 305, title = "Five", position = 4),
            ),
        )

        composeRule.setContent {
            TaskManagerTheme {
                TaskCard(
                    task = task,
                    list = null,
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                    isSubtasksCollapsed = false,
                    isExpandedSubtaskPreview = expanded,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onRequestDeleteTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = { expanded = !expanded },
                )
            }
        }

        composeRule.onNodeWithText("+1 more").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Five").assertIsDisplayed()
    }

    @Test
    fun `task card exposes move affordances in move mode`() {
        var startedMoveTaskId: Int? = null
        var droppedInside = false
        var droppedAfterSubtaskId: Int? = null

        val task = testTaskItem(
            id = 77,
            title = "Parent task",
            subtasks = listOf(
                testTaskSubtask(id = 701, title = "First child", position = 0),
                testTaskSubtask(id = 702, title = "Second child", position = 1),
            ),
        )

        composeRule.setContent {
            TaskManagerTheme {
                TaskCard(
                    task = task,
                    list = null,
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                    isSubtasksCollapsed = false,
                    isExpandedSubtaskPreview = true,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onRequestDeleteTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                    onStartMoveTask = { startedMoveTaskId = it.id },
                    onStartMoveSubtask = {},
                    canDropInside = true,
                    onDropInside = { droppedInside = true },
                    onDropAfterSubtask = { droppedAfterSubtaskId = it.id },
                )
            }
        }

        composeRule.onNodeWithText("Parent task").performTouchInput { longClick() }
        composeRule.onNodeWithText("Drop into Parent task").performClick()
        composeRule.onNodeWithText("First child").performClick()

        composeRule.runOnIdle {
            assertThat(startedMoveTaskId).isEqualTo(77)
            assertThat(droppedInside).isTrue()
            assertThat(droppedAfterSubtaskId).isEqualTo(701)
        }
    }

    @Test
    fun `task card shows description indicators for task and subtask content`() {
        val task = testTaskItem(
            id = 102,
            title = "Task with notes",
            descriptionBlocks = listOf(com.taskmanager.android.model.DescriptionBlock.Text("Has details")),
            subtasks = listOf(
                testTaskSubtask(
                    id = 401,
                    title = "Subtask with notes",
                    descriptionBlocks = listOf(com.taskmanager.android.model.DescriptionBlock.Text("Nested details")),
                ),
            ),
        )

        composeRule.setContent {
            TaskManagerTheme {
                TaskCard(
                    task = task,
                    list = null,
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                    isSubtasksCollapsed = false,
                    isExpandedSubtaskPreview = true,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onRequestDeleteTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Has description")[0].assertIsDisplayed()
    }

    @Test
    fun `task card renders time badge before other metadata`() {
        val task = testTaskItem(
            id = 103,
            title = "Timed task",
            dueDate = "2026-03-15",
            startTime = "09:00",
            endTime = "10:30",
        )

        composeRule.setContent {
            TaskManagerTheme {
                TaskCard(
                    task = task,
                    list = null,
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                    isSubtasksCollapsed = true,
                    isExpandedSubtaskPreview = false,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onRequestDeleteTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                )
            }
        }

        composeRule.onNodeWithText("09:00–10:30").assertIsDisplayed()
    }
}
