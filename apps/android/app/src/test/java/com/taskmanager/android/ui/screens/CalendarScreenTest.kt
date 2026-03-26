package com.taskmanager.android.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.testListItem
import com.taskmanager.android.testTaskItem
import com.taskmanager.android.ui.theme.TaskManagerTheme
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `calendar screen updates selected date content`() {
        composeRule.setContent {
            TaskManagerTheme {
                var selectedDate by remember { mutableStateOf(LocalDate.parse("2026-03-15")) }
                var visibleMonth by remember { mutableStateOf(YearMonth.of(2026, 3)) }

                CalendarScreen(
                    tasks = listOf(
                        testTaskItem(id = 1, title = "Gym", dueDate = "2026-03-15", listId = 7),
                        testTaskItem(id = 2, title = "Review", dueDate = "2026-03-16", reminderTime = "09:00"),
                    ),
                    lists = listOf(testListItem(id = 7, name = "Health", color = "#FF8A00")),
                    todayString = "2026-03-15",
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                    onChangeMonth = { visibleMonth = it },
                    onOpenTask = {},
                )
            }
        }

        composeRule.onNodeWithTag("calendar-day-2026-03-16").performClick()
        composeRule.runOnIdle {
            assertThat(composeRule.onAllNodesWithText("MAR 16").fetchSemanticsNodes()).isNotEmpty()
        }
    }

    @Test
    fun `calendar screen shows markers for dates with task occurrences`() {
        composeRule.setContent {
            TaskManagerTheme {
                var selectedDate by remember { mutableStateOf(LocalDate.parse("2026-03-15")) }
                var visibleMonth by remember { mutableStateOf(YearMonth.of(2026, 3)) }

                CalendarScreen(
                    tasks = listOf(
                        testTaskItem(
                            id = 1,
                            title = "Gym",
                            dueDate = "2026-03-15",
                            repeat = TaskRepeat.DAILY,
                            repeatUntil = "2026-03-18",
                        ),
                    ),
                    lists = emptyList(),
                    todayString = "2026-03-15",
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    onSelectDate = { selectedDate = it },
                    onChangeMonth = { visibleMonth = it },
                    onOpenTask = {},
                )
            }
        }

        composeRule.runOnIdle {
            assertThat(composeRule.onAllNodesWithTag("calendar-markers-2026-03-15", useUnmergedTree = true).fetchSemanticsNodes()).isNotEmpty()
            assertThat(composeRule.onAllNodesWithTag("calendar-markers-2026-03-18", useUnmergedTree = true).fetchSemanticsNodes()).isNotEmpty()
        }
    }
}
