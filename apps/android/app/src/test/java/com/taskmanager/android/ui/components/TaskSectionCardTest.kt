package com.taskmanager.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.model.TaskSectionId
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.ui.theme.TaskManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskSectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `section tag collapses and expands the group`() {
        var collapsed by mutableStateOf(false)

        composeRule.setContent {
            TaskManagerTheme {
                TaskSectionCard(
                    title = "High",
                    sectionId = TaskSectionId.URGENT_IMPORTANT,
                    tasks = listOf(testTaskItem(id = 1, title = "Visible task", priority = com.taskmanager.android.model.TaskPriority.URGENT_IMPORTANT)),
                    collapsed = collapsed,
                    listById = emptyMap(),
                    collapsedTaskIds = emptySet(),
                    expandedSubtaskPreviewIds = emptySet(),
                    activeMoveTask = null,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                    onStartTaskMove = { _, _, _, _ -> },
                    onToggleCollapsed = { collapsed = !collapsed },
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                )
            }
        }

        composeRule.onNodeWithText("Visible task").assertIsDisplayed()
        composeRule.onNodeWithTag("section-tag-urgent_important").performClick()
        composeRule.onAllNodesWithText("Visible task").assertCountEquals(0)
        composeRule.onNodeWithTag("section-tag-urgent_important").performClick()
        composeRule.onNodeWithText("Visible task").assertIsDisplayed()
    }

    @Test
    fun `section header keeps collapse button count and plus aligned`() {
        composeRule.setContent {
            TaskManagerTheme {
                TaskSectionCard(
                    title = "High",
                    sectionId = TaskSectionId.URGENT_IMPORTANT,
                    tasks = listOf(
                        testTaskItem(id = 1, title = "First", priority = com.taskmanager.android.model.TaskPriority.URGENT_IMPORTANT),
                        testTaskItem(id = 2, title = "Second", priority = com.taskmanager.android.model.TaskPriority.URGENT_IMPORTANT),
                    ),
                    collapsed = false,
                    listById = emptyMap(),
                    collapsedTaskIds = emptySet(),
                    expandedSubtaskPreviewIds = emptySet(),
                    activeMoveTask = null,
                    onToggleTask = {},
                    onToggleSubtask = {},
                    onEditTask = {},
                    onToggleSubtasks = {},
                    onToggleExpandedSubtaskPreview = {},
                    onStartTaskMove = { _, _, _, _ -> },
                    onToggleCollapsed = {},
                    todayString = "2026-03-15",
                    tomorrowString = "2026-03-16",
                    onCreateTaskInSection = {},
                )
            }
        }

        composeRule.onNodeWithTag("section-collapse-urgent_important").assertIsDisplayed()
        composeRule.onNodeWithTag("section-count-urgent_important").assertIsDisplayed()
        composeRule.onNodeWithTag("section-create-urgent_important").assertIsDisplayed()

        composeRule.runOnIdle {
            val collapseLeft = composeRule.onNodeWithTag("section-collapse-urgent_important").fetchSemanticsNode().boundsInRoot.left
            val countLeft = composeRule.onNodeWithTag("section-count-urgent_important").fetchSemanticsNode().boundsInRoot.left
            val createLeft = composeRule.onNodeWithTag("section-create-urgent_important").fetchSemanticsNode().boundsInRoot.left

            assertThat(collapseLeft).isLessThan(countLeft)
            assertThat(countLeft).isLessThan(createLeft)
        }
    }
}
