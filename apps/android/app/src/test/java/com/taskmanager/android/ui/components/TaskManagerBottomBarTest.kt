package com.taskmanager.android.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.ui.theme.TaskManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskManagerBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bottom bar only shows task and calendar entries`() {
        var taskClicks = 0
        var calendarClicks = 0

        composeRule.setContent {
            TaskManagerTheme {
                TaskManagerBottomBar(
                    selectedDestination = TaskManagerBottomDestination.TASKS,
                    onTasksClick = { taskClicks += 1 },
                    onCalendarClick = { calendarClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Task").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Calendar").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertThat(taskClicks).isEqualTo(1)
            assertThat(calendarClicks).isEqualTo(1)
            assertThat(composeRule.onAllNodesWithText("All").fetchSemanticsNodes()).isEmpty()
            assertThat(composeRule.onAllNodesWithText("Today").fetchSemanticsNodes()).isEmpty()
            assertThat(composeRule.onAllNodesWithText("Tomorrow").fetchSemanticsNodes()).isEmpty()
            assertThat(composeRule.onAllNodesWithText("Inbox").fetchSemanticsNodes()).isEmpty()
        }
    }
}
