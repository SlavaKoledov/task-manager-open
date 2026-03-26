package com.taskmanager.android.model

import androidx.compose.runtime.Immutable

enum class TaskPriority(val wire: String, val title: String) {
    URGENT_IMPORTANT("urgent_important", "High"),
    NOT_URGENT_IMPORTANT("not_urgent_important", "Medium"),
    URGENT_UNIMPORTANT("urgent_unimportant", "Low"),
    NOT_URGENT_UNIMPORTANT("not_urgent_unimportant", "None"),
    ;

    companion object {
        fun fromWire(value: String): TaskPriority =
            entries.firstOrNull { it.wire == value } ?: NOT_URGENT_UNIMPORTANT
    }
}

enum class TaskRepeat(val wire: String, val title: String) {
    NONE("none", "No repeat"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly"),
    ;

    companion object {
        fun fromWire(value: String): TaskRepeat =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

enum class TaskSectionId(val wire: String, val title: String) {
    PINNED("pinned", "Pinned"),
    URGENT_IMPORTANT("urgent_important", "High"),
    NOT_URGENT_IMPORTANT("not_urgent_important", "Medium"),
    URGENT_UNIMPORTANT("urgent_unimportant", "Low"),
    NOT_URGENT_UNIMPORTANT("not_urgent_unimportant", "None"),
    ;

    fun toPriorityOrNull(): TaskPriority? = when (this) {
        PINNED -> null
        URGENT_IMPORTANT -> TaskPriority.URGENT_IMPORTANT
        NOT_URGENT_IMPORTANT -> TaskPriority.NOT_URGENT_IMPORTANT
        URGENT_UNIMPORTANT -> TaskPriority.URGENT_UNIMPORTANT
        NOT_URGENT_UNIMPORTANT -> TaskPriority.NOT_URGENT_UNIMPORTANT
    }

    companion object {
        fun fromWire(value: String): TaskSectionId =
            entries.firstOrNull { it.wire == value } ?: NOT_URGENT_UNIMPORTANT

        fun fromTask(task: TaskItem): TaskSectionId = if (task.isPinned) PINNED else fromPriority(task.priority)

        fun fromPriority(priority: TaskPriority): TaskSectionId = when (priority) {
            TaskPriority.URGENT_IMPORTANT -> URGENT_IMPORTANT
            TaskPriority.NOT_URGENT_IMPORTANT -> NOT_URGENT_IMPORTANT
            TaskPriority.URGENT_UNIMPORTANT -> URGENT_UNIMPORTANT
            TaskPriority.NOT_URGENT_UNIMPORTANT -> NOT_URGENT_UNIMPORTANT
        }
    }
}

enum class AllTaskGroupId(val wire: String, val title: String, val emptyDescription: String) {
    OVERDUE("overdue", "Overdue", "Nothing is overdue."),
    TODAY("today", "Today", "Nothing scheduled for today."),
    TOMORROW("tomorrow", "Tomorrow", "Nothing queued for tomorrow."),
    NEXT_7_DAYS("next_7_days", "Next 7 Days", "Nothing due in the next 7 days."),
    LATER("later", "Later", "Nothing scheduled later on."),
    NO_DATE("no_date", "No Date", "Everything here has a date."),
    ;

    companion object {
        fun fromWire(value: String): AllTaskGroupId =
            entries.firstOrNull { it.wire == value } ?: TODAY
    }
}

enum class NewTaskPlacement(val wire: String, val title: String) {
    START("start", "Add task to the beginning"),
    END("end", "Add task to the end"),
    ;

    companion object {
        fun fromWire(value: String?): NewTaskPlacement = if (value == START.wire) START else END
    }
}

enum class ViewMode {
    ALL,
    TODAY,
    TOMORROW,
    INBOX,
    LIST,
    CALENDAR,
}

sealed interface TaskViewTarget {
    val mode: ViewMode
    val title: String

    data object All : TaskViewTarget {
        override val mode = ViewMode.ALL
        override val title = "All"
    }

    data object Today : TaskViewTarget {
        override val mode = ViewMode.TODAY
        override val title = "Today"
    }

    data object Tomorrow : TaskViewTarget {
        override val mode = ViewMode.TOMORROW
        override val title = "Tomorrow"
    }

    data object Inbox : TaskViewTarget {
        override val mode = ViewMode.INBOX
        override val title = "Inbox"
    }

    data class ListView(
        val listId: Int,
        val listName: String?,
    ) : TaskViewTarget {
        override val mode = ViewMode.LIST
        override val title = listName ?: "List"
    }

    data object Calendar : TaskViewTarget {
        override val mode = ViewMode.CALENDAR
        override val title = "Calendar"
    }
}

sealed interface DescriptionBlock {
    val text: String

    data class Text(
        override val text: String = "",
    ) : DescriptionBlock

    data class Checkbox(
        override val text: String = "",
        val checked: Boolean = false,
    ) : DescriptionBlock
}

@Immutable
data class ListItem(
    val id: Int,
    val name: String,
    val color: String,
    val position: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Immutable
open class BaseTask(
    open val id: Int,
    open val title: String,
    open val description: String?,
    open val descriptionBlocks: List<DescriptionBlock>,
    open val dueDate: String?,
    open val reminderTime: String?,
    open val repeatUntil: String?,
    open val isDone: Boolean,
    open val isPinned: Boolean,
    open val priority: TaskPriority,
    open val repeat: TaskRepeat,
    open val parentId: Int?,
    open val position: Int,
    open val listId: Int?,
    open val createdAt: String,
    open val updatedAt: String,
)

@Immutable
data class TaskSubtask(
    override val id: Int,
    override val title: String,
    override val description: String?,
    override val descriptionBlocks: List<DescriptionBlock>,
    override val dueDate: String?,
    override val reminderTime: String?,
    override val repeatUntil: String?,
    override val isDone: Boolean,
    override val isPinned: Boolean,
    override val priority: TaskPriority,
    override val repeat: TaskRepeat,
    override val parentId: Int?,
    override val position: Int,
    override val listId: Int?,
    override val createdAt: String,
    override val updatedAt: String,
) : BaseTask(
    id = id,
    title = title,
    description = description,
    descriptionBlocks = descriptionBlocks,
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatUntil = repeatUntil,
    isDone = isDone,
    isPinned = isPinned,
    priority = priority,
    repeat = repeat,
    parentId = parentId,
    position = position,
    listId = listId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Immutable
data class TaskItem(
    override val id: Int,
    override val title: String,
    override val description: String?,
    override val descriptionBlocks: List<DescriptionBlock>,
    override val dueDate: String?,
    override val reminderTime: String?,
    override val repeatUntil: String?,
    override val isDone: Boolean,
    override val isPinned: Boolean,
    override val priority: TaskPriority,
    override val repeat: TaskRepeat,
    override val parentId: Int?,
    override val position: Int,
    override val listId: Int?,
    override val createdAt: String,
    override val updatedAt: String,
    val subtasks: List<TaskSubtask>,
) : BaseTask(
    id = id,
    title = title,
    description = description,
    descriptionBlocks = descriptionBlocks,
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatUntil = repeatUntil,
    isDone = isDone,
    isPinned = isPinned,
    priority = priority,
    repeat = repeat,
    parentId = parentId,
    position = position,
    listId = listId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

@Immutable
data class TaskSection(
    val id: TaskSectionId,
    val title: String,
    val tasks: List<TaskItem>,
)

@Immutable
data class TaskCollection(
    val sections: List<TaskSection>,
    val doneTasks: List<TaskItem>,
    val activeCount: Int,
    val doneCount: Int,
    val totalCount: Int,
)

@Immutable
data class AllTaskGroup(
    val id: AllTaskGroupId,
    val title: String,
    val emptyDescription: String,
    val tasks: List<TaskItem>,
    val sections: List<TaskSection>,
    val doneTasks: List<TaskItem>,
    val activeCount: Int,
    val doneCount: Int,
    val totalCount: Int,
)

@Immutable
data class SidebarCounts(
    val all: Int,
    val today: Int,
    val tomorrow: Int,
    val inbox: Int,
    val listTaskCounts: Map<Int, Int>,
)

@Immutable
data class AppPreferences(
    val showCompleted: Boolean = true,
    val newTaskPlacement: NewTaskPlacement = NewTaskPlacement.END,
    val dailyNotificationEnabled: Boolean = false,
    val dailyNotificationTime: String = "09:00",
    val baseUrl: String,
)

@Immutable
data class ActiveTaskMove(
    val taskId: Int,
    val parentId: Int?,
    val title: String,
    val hasSubtasks: Boolean,
)

@Immutable
data class TaskDraft(
    val title: String = "",
    val description: String = "",
    val dueDate: String = "",
    val reminderTime: String = "",
    val repeatUntil: String = "",
    val isDone: Boolean = false,
    val isPinned: Boolean = false,
    val priority: TaskPriority = TaskPriority.NOT_URGENT_UNIMPORTANT,
    val repeat: TaskRepeat = TaskRepeat.NONE,
    val listId: Int? = null,
)

@Immutable
data class EditableSubtaskDraft(
    val id: Long,
    val title: String,
    val isDone: Boolean = false,
    val position: Int,
)

@Immutable
data class TaskEditorContext(
    val viewTarget: TaskViewTarget,
    val groupId: AllTaskGroupId? = null,
    val sectionId: TaskSectionId? = null,
    val prefilledDueDate: String? = null,
)

sealed interface TaskTopLevelReorderScope {
    val sectionId: TaskSectionId

    data class ListScope(
        val listId: Int,
        override val sectionId: TaskSectionId,
    ) : TaskTopLevelReorderScope

    data class InboxScope(
        override val sectionId: TaskSectionId,
    ) : TaskTopLevelReorderScope

    data class DateScope(
        val view: ViewMode,
        val targetDate: String,
        override val sectionId: TaskSectionId,
    ) : TaskTopLevelReorderScope

    data class AllScope(
        val groupId: AllTaskGroupId,
        val referenceDate: String,
        override val sectionId: TaskSectionId,
    ) : TaskTopLevelReorderScope
}

enum class TaskInsertDirection {
    BEFORE,
    AFTER,
}
