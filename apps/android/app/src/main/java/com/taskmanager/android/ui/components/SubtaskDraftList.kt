package com.taskmanager.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

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
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newSubtaskTitle by remember(items.size) { mutableStateOf("") }
    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var draggingOffsetY by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            key(item.id) {
                val isDragging = draggingId == item.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            itemBounds[item.id] = coordinates.boundsInParent()
                        }
                        .offset {
                            IntOffset(
                                x = 0,
                                y = if (isDragging) draggingOffsetY.roundToInt() else 0,
                            )
                        }
                        .zIndex(if (isDragging) 1f else 0f),
                    shape = MaterialTheme.shapes.large,
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
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
                        IconButton(
                            modifier = Modifier
                                .testTag("subtask-drag-handle-${item.id}")
                                .pointerInput(item.id, items) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingId = item.id
                                            draggingOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            commitDragReorder(
                                                items = items,
                                                itemBounds = itemBounds,
                                                draggingId = draggingId,
                                                draggingOffsetY = draggingOffsetY,
                                                onReorder = onReorder,
                                            )
                                            draggingId = null
                                            draggingOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            draggingOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (draggingId != item.id) {
                                                return@detectDragGestures
                                            }
                                            change.consume()
                                            draggingOffsetY += dragAmount.y
                                        },
                                    )
                                },
                            onClick = {},
                        ) {
                            Icon(Icons.Outlined.DragHandle, contentDescription = "Reorder subtask")
                        }
                        IconButton(onClick = { onDelete(item.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete subtask")
                        }
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

private fun commitDragReorder(
    items: List<EditableSubtaskItem>,
    itemBounds: SnapshotStateMap<Long, Rect>,
    draggingId: Long?,
    draggingOffsetY: Float,
    onReorder: (List<Long>) -> Unit,
) {
    val activeId = draggingId ?: return
    val orderedIds = items.map(EditableSubtaskItem::id)
    val fromIndex = orderedIds.indexOf(activeId)
    val activeBounds = itemBounds[activeId] ?: return
    if (fromIndex == -1) {
        return
    }

    val draggedCenterY = activeBounds.center.y + draggingOffsetY
    val targetIndex = resolveDropIndex(orderedIds, itemBounds, draggedCenterY)
    if (targetIndex == -1 || targetIndex == fromIndex) {
        return
    }

    val reorderedIds = orderedIds.toMutableList().apply {
        add(targetIndex, removeAt(fromIndex))
    }
    if (reorderedIds != orderedIds) {
        onReorder(reorderedIds)
    }
}

private fun resolveDropIndex(
    orderedIds: List<Long>,
    itemBounds: SnapshotStateMap<Long, Rect>,
    draggedCenterY: Float,
): Int {
    val boundsByOrder = orderedIds.mapNotNull { id -> itemBounds[id]?.let { bounds -> id to bounds } }
    if (boundsByOrder.isEmpty()) {
        return -1
    }

    boundsByOrder.forEachIndexed { index, (_, bounds) ->
        if (draggedCenterY < bounds.center.y) {
            return index
        }
    }

    return boundsByOrder.lastIndex
}
