package com.taskmanager.android.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.domain.buildCalendarMonthDays
import com.taskmanager.android.domain.buildTaskCreatePayloadFromDraft
import com.taskmanager.android.domain.buildTaskDraft
import com.taskmanager.android.domain.buildTaskUpdatePayloadJson
import com.taskmanager.android.domain.ensureCustomRepeatConfig
import com.taskmanager.android.domain.formatTaskTimeRange
import com.taskmanager.android.domain.getSubtaskProgressSummary
import com.taskmanager.android.domain.getTaskRepeatSummary
import com.taskmanager.android.domain.normalizeCustomRepeatConfig
import com.taskmanager.android.domain.normalizeTaskTime
import com.taskmanager.android.domain.switchCustomRepeatUnit
import com.taskmanager.android.domain.updateDraftDate
import com.taskmanager.android.domain.updateDraftRepeat
import com.taskmanager.android.domain.validateTaskDraft
import com.taskmanager.android.domain.validateTaskTimeRange
import com.taskmanager.android.model.EditableSubtaskDraft
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskCustomRepeatConfig
import com.taskmanager.android.model.TaskCustomRepeatUnit
import com.taskmanager.android.model.TaskDraft
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.ui.components.EditableSubtaskItem
import com.taskmanager.android.ui.components.PriorityCheckbox
import com.taskmanager.android.ui.components.SubtaskDraftList
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    task: TaskItem?,
    lists: List<ListItem>,
    editorContext: TaskEditorContext,
    showDeleteConfirmationOnLaunch: Boolean = false,
    onBack: () -> Unit,
    onCreateTask: suspend (ApiTaskCreatePayload) -> Result<Unit>,
    onUpdateTask: suspend (Int, kotlinx.serialization.json.JsonObject) -> Result<Unit>,
    onDeleteTask: suspend (Int) -> Result<Unit>,
    onCreateSubtask: suspend (Int, String) -> Result<Unit>,
    onUpdateSubtask: suspend (Int, kotlinx.serialization.json.JsonObject) -> Result<Unit>,
    onToggleSubtask: suspend (Int) -> Result<Unit>,
    onDeleteSubtask: suspend (Int) -> Result<Unit>,
    onReorderSubtasks: suspend (Int, List<Int>) -> Result<Unit>,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var draft by remember { mutableStateOf(TaskDraft()) }
    var createSubtasks by remember { mutableStateOf(emptyList<EditableSubtaskDraft>()) }
    var localSubtaskId by remember { mutableLongStateOf(-1L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDueDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showRepeatUntilPicker by remember { mutableStateOf(false) }
    var initializedKey by remember { mutableStateOf<String?>(null) }
    var customYearlyVisibleMonth by remember { mutableStateOf(YearMonth.now()) }
    val subtaskProgress = getSubtaskProgressSummary(
        done = if (task == null) createSubtasks.count(EditableSubtaskDraft::isDone) else task.subtasks.count { it.isDone },
        total = if (task == null) createSubtasks.size else task.subtasks.size,
    )
    val customRepeatAnchorDate =
        draft.dueDate.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now()
    val customRepeatConfig =
        if (draft.repeat == TaskRepeat.CUSTOM) {
            ensureCustomRepeatConfig(draft.repeatConfig, customRepeatAnchorDate)
        } else {
            null
        }
    val taskTimeError = validateTaskTimeRange(draft.dueDate, draft.startTime, draft.endTime)

    val initializationKey = task?.id?.toString() ?: "create:${editorContext.viewTarget.mode}:${editorContext.groupId?.wire}:${editorContext.sectionId?.wire}"
    LaunchedEffect(initializationKey) {
        if (initializedKey == initializationKey) return@LaunchedEffect
        initializedKey = initializationKey
        val nextDraft = task?.let(::buildTaskDraft)
            ?: buildTaskDraft(
                context = editorContext,
                todayString = com.taskmanager.android.domain.getLocalDateString(),
                tomorrowString = com.taskmanager.android.domain.getTomorrowDateString(),
            )
        draft = nextDraft
        customYearlyVisibleMonth = initialCustomYearlyVisibleMonth(nextDraft.repeatConfig, nextDraft.dueDate)
        createSubtasks = emptyList()
        localSubtaskId = -1L
        errorMessage = null
    }

    LaunchedEffect(showDeleteConfirmationOnLaunch, task?.id) {
        if (showDeleteConfirmationOnLaunch && task != null) {
            showDeleteConfirmation = true
        }
    }

    if (showReminderTimePicker) {
        val (hour, minute) = draft.reminderTime
            .takeIf { it.isNotBlank() }
            ?.split(":")
            ?.let { parts -> (parts.getOrNull(0)?.toIntOrNull() ?: 9) to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
            ?: (9 to 0)
        DisposableEffect(showReminderTimePicker) {
            val dialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    draft = draft.copy(reminderTime = "%02d:%02d".format(selectedHour, selectedMinute))
                    showReminderTimePicker = false
                },
                hour,
                minute,
                true,
            )
            dialog.setOnDismissListener { showReminderTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    if (showStartTimePicker) {
        val (hour, minute) = draft.startTime
            .takeIf { it.isNotBlank() }
            ?.split(":")
            ?.let { parts -> (parts.getOrNull(0)?.toIntOrNull() ?: 9) to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
            ?: (9 to 0)
        DisposableEffect(showStartTimePicker) {
            val dialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    draft = draft.copy(startTime = "%02d:%02d".format(selectedHour, selectedMinute))
                    showStartTimePicker = false
                },
                hour,
                minute,
                true,
            )
            dialog.setOnDismissListener { showStartTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    if (showEndTimePicker) {
        val (hour, minute) = draft.endTime
            .takeIf { it.isNotBlank() }
            ?.split(":")
            ?.let { parts -> (parts.getOrNull(0)?.toIntOrNull() ?: 17) to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
            ?: draft.startTime
                .takeIf { it.isNotBlank() }
                ?.split(":")
                ?.let { parts -> (parts.getOrNull(0)?.toIntOrNull() ?: 17) to (parts.getOrNull(1)?.toIntOrNull() ?: 0) }
            ?: (17 to 0)
        DisposableEffect(showEndTimePicker) {
            val dialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    draft = draft.copy(endTime = "%02d:%02d".format(selectedHour, selectedMinute))
                    showEndTimePicker = false
                },
                hour,
                minute,
                true,
            )
            dialog.setOnDismissListener { showEndTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    if (showDueDatePicker) {
        val initialDate = draft.dueDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse) ?: LocalDate.now()
        DisposableEffect(showDueDatePicker) {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    draft = updateDraftDate(draft, LocalDate.of(year, month + 1, dayOfMonth).toString())
                    showDueDatePicker = false
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth,
            )
            dialog.setOnDismissListener { showDueDatePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    if (showRepeatUntilPicker) {
        val initialDate = draft.repeatUntil.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            ?: draft.dueDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)
            ?: LocalDate.now()
        DisposableEffect(showRepeatUntilPicker) {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    draft = draft.copy(repeatUntil = LocalDate.of(year, month + 1, dayOfMonth).toString())
                    showRepeatUntilPicker = false
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth,
            )
            draft.dueDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse)?.let { dueDate ->
                dialog.datePicker.minDate = dueDate.toEpochDay() * 24L * 60L * 60L * 1000L
            }
            dialog.setOnDismissListener { showRepeatUntilPicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (task == null) "Create task" else "Edit task") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete task")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val validationError = validateTaskDraft(draft)
                        if (validationError != null) {
                            errorMessage = validationError
                            return@Button
                        }
                        coroutineScope.launch {
                            val result = if (task == null) {
                                onCreateTask(buildTaskCreatePayloadFromDraft(draft, subtasks = createSubtasks))
                            } else {
                                onUpdateTask(task.id, buildTaskUpdatePayloadJson(draft))
                            }
                            result.onSuccess { onBack() }.onFailure {
                                errorMessage = it.message ?: "Failed to save task."
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("task-editor-submit"),
                ) {
                    Text(if (task == null) "Create task" else "Save changes")
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PriorityCheckbox(
                    checked = draft.isDone,
                    priority = draft.priority,
                    onCheckedChange = { draft = draft.copy(isDone = !draft.isDone) },
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Task title") },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Date", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.dueDate.isNotBlank(),
                        onClick = { showDueDatePicker = true },
                        label = { Text(if (draft.dueDate.isBlank()) "Pick due date" else draft.dueDate) },
                    )
                    if (draft.dueDate.isNotBlank()) {
                        FilterChip(
                            selected = draft.startTime.isNotBlank(),
                            onClick = { showStartTimePicker = true },
                            label = {
                                Text(
                                    if (draft.startTime.isBlank()) {
                                        "Start time"
                                    } else {
                                        "Starts ${draft.startTime}"
                                    },
                                )
                            },
                        )
                        FilterChip(
                            selected = draft.endTime.isNotBlank(),
                            onClick = { showEndTimePicker = true },
                            enabled = draft.startTime.isNotBlank(),
                            label = {
                                Text(
                                    if (draft.endTime.isBlank()) {
                                        "End time"
                                    } else {
                                        "Ends ${draft.endTime}"
                                    },
                                )
                            },
                        )
                        FilterChip(
                            selected = draft.reminderTime.isNotBlank(),
                            onClick = { showReminderTimePicker = true },
                            label = {
                                Text(
                                    if (draft.reminderTime.isBlank()) {
                                        "Reminder"
                                    } else {
                                        "Reminder ${draft.reminderTime}"
                                    },
                                )
                            },
                        )
                    }
                    if (draft.startTime.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = { draft = draft.copy(startTime = "", endTime = "") },
                            label = { Text("Clear time") },
                        )
                    }
                    if (draft.endTime.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = { draft = draft.copy(endTime = "") },
                            label = { Text("Clear end time") },
                        )
                    }
                    if (draft.reminderTime.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = { draft = draft.copy(reminderTime = "") },
                            label = { Text("Clear reminder") },
                        )
                    }
                    if (draft.dueDate.isNotBlank()) {
                        FilterChip(
                            selected = false,
                            onClick = { draft = updateDraftDate(draft, "") },
                            label = { Text("Clear date") },
                        )
                    }
                }
                if (draft.startTime.isNotBlank()) {
                    Text(
                        text = formatTaskTimeRange(draft.startTime, draft.endTime) ?: normalizeTaskTime(draft.startTime).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (taskTimeError != null) {
                    Text(
                        text = taskTimeError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repeat", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRepeat.entries.forEach { repeat ->
                        FilterChip(
                            selected = draft.repeat == repeat,
                            onClick = {
                                draft = updateDraftRepeat(draft, repeat)
                                if (repeat == TaskRepeat.CUSTOM) {
                                    customYearlyVisibleMonth = initialCustomYearlyVisibleMonth(
                                        draft.repeatConfig,
                                        draft.dueDate,
                                    )
                                }
                            },
                            modifier = Modifier.testTag("task-repeat-${repeat.wire}"),
                            label = { Text(repeat.title) },
                        )
                    }
                }
                if (draft.repeat != TaskRepeat.NONE) {
                    Text(
                        text = getTaskRepeatSummary(draft.repeat, draft.repeatConfig),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (customRepeatConfig != null) {
                    CustomRepeatEditor(
                        config = customRepeatConfig,
                        visibleYearMonth = customYearlyVisibleMonth,
                        onVisibleYearMonthChange = { customYearlyVisibleMonth = it },
                        onConfigChange = { nextConfig ->
                            draft = draft.copy(repeatConfig = normalizeCustomRepeatConfig(nextConfig))
                        },
                        anchorDate = customRepeatAnchorDate,
                    )
                }
                if (draft.repeat != TaskRepeat.NONE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = draft.repeatUntil.isNotBlank(),
                            onClick = { showRepeatUntilPicker = true },
                            label = { Text(if (draft.repeatUntil.isBlank()) "Recurring until" else draft.repeatUntil) },
                        )
                        if (draft.repeatUntil.isNotBlank()) {
                            FilterChip(
                                selected = false,
                                onClick = { draft = draft.copy(repeatUntil = "") },
                                label = { Text("Clear end date") },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Priority", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = draft.priority == priority,
                            onClick = { draft = draft.copy(priority = priority) },
                            label = { Text(priority.title) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("List", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.listId == null,
                        onClick = { draft = draft.copy(listId = null) },
                        label = { Text("Inbox") },
                    )
                    lists.forEach { list ->
                        FilterChip(
                            selected = draft.listId == list.id,
                            onClick = { draft = draft.copy(listId = list.id) },
                            label = { Text(list.name) },
                        )
                    }
                }
            }

            FilterChip(
                selected = draft.isPinned,
                onClick = { draft = draft.copy(isPinned = !draft.isPinned) },
                label = { Text(if (draft.isPinned) "Pinned" else "Pin task") },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Description", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                    label = { Text("Description") },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Subtasks", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        if (subtaskProgress.percent != null) {
                            append("${subtaskProgress.percent}% complete. ")
                        }
                        append(
                            if (task == null) {
                                "Add subtasks now and they will be created together with the task."
                            } else {
                                "Active subtasks can be reordered. Updates save immediately."
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (task == null) {
                    SubtaskDraftList(
                        items = createSubtasks.map { EditableSubtaskItem(it.id, it.title, it.isDone) },
                        onAdd = { title ->
                            createSubtasks = createSubtasks + EditableSubtaskDraft(
                                id = localSubtaskId--,
                                title = title,
                                position = createSubtasks.size,
                            )
                        },
                        onUpdate = { id, title ->
                            createSubtasks = createSubtasks.map { if (it.id == id) it.copy(title = title) else it }
                        },
                        onToggle = { id ->
                            createSubtasks = createSubtasks.map { if (it.id == id) it.copy(isDone = !it.isDone) else it }
                        },
                        onDelete = { id ->
                            createSubtasks = createSubtasks.filterNot { it.id == id }.mapIndexed { index, item -> item.copy(position = index) }
                        },
                        onReorder = { orderedIds ->
                            val positions = orderedIds.withIndex().associate { it.value to it.index }
                            createSubtasks = createSubtasks
                                .map { item -> item.copy(position = positions.getValue(item.id)) }
                                .sortedBy(EditableSubtaskDraft::position)
                        },
                    )
                } else {
                    val subtaskItems = task.subtasks.map { EditableSubtaskItem(it.id.toLong(), it.title, it.isDone) }
                    SubtaskDraftList(
                        items = subtaskItems,
                        onAdd = { title ->
                            coroutineScope.launch {
                                onCreateSubtask(task.id, title).onFailure {
                                    errorMessage = it.message ?: "Failed to create subtask."
                                }
                            }
                        },
                        onUpdate = { id, title ->
                            coroutineScope.launch {
                                onUpdateSubtask(
                                    id.toInt(),
                                    buildJsonObject { put("title", JsonPrimitive(title)) },
                                ).onFailure {
                                    errorMessage = it.message ?: "Failed to update subtask."
                                }
                            }
                        },
                        onToggle = { id ->
                            coroutineScope.launch {
                                onToggleSubtask(id.toInt()).onFailure {
                                    errorMessage = it.message ?: "Failed to toggle subtask."
                                }
                            }
                        },
                        onDelete = { id ->
                            coroutineScope.launch {
                                onDeleteSubtask(id.toInt()).onFailure {
                                    errorMessage = it.message ?: "Failed to delete subtask."
                                }
                            }
                        },
                        onReorder = { orderedIds ->
                            coroutineScope.launch {
                                onReorderSubtasks(task.id, orderedIds.map(Long::toInt)).onFailure {
                                    errorMessage = it.message ?: "Failed to reorder subtasks."
                                }
                            }
                        },
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showDeleteConfirmation && task != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete task?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch {
                        onDeleteTask(task.id)
                            .onSuccess { onBack() }
                            .onFailure { errorMessage = it.message ?: "Failed to delete task." }
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomRepeatEditor(
    config: TaskCustomRepeatConfig,
    visibleYearMonth: YearMonth,
    onVisibleYearMonthChange: (YearMonth) -> Unit,
    onConfigChange: (TaskCustomRepeatConfig) -> Unit,
    anchorDate: LocalDate,
) {
    var intervalInput by remember { mutableStateOf(config.interval.toString()) }
    var isEditingInterval by remember { mutableStateOf(false) }

    LaunchedEffect(config.interval, config.unit, isEditingInterval) {
        if (!isEditingInterval) {
            intervalInput = config.interval.toString()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Every", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = intervalInput,
                onValueChange = { value ->
                    val digitsOnly = value.filter(Char::isDigit)
                    if (digitsOnly.isBlank()) {
                        intervalInput = ""
                        onConfigChange(config.copy(interval = 1))
                        return@OutlinedTextField
                    }
                    val nextInterval = digitsOnly.toIntOrNull()?.takeIf { it >= 1 } ?: return@OutlinedTextField
                    intervalInput = digitsOnly
                    onConfigChange(config.copy(interval = nextInterval))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom-repeat-interval")
                    .onFocusChanged { focusState ->
                        isEditingInterval = focusState.isFocused
                    },
                label = { Text("Interval") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TaskCustomRepeatUnit.entries.forEach { unit ->
                FilterChip(
                    selected = config.unit == unit,
                    onClick = { onConfigChange(switchCustomRepeatUnit(config, unit, anchorDate)) },
                    modifier = Modifier.testTag("custom-repeat-unit-${unit.wire}"),
                    label = { Text(unit.wire.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        when (config.unit) {
            TaskCustomRepeatUnit.DAY -> {
                FilterChip(
                    selected = config.skipWeekends,
                    onClick = { onConfigChange(config.copy(skipWeekends = !config.skipWeekends)) },
                    label = { Text("Skip weekends") },
                )
            }

            TaskCustomRepeatUnit.WEEK -> {
                Text("Repeat on", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEKDAY_LETTERS.forEachIndexed { index, label ->
                        val weekday = index + 1
                        val selected = weekday in config.weekdays
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val nextWeekdays = if (selected) {
                                    config.weekdays.filterNot { it == weekday }
                                } else {
                                    (config.weekdays + weekday).distinct().sorted()
                                }
                                onConfigChange(config.copy(weekdays = nextWeekdays))
                            },
                            modifier = Modifier
                                .testTag("custom-repeat-weekday-$weekday")
                                .semantics { contentDescription = WEEKDAY_NAMES[index] },
                            label = { Text(label) },
                        )
                    }
                }
            }

            TaskCustomRepeatUnit.MONTH -> {
                Text("Day of month", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..31).forEach { day ->
                        FilterChip(
                            selected = config.monthDay == day,
                            onClick = { onConfigChange(config.copy(monthDay = day)) },
                            modifier = Modifier.testTag("custom-repeat-month-day-$day"),
                            label = { Text(day.toString()) },
                        )
                    }
                }
                FilterChip(
                    selected = config.skipWeekends,
                    onClick = { onConfigChange(config.copy(skipWeekends = !config.skipWeekends)) },
                    label = { Text("Skip weekends") },
                )
            }

            TaskCustomRepeatUnit.YEAR -> {
                YearlyRepeatCalendar(
                    config = config,
                    visibleYearMonth = visibleYearMonth,
                    onVisibleYearMonthChange = onVisibleYearMonthChange,
                    onConfigChange = onConfigChange,
                )
            }
        }
    }
}

@Composable
private fun YearlyRepeatCalendar(
    config: TaskCustomRepeatConfig,
    visibleYearMonth: YearMonth,
    onVisibleYearMonthChange: (YearMonth) -> Unit,
    onConfigChange: (TaskCustomRepeatConfig) -> Unit,
) {
    val selectedMonth = config.month ?: visibleYearMonth.monthValue
    val selectedDay = config.day ?: 1
    val selectedDate = LocalDate.of(2024, selectedMonth, minOf(selectedDay, YearMonth.of(2024, selectedMonth).lengthOfMonth()))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = visibleYearMonth.month.getDisplayName(TextStyle.FULL, Locale.US),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onVisibleYearMonthChange(visibleYearMonth.minusMonths(1)) }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Previous custom repeat month",
                    modifier = Modifier.testTag("custom-repeat-year-prev"),
                )
            }
            IconButton(onClick = { onVisibleYearMonthChange(visibleYearMonth.plusMonths(1)) }) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Next custom repeat month",
                    modifier = Modifier.testTag("custom-repeat-year-next"),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            WEEKDAY_LETTERS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        buildCalendarMonthDays(visibleYearMonth).chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val isSelected =
                        day.isCurrentMonth &&
                            day.date.monthValue == selectedDate.monthValue &&
                            day.date.dayOfMonth == selectedDate.dayOfMonth
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (day.isCurrentMonth) 0.55f else 0.25f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (day.isCurrentMonth) {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            },
                        ),
                        onClick = {
                            onConfigChange(
                                config.copy(
                                    month = day.date.monthValue,
                                    day = day.date.dayOfMonth,
                                ),
                            )
                            onVisibleYearMonthChange(YearMonth.of(2024, day.date.monthValue))
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                modifier = Modifier.testTag(
                                    "custom-repeat-year-day-${day.date.monthValue}-${day.date.dayOfMonth}",
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else if (day.isCurrentMonth) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val WEEKDAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val WEEKDAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun initialCustomYearlyVisibleMonth(
    repeatConfig: TaskCustomRepeatConfig?,
    dueDate: String,
): YearMonth {
    val normalized = normalizeCustomRepeatConfig(repeatConfig)
    if (normalized?.unit == TaskCustomRepeatUnit.YEAR && normalized.month != null) {
        return YearMonth.of(2024, normalized.month)
    }

    val anchorDate = dueDate.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now()
    return YearMonth.of(2024, anchorDate.monthValue)
}
