package com.taskmanager.android.ui.navigation

import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskViewTarget

object TaskManagerRoutes {
    const val ALL = "all"
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val INBOX = "inbox"
    const val CALENDAR = "calendar"
    const val LIST = "list/{listId}"
    const val MANAGE_LISTS = "lists/manage"
    const val SETTINGS = "settings"
    const val NOTIFICATIONS = "settings/notifications"
    const val EDIT_TASK = "task/{taskId}"
    const val CREATE_TASK = "task/create?mode={mode}&listId={listId}&groupId={groupId}&sectionId={sectionId}&dueDate={dueDate}"

    fun list(listId: Int): String = "list/$listId"

    fun editTask(taskId: Int): String = "task/$taskId"

    fun createTask(context: TaskEditorContext): String {
        val mode = context.viewTarget.mode.name.lowercase()
        val listId = (context.viewTarget as? TaskViewTarget.ListView)?.listId ?: -1
        val groupId = context.groupId?.wire ?: "none"
        val sectionId = context.sectionId?.wire ?: "none"
        val dueDate = context.prefilledDueDate ?: "none"
        return "task/create?mode=$mode&listId=$listId&groupId=$groupId&sectionId=$sectionId&dueDate=$dueDate"
    }
}
