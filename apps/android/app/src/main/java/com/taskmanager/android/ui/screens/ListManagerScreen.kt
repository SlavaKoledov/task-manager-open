package com.taskmanager.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.taskmanager.android.model.ListItem

private data class ListEditorDraft(
    val id: Int? = null,
    val name: String = "",
    val color: String = "#2563EB",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListManagerScreen(
    lists: List<ListItem>,
    taskCounts: Map<Int, Int>,
    onBack: () -> Unit,
    onCreateList: (String, String) -> Unit,
    onUpdateList: (Int, String, String) -> Unit,
    onDeleteList: (Int) -> Unit,
    onReorderLists: (List<Int>) -> Unit,
) {
    var draft by remember { mutableStateOf<ListEditorDraft?>(null) }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage lists") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { draft = ListEditorDraft() }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create list")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(lists, key = { _, item -> item.id }) { index, list ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .background(
                                    color = runCatching { Color(android.graphics.Color.parseColor(list.color)) }.getOrDefault(MaterialTheme.colorScheme.primary),
                                    shape = CircleShape,
                                )
                                .padding(6.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = list.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${taskCounts[list.id] ?: 0} active tasks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(enabled = index > 0, onClick = {
                            val reorderedIds = lists.map(ListItem::id).toMutableList().apply {
                                add(index - 1, removeAt(index))
                            }
                            onReorderLists(reorderedIds)
                        }) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(enabled = index < lists.lastIndex, onClick = {
                            val reorderedIds = lists.map(ListItem::id).toMutableList().apply {
                                add(index + 1, removeAt(index))
                            }
                            onReorderLists(reorderedIds)
                        }) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        IconButton(onClick = {
                            draft = ListEditorDraft(id = list.id, name = list.name, color = list.color)
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { pendingDeleteId = list.id }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    draft?.let { currentDraft ->
        ListEditorDialog(
            draft = currentDraft,
            onDismiss = { draft = null },
            onSave = { nextDraft ->
                if (nextDraft.id == null) {
                    onCreateList(nextDraft.name.trim(), nextDraft.color)
                } else {
                    onUpdateList(nextDraft.id, nextDraft.name.trim(), nextDraft.color)
                }
                draft = null
            },
        )
    }

    pendingDeleteId?.let { listId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete list?") },
            text = { Text("Tasks in the list will stay in the system and become unassigned.") },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    onDeleteList(listId)
                    pendingDeleteId = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ListEditorDialog(
    draft: ListEditorDraft,
    onDismiss: () -> Unit,
    onSave: (ListEditorDraft) -> Unit,
) {
    var currentDraft by remember(draft) { mutableStateOf(draft) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.id == null) "Create list" else "Edit list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentDraft.name,
                    onValueChange = { currentDraft = currentDraft.copy(name = it) },
                    label = { Text("Name") },
                )
                OutlinedTextField(
                    value = currentDraft.color,
                    onValueChange = { currentDraft = currentDraft.copy(color = it) },
                    label = { Text("Color hex") },
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                enabled = currentDraft.name.isNotBlank(),
                onClick = { onSave(currentDraft) },
            ) {
                Text(if (draft.id == null) "Create" else "Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
