package com.taskmanager.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Toc
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taskmanager.android.R
import com.taskmanager.android.domain.buildSidebarCounts
import com.taskmanager.android.model.ListItem
import com.taskmanager.android.model.TaskItem
import com.taskmanager.android.model.TaskViewTarget

private data class DrawerDestination(
    val title: String,
    val icon: ImageVector,
    val count: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun AppDrawerContent(
    lists: List<ListItem>,
    tasks: List<TaskItem>,
    todayString: String,
    tomorrowString: String,
    currentViewTarget: TaskViewTarget,
    onSelectView: (TaskViewTarget) -> Unit,
    onManageLists: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = buildSidebarCounts(
        tasks = tasks,
        listIds = lists.map(ListItem::id),
        todayString = todayString,
        tomorrowString = tomorrowString,
    )

    val destinations = listOf(
        DrawerDestination(
            title = "All",
            icon = Icons.Outlined.Toc,
            count = counts.all,
            selected = currentViewTarget == TaskViewTarget.All,
            onClick = { onSelectView(TaskViewTarget.All) },
        ),
        DrawerDestination(
            title = "Today",
            icon = Icons.Outlined.Today,
            count = counts.today,
            selected = currentViewTarget == TaskViewTarget.Today,
            onClick = { onSelectView(TaskViewTarget.Today) },
        ),
        DrawerDestination(
            title = "Tomorrow",
            icon = Icons.Outlined.Timelapse,
            count = counts.tomorrow,
            selected = currentViewTarget == TaskViewTarget.Tomorrow,
            onClick = { onSelectView(TaskViewTarget.Tomorrow) },
        ),
        DrawerDestination(
            title = "Inbox",
            icon = Icons.Outlined.Inbox,
            count = counts.inbox,
            selected = currentViewTarget == TaskViewTarget.Inbox,
            onClick = { onSelectView(TaskViewTarget.Inbox) },
        ),
    )

    ModalDrawerSheet(
        modifier = modifier.fillMaxSize(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
            drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                    .padding(horizontal = 18.dp, vertical = 18.dp),
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.brand_logo_mark),
                    contentDescription = "App logo",
                    modifier = Modifier.size(72.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(destinations) { destination ->
                    DrawerItem(
                        title = destination.title,
                        icon = destination.icon,
                        count = destination.count,
                        selected = destination.selected,
                        color = null,
                        onClick = destination.onClick,
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                    )
                }

                item {
                    Text(
                        text = "Lists",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                }

                items(lists) { list ->
                    val selected = (currentViewTarget as? TaskViewTarget.ListView)?.listId == list.id
                    DrawerItem(
                        title = list.name,
                        icon = Icons.Outlined.List,
                        count = counts.listTaskCounts[list.id] ?: 0,
                        selected = selected,
                        color = runCatching { Color(android.graphics.Color.parseColor(list.color)) }.getOrNull(),
                        onClick = { onSelectView(TaskViewTarget.ListView(list.id, list.name)) },
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            )
            DrawerItem(
                title = "Manage lists",
                icon = Icons.Outlined.CheckCircle,
                count = lists.size,
                selected = false,
                color = null,
                onClick = onManageLists,
            )
            DrawerItem(
                title = "Settings",
                icon = Icons.Outlined.Settings,
                count = 0,
                selected = false,
                color = null,
                onClick = onOpenSettings,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DrawerItem(
    title: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    color: Color?,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                if (count > 0) {
                    Text(
                        text = count.toString(),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        icon = {
            if (color != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color, shape = MaterialTheme.shapes.small),
                )
            } else {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                )
            }
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            selectedIconColor = MaterialTheme.colorScheme.secondary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
