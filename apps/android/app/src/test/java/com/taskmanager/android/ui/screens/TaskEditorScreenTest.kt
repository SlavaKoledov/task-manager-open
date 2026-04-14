package com.taskmanager.android.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.testTaskSubtask
import com.taskmanager.android.ui.theme.TaskManagerTheme
import com.taskmanager.android.model.TaskEditorContext
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
}
