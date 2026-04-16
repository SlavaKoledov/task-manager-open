package com.taskmanager.android.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.testTaskSubtask
import com.taskmanager.android.ui.theme.TaskManagerTheme
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskCustomRepeatUnit
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.model.TaskViewTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskEditorScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `task editor always shows task subtasks`() {
        composeRule.setContent {
            TaskManagerTheme {
                TaskEditorScreen(
                    task = testTaskItem(
                        id = 41,
                        title = "Parent task",
                        subtasks = listOf(
                            testTaskSubtask(id = 101, title = "Write notes", position = 0),
                            testTaskSubtask(id = 102, title = "Share plan", position = 1),
                        ),
                    ),
                    lists = emptyList(),
                    editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
                    onBack = {},
                    onCreateTask = { Result.success(Unit) },
                    onUpdateTask = { _, _ -> Result.success(Unit) },
                    onDeleteTask = { _ -> Result.success(Unit) },
                    onCreateSubtask = { _, _ -> Result.success(Unit) },
                    onUpdateSubtask = { _, _ -> Result.success(Unit) },
                    onToggleSubtask = { _ -> Result.success(Unit) },
                    onDeleteSubtask = { _ -> Result.success(Unit) },
                    onReorderSubtasks = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onAllNodesWithText("Write notes").assertCountEquals(1)
        composeRule.onAllNodesWithText("Share plan").assertCountEquals(1)
    }

    @Test
    fun `task editor can configure custom repeat and show restored summary`() {
        composeRule.setContent {
            TaskManagerTheme {
                TaskEditorScreen(
                    task = testTaskItem(
                        id = 55,
                        title = "Recurring task",
                        dueDate = "2026-03-16",
                        repeat = TaskRepeat.CUSTOM,
                        repeatConfig = TaskCustomRepeatConfig(
                            interval = 2,
                            unit = TaskCustomRepeatUnit.WEEK,
                            weekdays = listOf(1, 3, 5),
                        ),
                    ),
                    lists = emptyList(),
                    editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
                    onBack = {},
                    onCreateTask = { Result.success(Unit) },
                    onUpdateTask = { _, _ -> Result.success(Unit) },
                    onDeleteTask = { _ -> Result.success(Unit) },
                    onCreateSubtask = { _, _ -> Result.success(Unit) },
                    onUpdateSubtask = { _, _ -> Result.success(Unit) },
                    onToggleSubtask = { _ -> Result.success(Unit) },
                    onDeleteSubtask = { _ -> Result.success(Unit) },
                    onReorderSubtasks = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onAllNodesWithText("Every 2 weeks on M, W, F").assertCountEquals(1)
        composeRule.onNodeWithTag("custom-repeat-weekday-2", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onAllNodesWithText("Every 2 weeks on M, T, W, F").assertCountEquals(1)
    }

    @Test
    fun `custom weekly repeat with no weekdays does not save`() {
        var createCalls = 0

        composeRule.setContent {
            TaskManagerTheme {
                TaskEditorScreen(
                    task = null,
                    lists = emptyList(),
                    editorContext = TaskEditorContext(
                        viewTarget = TaskViewTarget.All,
                        prefilledDueDate = "2026-03-16",
                    ),
                    onBack = {},
                    onCreateTask = {
                        createCalls += 1
                        Result.success(Unit)
                    },
                    onUpdateTask = { _, _ -> Result.success(Unit) },
                    onDeleteTask = { _ -> Result.success(Unit) },
                    onCreateSubtask = { _, _ -> Result.success(Unit) },
                    onUpdateSubtask = { _, _ -> Result.success(Unit) },
                    onToggleSubtask = { _ -> Result.success(Unit) },
                    onDeleteSubtask = { _ -> Result.success(Unit) },
                    onReorderSubtasks = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onNodeWithText("Task title").performTextInput("Custom weekly")
        composeRule.onNodeWithTag("task-repeat-custom", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("custom-repeat-unit-week", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("custom-repeat-weekday-1", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("task-editor-submit").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onAllNodesWithText("Choose at least one weekday for a custom weekly repeat.").assertCountEquals(1)
        composeRule.runOnIdle {
            assertThat(createCalls).isEqualTo(0)
        }
    }

    @Test
    fun `custom repeat interval can be cleared and empty save normalizes to one`() {
        var createdPayload: ApiTaskCreatePayload? = null

        composeRule.setContent {
            TaskManagerTheme {
                TaskEditorScreen(
                    task = null,
                    lists = emptyList(),
                    editorContext = TaskEditorContext(
                        viewTarget = TaskViewTarget.All,
                        prefilledDueDate = "2026-03-16",
                    ),
                    onBack = {},
                    onCreateTask = {
                        createdPayload = it
                        Result.success(Unit)
                    },
                    onUpdateTask = { _, _ -> Result.success(Unit) },
                    onDeleteTask = { _ -> Result.success(Unit) },
                    onCreateSubtask = { _, _ -> Result.success(Unit) },
                    onUpdateSubtask = { _, _ -> Result.success(Unit) },
                    onToggleSubtask = { _ -> Result.success(Unit) },
                    onDeleteSubtask = { _ -> Result.success(Unit) },
                    onReorderSubtasks = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onNodeWithText("Task title").performTextInput("Custom interval")
        composeRule.onNodeWithTag("task-repeat-custom", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("custom-repeat-interval", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("")) }

        composeRule.runOnIdle {
            val editableText = composeRule.onNodeWithTag("custom-repeat-interval", useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
                .getOrElse(SemanticsProperties.EditableText) { AnnotatedString("") }
                .text
            assertThat(editableText).isEmpty()
        }

        composeRule.onNodeWithTag("task-editor-submit").performSemanticsAction(SemanticsActions.OnClick)

        composeRule.runOnIdle {
            assertThat(createdPayload?.repeatConfig?.interval).isEqualTo(1)
        }
    }

    @Test
    fun `task editor restores start and end time chips for timed tasks`() {
        composeRule.setContent {
            TaskManagerTheme {
                TaskEditorScreen(
                    task = testTaskItem(
                        id = 88,
                        title = "Timed task",
                        dueDate = "2026-03-16",
                        startTime = "09:00",
                        endTime = "10:30",
                    ),
                    lists = emptyList(),
                    editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
                    onBack = {},
                    onCreateTask = { Result.success(Unit) },
                    onUpdateTask = { _, _ -> Result.success(Unit) },
                    onDeleteTask = { _ -> Result.success(Unit) },
                    onCreateSubtask = { _, _ -> Result.success(Unit) },
                    onUpdateSubtask = { _, _ -> Result.success(Unit) },
                    onToggleSubtask = { _ -> Result.success(Unit) },
                    onDeleteSubtask = { _ -> Result.success(Unit) },
                    onReorderSubtasks = { _, _ -> Result.success(Unit) },
                )
            }
        }

        composeRule.onNodeWithText("Starts 09:00").assertIsDisplayed()
        composeRule.onNodeWithText("Ends 10:30").assertIsDisplayed()
        composeRule.onNodeWithText("09:00–10:30").assertIsDisplayed()
    }
}
