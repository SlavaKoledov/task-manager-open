package com.taskmanager.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class EditableSubtaskItem(
    val id: Long,
    val title: String,
    val isDone: Boolean,
)

@Composable
fun SubtaskDraftList(
    items: List<EditableSubtaskItem>,
    onAdd: (String) -> Unit,
    onUpdate: (Long, String) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onMoveUp: (Long) -> Unit,
    onMoveDown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newSubtaskTitle by remember(items.size) { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = item.isDone,
                        onCheckedChange = { onToggle(item.id) },
                    )
                    OutlinedTextField(
                        value = item.title,
                        onValueChange = { onUpdate(item.id, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Subtask") },
                    )
                    Column {
                        IconButton(enabled = index > 0, onClick = { onMoveUp(item.id) }) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(enabled = index < items.lastIndex, onClick = { onMoveDown(item.id) }) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete subtask")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newSubtaskTitle,
                onValueChange = { newSubtaskTitle = it },
                modifier = Modifier.weight(1f),
                label = { Text("New subtask") },
            )
            IconButton(
                onClick = {
                    if (newSubtaskTitle.isBlank()) return@IconButton
                    onAdd(newSubtaskTitle.trim())
                    newSubtaskTitle = ""
                },
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add subtask")
            }
        }
    }
}
