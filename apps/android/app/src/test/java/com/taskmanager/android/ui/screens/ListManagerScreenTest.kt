package com.taskmanager.android.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.testListItem
import com.taskmanager.android.ui.theme.TaskManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ListManagerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `moving a list up emits reordered ids`() {
        var reorderedIds: List<Int>? = null

        composeRule.setContent {
            TaskManagerTheme {
                ListManagerScreen(
                    lists = listOf(
                        testListItem(id = 1, name = "Inbox"),
                        testListItem(id = 2, name = "Work", position = 1),
                        testListItem(id = 3, name = "Errands", position = 2),
                    ),
                    taskCounts = mapOf(1 to 4, 2 to 7, 3 to 1),
                    onBack = {},
                    onCreateList = { _, _ -> },
                    onUpdateList = { _, _, _ -> },
                    onDeleteList = {},
                    onReorderLists = { reorderedIds = it },
                )
            }
        }

        composeRule.onNodeWithText("Manage lists").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Move up")[1].assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertThat(reorderedIds).containsExactly(2, 1, 3).inOrder()
        }
    }
}
