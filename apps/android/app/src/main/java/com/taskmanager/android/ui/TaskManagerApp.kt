package com.taskmanager.android.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dagger.hilt.android.EntryPointAccessors
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.navigation.navArgument
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.taskmanager.android.data.notifications.TaskNotificationManager
import com.taskmanager.android.domain.buildSidebarCounts
import com.taskmanager.android.domain.parseLocalDateString
import com.taskmanager.android.model.AllTaskGroupId
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskPriority
import com.taskmanager.android.model.TaskViewTarget
import com.taskmanager.android.ui.components.AppDrawerContent
import com.taskmanager.android.ui.components.TaskManagerBottomBar
import com.taskmanager.android.ui.components.TaskManagerBottomDestination
import com.taskmanager.android.ui.navigation.TaskManagerRoutes
import com.taskmanager.android.ui.screens.CalendarScreen
import com.taskmanager.android.ui.screens.HomeScreen
import com.taskmanager.android.ui.screens.ListManagerScreen
import com.taskmanager.android.ui.screens.NotificationSettingsScreen
import com.taskmanager.android.ui.screens.SettingsScreen
import com.taskmanager.android.ui.screens.TaskEditorScreen
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun TaskManagerApp(
    viewModel: TaskManagerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notificationManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            NotificationManagerEntryPoint::class.java,
        ).notificationManager()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    var lastTaskRoute by rememberSaveable { mutableStateOf(TaskManagerRoutes.ALL) }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    LaunchedEffect(currentBackStackEntry) {
        when (currentBackStackEntry?.destination?.route) {
            TaskManagerRoutes.ALL,
            TaskManagerRoutes.TODAY,
            TaskManagerRoutes.TOMORROW,
            TaskManagerRoutes.INBOX,
            -> {
                lastTaskRoute = currentBackStackEntry?.destination?.route ?: TaskManagerRoutes.ALL
            }

            TaskManagerRoutes.LIST -> {
                val listId = currentBackStackEntry?.arguments?.getInt("listId")
                if (listId != null) {
                    lastTaskRoute = TaskManagerRoutes.list(listId)
                }
            }
        }
    }

    DisposableEffect(context, lifecycleOwner, viewModel) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.handleTimeContextChanged()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                Lifecycle.Event.ON_RESUME -> viewModel.handleTimeContextChanged()
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    NavHost(
        navController = navController,
        startDestination = TaskManagerRoutes.ALL,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(TaskManagerRoutes.ALL) {
            HomeRoute(TaskViewTarget.All, navController, viewModel, uiState, snackbarHostState, lastTaskRoute)
        }
        composable(TaskManagerRoutes.TODAY) {
            HomeRoute(TaskViewTarget.Today, navController, viewModel, uiState, snackbarHostState, lastTaskRoute)
        }
        composable(TaskManagerRoutes.TOMORROW) {
            HomeRoute(TaskViewTarget.Tomorrow, navController, viewModel, uiState, snackbarHostState, lastTaskRoute)
        }
        composable(TaskManagerRoutes.INBOX) {
            HomeRoute(TaskViewTarget.Inbox, navController, viewModel, uiState, snackbarHostState, lastTaskRoute)
        }
        composable(
            route = TaskManagerRoutes.LIST,
            arguments = listOf(navArgument("listId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getInt("listId") ?: return@composable
            val list = viewModel.findList(listId)
            HomeRoute(TaskViewTarget.ListView(listId, list?.name), navController, viewModel, uiState, snackbarHostState, lastTaskRoute)
        }
        composable(TaskManagerRoutes.CALENDAR) {
            CalendarRoute(
                navController = navController,
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                lastTaskRoute = lastTaskRoute,
            )
        }
        composable(TaskManagerRoutes.MANAGE_LISTS) {
            val scope = rememberCoroutineScope()
            val counts = buildSidebarCounts(
                tasks = uiState.tasks,
                listIds = uiState.lists.map { it.id },
                todayString = uiState.todayString,
                tomorrowString = uiState.tomorrowString,
            )
            ListManagerScreen(
                lists = uiState.lists,
                taskCounts = counts.listTaskCounts,
                onBack = { navController.popBackStack() },
                onCreateList = { name, color ->
                    scope.launch { viewModel.createList(name, color) }
                },
                onUpdateList = { listId, name, color ->
                    scope.launch { viewModel.updateList(listId, name, color) }
                },
                onDeleteList = { listId ->
                    scope.launch {
                        viewModel.deleteList(listId)
                        val currentBackStackRoute = navController.currentBackStackEntry?.destination?.route
                        if (currentBackStackRoute == TaskManagerRoutes.list(listId)) {
                            navController.navigate(TaskManagerRoutes.ALL)
                        }
                    }
                },
                onReorderLists = { listIds ->
                    scope.launch { viewModel.reorderLists(listIds) }
                },
            )
        }
        composable(TaskManagerRoutes.SETTINGS) {
            val scope = rememberCoroutineScope()
            SettingsScreen(
                preferences = uiState.preferences,
                onBack = { navController.popBackStack() },
                onSaveBaseUrl = { scope.launch { viewModel.setBaseUrl(it) } },
                onSetShowCompleted = { scope.launch { viewModel.setShowCompleted(it) } },
                onSetNewTaskPlacement = { scope.launch { viewModel.setNewTaskPlacement(it) } },
                onOpenNotifications = { navController.navigate(TaskManagerRoutes.NOTIFICATIONS) },
            )
        }
        composable(TaskManagerRoutes.NOTIFICATIONS) {
            val scope = rememberCoroutineScope()
            NotificationSettingsScreen(
                preferences = uiState.preferences,
                notificationManager = notificationManager,
                onBack = { navController.popBackStack() },
                onSetDailyNotificationEnabled = { scope.launch { viewModel.setDailyNotificationEnabled(it) } },
                onSetDailyNotificationTime = { scope.launch { viewModel.setDailyNotificationTime(it) } },
            )
        }
        composable(
            route = TaskManagerRoutes.EDIT_TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getInt("taskId") ?: return@composable
            val task = viewModel.findTask(taskId)
            TaskEditorScreen(
                task = task,
                lists = uiState.lists,
                editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
                onBack = { navController.popBackStack() },
                onCreateTask = { Result.success(Unit) },
                onUpdateTask = { id, payload -> viewModel.updateTask(id, payload) },
                onDeleteTask = { id -> viewModel.deleteTask(id) },
                onCreateSubtask = { parentId, title ->
                    viewModel.createTask(
                        com.taskmanager.android.data.api.ApiTaskCreatePayload(
                            title = title,
                            description = null,
                            descriptionBlocks = emptyList(),
                            dueDate = null,
                            reminderTime = null,
                            repeatUntil = null,
                            isDone = false,
                            isPinned = false,
                            priority = task?.priority?.wire ?: TaskPriority.NOT_URGENT_UNIMPORTANT.wire,
                            repeat = "none",
                            parentId = parentId,
                            listId = task?.listId,
                            subtasks = emptyList(),
                        ),
                        TaskEditorContext(viewTarget = TaskViewTarget.All),
                    )
                },
                onUpdateSubtask = { id, payload -> viewModel.updateTask(id, payload) },
                onToggleSubtask = { id -> viewModel.toggleTask(id) },
                onDeleteSubtask = { id -> viewModel.deleteTask(id) },
                onReorderSubtasks = { parentId, ids -> viewModel.reorderSubtasks(parentId, ids) },
            )
        }
        composable(
            route = TaskManagerRoutes.CREATE_TASK,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("listId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("groupId") { type = NavType.StringType; defaultValue = "none" },
                navArgument("sectionId") { type = NavType.StringType; defaultValue = "none" },
                navArgument("dueDate") { type = NavType.StringType; defaultValue = "none" },
            ),
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "all"
            val listId = backStackEntry.arguments?.getInt("listId")?.takeIf { it > 0 }
            val groupId = backStackEntry.arguments?.getString("groupId")?.takeIf { it != "none" }?.let(AllTaskGroupId::fromWire)
            val sectionId = backStackEntry.arguments?.getString("sectionId")?.takeIf { it != "none" }?.let(com.taskmanager.android.model.TaskSectionId::fromWire)
            val dueDate = backStackEntry.arguments?.getString("dueDate")?.takeIf { it != "none" }

            val viewTarget = when (mode) {
                "today" -> TaskViewTarget.Today
                "tomorrow" -> TaskViewTarget.Tomorrow
                "inbox" -> TaskViewTarget.Inbox
                "list" -> TaskViewTarget.ListView(listId ?: -1, viewModel.findList(listId ?: -1)?.name)
                "calendar" -> TaskViewTarget.Calendar
                else -> TaskViewTarget.All
            }

            TaskEditorScreen(
                task = null,
                lists = uiState.lists,
                editorContext = TaskEditorContext(viewTarget = viewTarget, groupId = groupId, sectionId = sectionId, prefilledDueDate = dueDate),
                onBack = { navController.popBackStack() },
                onCreateTask = { payload ->
                    viewModel.createTask(
                        payload,
                        TaskEditorContext(
                            viewTarget = viewTarget,
                            groupId = groupId,
                            sectionId = sectionId,
                            prefilledDueDate = dueDate,
                        ),
                    )
                },
                onUpdateTask = { _, _ -> Result.success(Unit) },
                onDeleteTask = { Result.success(Unit) },
                onCreateSubtask = { _, _ -> Result.success(Unit) },
                onUpdateSubtask = { _, _ -> Result.success(Unit) },
                onToggleSubtask = { Result.success(Unit) },
                onDeleteSubtask = { Result.success(Unit) },
                onReorderSubtasks = { _, _ -> Result.success(Unit) },
            )
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NotificationManagerEntryPoint {
    fun notificationManager(): TaskNotificationManager
}

private fun navigateToPrimaryDestination(navController: NavHostController, route: String) {
    navController.navigate(route, navOptions {
        launchSingleTop = true
        restoreState = true
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoute(
    viewTarget: TaskViewTarget,
    navController: NavHostController,
    viewModel: TaskManagerViewModel,
    uiState: TaskManagerUiState,
    snackbarHostState: SnackbarHostState,
    lastTaskRoute: String,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                lists = uiState.lists,
                tasks = uiState.tasks,
                todayString = uiState.todayString,
                tomorrowString = uiState.tomorrowString,
                currentViewTarget = viewTarget,
                onSelectView = { target ->
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(
                        when (target) {
                            TaskViewTarget.All -> TaskManagerRoutes.ALL
                            TaskViewTarget.Today -> TaskManagerRoutes.TODAY
                            TaskViewTarget.Tomorrow -> TaskManagerRoutes.TOMORROW
                            TaskViewTarget.Inbox -> TaskManagerRoutes.INBOX
                            is TaskViewTarget.ListView -> TaskManagerRoutes.list(target.listId)
                            TaskViewTarget.Calendar -> TaskManagerRoutes.CALENDAR
                        },
                    )
                },
                onManageLists = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(TaskManagerRoutes.MANAGE_LISTS)
                },
                onOpenSettings = {
                    coroutineScope.launch { drawerState.close() }
                    navController.navigate(TaskManagerRoutes.SETTINGS)
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(viewTarget.title) },
                    navigationIcon = {
                        IconButton(onClick = {
                            coroutineScope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        SyncActionButton(
                            uiState = uiState,
                            onClick = { viewModel.refresh() },
                        )
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Display options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (uiState.preferences.showCompleted) "Hide completed" else "Show completed") },
                                onClick = {
                                    menuExpanded = false
                                    coroutineScope.launch { viewModel.setShowCompleted(!uiState.preferences.showCompleted) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Manage lists") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(TaskManagerRoutes.MANAGE_LISTS)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    navController.navigate(TaskManagerRoutes.SETTINGS)
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                TaskManagerBottomBar(
                    selectedDestination = TaskManagerBottomDestination.TASKS,
                    onTasksClick = { navigateToPrimaryDestination(navController, lastTaskRoute) },
                    onCalendarClick = { navigateToPrimaryDestination(navController, TaskManagerRoutes.CALENDAR) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(
                            TaskManagerRoutes.createTask(
                                TaskEditorContext(
                                    viewTarget = viewTarget,
                                    groupId = if (viewTarget == TaskViewTarget.All) uiState.selectedAllGroup else null,
                                ),
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create task")
                }
            },
        ) { paddingValues ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
                HomeScreen(
                    viewTarget = viewTarget,
                    uiState = uiState,
                    onSelectAllGroup = viewModel::setSelectedAllGroup,
                    onCreateTask = { context -> navController.navigate(TaskManagerRoutes.createTask(context)) },
                    onEditTask = { task -> navController.navigate(TaskManagerRoutes.editTask(task.id)) },
                    onToggleTask = { task ->
                        coroutineScope.launch { viewModel.toggleTask(task.id) }
                    },
                    onToggleSubtask = { subtask ->
                        coroutineScope.launch { viewModel.toggleTask(subtask.id) }
                    },
                    onToggleSection = viewModel::toggleSectionCollapse,
                    onToggleTaskSubtasks = viewModel::toggleTaskSubtasks,
                    onToggleExpandedSubtaskPreview = viewModel::toggleExpandedSubtaskPreview,
                    onStartTaskMove = viewModel::startTaskMove,
                    onCancelTaskMove = viewModel::cancelTaskMove,
                    onReorderTasks = { scope, ids ->
                        coroutineScope.launch { viewModel.reorderTopLevelTasks(scope, ids) }
                    },
                    onMoveTaskToParent = { taskId, parentTaskId, orderedIds ->
                        coroutineScope.launch { viewModel.moveTaskToParent(taskId, parentTaskId, orderedIds) }
                    },
                    onMoveTaskToScope = { taskId, scope, orderedIds ->
                        coroutineScope.launch { viewModel.moveTaskToScope(taskId, scope, orderedIds) }
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarRoute(
    navController: NavHostController,
    uiState: TaskManagerUiState,
    snackbarHostState: SnackbarHostState,
    lastTaskRoute: String,
) {
    val todayDate = remember(uiState.todayString) { parseLocalDateString(uiState.todayString) ?: LocalDate.now() }
    var selectedDateString by rememberSaveable { mutableStateOf(uiState.todayString) }
    var visibleMonthString by rememberSaveable { mutableStateOf(YearMonth.from(todayDate).toString()) }
    val selectedDate = remember(selectedDateString) { parseLocalDateString(selectedDateString) ?: todayDate }
    val visibleMonth = remember(visibleMonthString) { runCatching { YearMonth.parse(visibleMonthString) }.getOrElse { YearMonth.from(selectedDate) } }
    val visibleTasks = remember(uiState.tasks, uiState.preferences.showCompleted) {
        if (uiState.preferences.showCompleted) {
            uiState.tasks
        } else {
            uiState.tasks.filterNot { it.isDone }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            TaskManagerBottomBar(
                selectedDestination = TaskManagerBottomDestination.CALENDAR,
                onTasksClick = { navigateToPrimaryDestination(navController, lastTaskRoute) },
                onCalendarClick = { navigateToPrimaryDestination(navController, TaskManagerRoutes.CALENDAR) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(
                        TaskManagerRoutes.createTask(
                            TaskEditorContext(
                                viewTarget = TaskViewTarget.Calendar,
                                prefilledDueDate = selectedDateString,
                            ),
                        ),
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Create task")
            }
        },
    ) { paddingValues ->
        CalendarScreen(
            tasks = visibleTasks,
            lists = uiState.lists,
            todayString = uiState.todayString,
            visibleMonth = visibleMonth,
            selectedDate = selectedDate,
            onSelectDate = { nextDate ->
                selectedDateString = nextDate.toString()
                visibleMonthString = YearMonth.from(nextDate).toString()
            },
            onChangeMonth = { nextMonth ->
                visibleMonthString = nextMonth.toString()
                selectedDateString = nextMonth.atDay(minOf(selectedDate.dayOfMonth, nextMonth.lengthOfMonth())).toString()
            },
            onOpenTask = { task -> navController.navigate(TaskManagerRoutes.editTask(task.id)) },
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
internal fun SyncActionButton(
    uiState: TaskManagerUiState,
    onClick: () -> Unit,
) {
    val tint = when (uiState.syncVisualState) {
        com.taskmanager.android.data.sync.TaskSyncVisualState.FAILED -> MaterialTheme.colorScheme.error
        com.taskmanager.android.data.sync.TaskSyncVisualState.PENDING -> MaterialTheme.colorScheme.primary
        com.taskmanager.android.data.sync.TaskSyncVisualState.SUCCESS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val syncContentDescription = when (uiState.syncVisualState) {
        com.taskmanager.android.data.sync.TaskSyncVisualState.SYNCING -> "Syncing tasks"
        com.taskmanager.android.data.sync.TaskSyncVisualState.PENDING ->
            if (uiState.pendingSyncCount == 1) "One pending change. Sync now" else "${uiState.pendingSyncCount} pending changes. Sync now"
        com.taskmanager.android.data.sync.TaskSyncVisualState.FAILED -> "Sync failed. Retry sync"
        com.taskmanager.android.data.sync.TaskSyncVisualState.SUCCESS -> "Last sync succeeded. Sync again"
        com.taskmanager.android.data.sync.TaskSyncVisualState.IDLE -> "Sync tasks"
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = syncContentDescription
        },
    ) {
        BadgedBox(
            badge = {
                when {
                    uiState.hasPendingSync -> {
                        if (uiState.pendingSyncCount > 0) {
                            Badge {
                                Text(if (uiState.pendingSyncCount > 9) "9+" else uiState.pendingSyncCount.toString())
                            }
                        } else {
                            Badge()
                        }
                    }
                    uiState.syncVisualState == com.taskmanager.android.data.sync.TaskSyncVisualState.FAILED -> {
                        Badge(containerColor = MaterialTheme.colorScheme.error)
                    }
                }
            },
        ) {
            if (uiState.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(3.dp)
                        .size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = tint,
                )
            }
        }
    }
}
