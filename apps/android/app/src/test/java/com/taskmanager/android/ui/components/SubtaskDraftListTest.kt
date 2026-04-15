package com.taskmanager.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.ui.theme.TaskManagerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SubtaskDraftListTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `drag handle reorder updates item order`() {
        var items by mutableStateOf(
            listOf(
                EditableSubtaskItem(id = 1, title = "First", isDone = false),
                EditableSubtaskItem(id = 2, title = "Second", isDone = false),
                EditableSubtaskItem(id = 3, title = "Third", isDone = false),
            ),
        )

        composeRule.setContent {
            TaskManagerTheme {
                SubtaskDraftList(
                    items = items,
                    onAdd = {},
                    onUpdate = { _, _ -> },
                    onToggle = {},
                    onDelete = {},
                    onReorder = { orderedIds ->
                        items = orderedIds.map { orderedId -> items.first { it.id == orderedId } }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("subtask-drag-handle-1").performTouchInput {
            down(center)
            moveBy(Offset(0f, 420f))
            up()
        }

        composeRule.runOnIdle {
            assertThat(items.map(EditableSubtaskItem::id)).containsExactly(2L, 3L, 1L).inOrder()
        }
    }
}
