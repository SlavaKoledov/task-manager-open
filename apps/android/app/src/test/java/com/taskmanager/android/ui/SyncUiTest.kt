package com.taskmanager.android.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.ui.screens.HomeScreen
import com.taskmanager.android.ui.theme.TaskManagerTheme
import com.taskmanager.android.data.sync.TaskSyncVisualState
import com.taskmanager.android.model.TaskViewTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SyncUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `sync button exposes pending sync state`() {
        var clicked = false

        composeRule.setContent {
            TaskManagerTheme {
                SyncActionButton(
                    uiState = TaskManagerUiState(
                        syncVisualState = TaskSyncVisualState.PENDING,
                        hasPendingSync = true,
                        pendingSyncCount = 2,
                    ),
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("2 pending changes. Sync now")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertThat(clicked).isTrue()
        }
    }

    @Test
    fun `home screen shows pending sync banner`() {
        composeRule.setContent {
            TaskManagerTheme {
                HomeScreen(
                    viewTarget = TaskViewTarget.All,
                    uiState = TaskManagerUiState(
                        syncVisualState = TaskSyncVisualState.PENDING,
                        hasPendingSync = true,
                        pendingSyncCount = 2,
                    ),
                    onSelectAllGroup = {},
                    onCreateTask = {},
                    onEditTask = {},
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onRequestDeleteTask = {},
                    onToggleSection = {},
                    onToggleTaskSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                    onStartTaskMove = { _, _, _, _ -> },
                    onCancelTaskMove = {},
                    onReorderTasks = { _, _ -> },
                    onMoveTaskToParent = { _, _, _ -> },
                    onMoveTaskToScope = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Pending changes").assertIsDisplayed()
    }

    @Test
    fun `sync status does not hide existing task content`() {
        composeRule.setContent {
            TaskManagerTheme {
                HomeScreen(
                    viewTarget = TaskViewTarget.Today,
                    uiState = TaskManagerUiState(
                        syncVisualState = TaskSyncVisualState.FAILED,
                        lastSyncError = "Network unavailable",
                        tasks = listOf(testTaskItem(id = 1, title = "Visible task", dueDate = "2026-03-15")),
                    ),
                    onSelectAllGroup = {},
                    onCreateTask = {},
                    onEditTask = {},
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onRequestDeleteTask = {},
                    onToggleSection = {},
                    onToggleTaskSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                    onStartTaskMove = { _, _, _, _ -> },
                    onCancelTaskMove = {},
                    onReorderTasks = { _, _ -> },
                    onMoveTaskToParent = { _, _, _ -> },
                    onMoveTaskToScope = { _, _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("Sync paused").assertIsDisplayed()
        composeRule.runOnIdle {
            assertThat(composeRule.onAllNodesWithText("Visible task", useUnmergedTree = true).fetchSemanticsNodes()).isNotEmpty()
        }
    }
}
