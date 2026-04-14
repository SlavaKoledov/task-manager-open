package com.taskmanager.android.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.domain.buildTaskCreatePayloadFromDraft
import com.taskmanager.android.domain.buildTaskDraft
import com.taskmanager.android.domain.buildTaskUpdatePayloadJson
import com.taskmanager.android.domain.getSubtaskProgressSummary
import com.taskmanager.android.domain.updateDraftDate
import com.taskmanager.android.domain.updateDraftRepeat
import com.taskmanager.android.domain.validateTaskDraft
import com.taskmanager.android.model.EditableSubtaskDraft
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskDraft
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskRepeat
import com.taskmanager.android.ui.components.EditableSubtaskItem
import com.taskmanager.android.ui.components.PriorityCheckbox
import com.taskmanager.android.ui.components.SubtaskDraftList
import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    task: TaskItem?,
    lists: List<ListItem>,
    editorContext: TaskEditorContext,
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
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var showRepeatUntilPicker by remember { mutableStateOf(false) }
    var initializedKey by remember { mutableStateOf<String?>(null) }
    val subtaskProgress = getSubtaskProgressSummary(
        done = if (task == null) createSubtasks.count(EditableSubtaskDraft::isDone) else task.subtasks.count { it.isDone },
        total = if (task == null) createSubtasks.size else task.subtasks.size,
    )

    val initializationKey = task?.id?.toString() ?: "create:${editorContext.viewTarget.mode}:${editorContext.groupId?.wire}:${editorContext.sectionId?.wire}"
    LaunchedEffect(initializationKey) {
        if (initializedKey == initializationKey) return@LaunchedEffect
        initializedKey = initializationKey
        draft = task?.let(::buildTaskDraft)
            ?: buildTaskDraft(
                context = editorContext,
                todayString = com.taskmanager.android.domain.getLocalDateString(),
                tomorrowString = com.taskmanager.android.domain.getTomorrowDateString(),
            )
        createSubtasks = emptyList()
        localSubtaskId = -1L
        errorMessage = null
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
                    modifier = Modifier.weight(1f),
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Repeat", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskRepeat.entries.forEach { repeat ->
                        FilterChip(
                            selected = draft.repeat == repeat,
                            onClick = { draft = updateDraftRepeat(draft, repeat) },
                            label = { Text(repeat.title) },
                        )
                    }
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
