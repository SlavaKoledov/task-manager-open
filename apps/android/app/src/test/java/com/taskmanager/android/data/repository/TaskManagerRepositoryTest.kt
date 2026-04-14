package com.taskmanager.android.data.repository

import android.app.AlarmManager
import android.content.Context
import androidx.room.withTransaction
import androidx.room.Room
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.taskmanager.android.data.api.ApiListItem
import com.taskmanager.android.data.api.ApiServiceFactory
import com.taskmanager.android.data.api.ApiSubtaskReorderPayload
import com.taskmanager.android.data.api.ApiTask
import com.taskmanager.android.data.api.ApiTaskCreatePayload
import com.taskmanager.android.data.local.LocalCacheMapper
import com.taskmanager.android.data.local.TaskManagerDao
import com.taskmanager.android.data.local.TaskManagerDatabase
import com.taskmanager.android.data.notifications.TaskNotificationManager
import com.taskmanager.android.data.preferences.AppPreferencesStore
import com.taskmanager.android.data.sync.TaskSyncScheduler
import com.taskmanager.android.model.NewTaskPlacement
import com.taskmanager.android.model.TaskEditorContext
import com.taskmanager.android.model.TaskViewTarget
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TaskManagerRepositoryTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var dispatcher: TaskApiDispatcher
    private lateinit var preferencesStore: AppPreferencesStore
    private lateinit var json: Json
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var mapper: LocalCacheMapper
    private lateinit var database: TaskManagerDatabase
    private lateinit var dao: TaskManagerDao
    private lateinit var scheduler: RecordingTaskSyncScheduler
    private lateinit var repository: TaskManagerRepository
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-03-17T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var databaseName: String

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        dispatcher = TaskApiDispatcher()
        server.dispatcher = dispatcher
        server.start()

        json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "kind"
            encodeDefaults = false
        }
        okHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        mapper = LocalCacheMapper(json)
        preferencesStore = AppPreferencesStore(context)
        preferencesStore.setBaseUrl(server.url("/api/").toString())

        databaseName = "task-manager-test-${UUID.randomUUID()}"
        database = buildDatabase(databaseName)
        dao = database.taskManagerDao()
        scheduler = RecordingTaskSyncScheduler()
        repository = createRepository(database, dao, scheduler)
    }

    @After
    fun tearDown() {
        database.close()
        runCatching { server.shutdown() }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `observeLiveEvents emits when a task event arrives`() = runBlocking {
        dispatcher.eventStreamResponse =
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(
                    "event: change\n" +
                        "data: {\"version\":1,\"entity_type\":\"task\",\"entity_ids\":[42],\"changed_at\":\"2026-03-15T12:00:00Z\"}\n\n",
                    32,
                )

        val result = withTimeout(3_000) {
            repository.observeLiveEvents(server.url("/api/").toString()).first()
        }

        assertThat(result).isEqualTo(Unit)
    }

    @Test
    fun `cold start without network serves cached lists and tasks`() {
        runBlocking {
            val baseUrl = preferencesStore.currentPreferences().baseUrl
            seedCache(baseUrl)

            database.close()
            database = buildDatabase(databaseName)
            dao = database.taskManagerDao()
            repository = createRepository(database, dao, scheduler)
            server.shutdown()

            val cachedLists = repository.observeLists().first()
            val cachedTasks = repository.observeTasks().first()

            assertThat(cachedLists.map { it.name }).containsExactly("Inbox")
            assertThat(cachedTasks.map { it.title }).containsExactly("Cached task")
        }
    }

    @Test
    fun `offline create stores local task and pending operation`() = runBlocking {
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Offline create",
                description = "Persist me",
                priority = "not_urgent_important",
                repeat = "none",
                listId = null,
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        val baseUrl = preferencesStore.currentPreferences().baseUrl
        val cachedTasks = repository.observeTasks().first { it.isNotEmpty() }
        val pendingOps = dao.getPendingSyncOperations(baseUrl)

        assertThat(cachedTasks.single().id).isLessThan(0)
        assertThat(cachedTasks.single().title).isEqualTo("Offline create")
        assertThat(pendingOps).hasSize(1)
        assertThat(pendingOps.single().operationType).isEqualTo("create_task")
        assertThat(scheduler.enqueueCalls).isEqualTo(1)
    }

    @Test
    fun `offline toggle updates local state and enqueues completion op`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)

        repository.toggleTask(7)

        val updatedTask = repository.observeTasks().first().single()
        val pendingOps = dao.getPendingSyncOperations(baseUrl)

        assertThat(updatedTask.isDone).isTrue()
        assertThat(pendingOps).hasSize(1)
        assertThat(pendingOps.single().operationType).isEqualTo("set_task_completion")
        assertThat(scheduler.enqueueCalls).isEqualTo(1)
    }

    @Test
    fun `pending ops survive app restart`() = runBlocking {
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Restart me",
                priority = "not_urgent_unimportant",
                repeat = "none",
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        database.close()
        database = buildDatabase(databaseName)
        dao = database.taskManagerDao()
        repository = createRepository(database, dao, RecordingTaskSyncScheduler())

        val baseUrl = preferencesStore.currentPreferences().baseUrl
        val cachedTasks = repository.observeTasks().first()
        val pendingOps = dao.getPendingSyncOperations(baseUrl)

        assertThat(cachedTasks).hasSize(1)
        assertThat(cachedTasks.single().title).isEqualTo("Restart me")
        assertThat(pendingOps).hasSize(1)
    }

    @Test
    fun `manual sync uploads queued create and refreshes cache from server`() = runBlocking {
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Sync me",
                priority = "urgent_important",
                repeat = "none",
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        val syncedTasks = repository.observeTasks().first()
        val pendingOps = dao.getPendingSyncOperations(baseUrl)

        assertThat(result.success).isTrue()
        assertThat(syncedTasks.single().id).isGreaterThan(0)
        assertThat(syncedTasks.single().title).isEqualTo("Sync me")
        assertThat(pendingOps).isEmpty()
        assertThat(dispatcher.createCalls).isEqualTo(1)
    }

    @Test
    fun `create subtask keeps it nested under parent before and after sync`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(
            baseUrl,
            listOf(
                serverTask(
                    id = 7,
                    title = "Parent task",
                    isDone = false,
                    listId = 4,
                    subtasks = emptyList(),
                ),
            ),
        )

        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Nested child",
                priority = "not_urgent_unimportant",
                repeat = "none",
                parentId = 7,
                listId = 4,
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        val localParent = repository.observeTasks().first().single()
        assertThat(localParent.subtasks.map { it.title }).containsExactly("Nested child")
        assertThat(localParent.subtasks.single().id).isLessThan(0)

        val syncResult = repository.syncCurrentBaseUrl(forceRefresh = true)
        val syncedParent = repository.observeTasks().first().single()

        assertThat(syncResult.success).isTrue()
        assertThat(dispatcher.createCalls).isEqualTo(1)
        assertThat(syncedParent.subtasks.map { it.title }).containsExactly("Nested child")
        assertThat(syncedParent.subtasks.single().id).isGreaterThan(0)
        assertThat(syncedParent.subtasks.single().parentId).isEqualTo(7)
        assertThat(syncedParent.subtasks.single().listId).isEqualTo(4)
    }

    @Test
    fun `toggling one subtask keeps it in the list and preserves sibling progress through sync`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(
            baseUrl,
            listOf(
                serverTask(
                    id = 7,
                    title = "Parent task",
                    isDone = false,
                    subtasks = listOf(
                        serverSubtask(id = 71, parentId = 7, title = "First child", isDone = false, position = 0),
                        serverSubtask(id = 72, parentId = 7, title = "Second child", isDone = true, position = 1),
                    ),
                ),
            ),
        )

        repository.toggleTask(71)

        val localParent = repository.observeTasks().first().single()
        assertThat(localParent.subtasks.map { it.id }).containsExactly(71, 72).inOrder()
        assertThat(localParent.subtasks.first { it.id == 71 }.isDone).isTrue()
        assertThat(localParent.subtasks.first { it.id == 72 }.isDone).isTrue()

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val syncedParent = repository.observeTasks().first().single()

        assertThat(result.success).isTrue()
        assertThat(dispatcher.toggleCalls).isEqualTo(1)
        assertThat(syncedParent.subtasks.map { it.id }).containsExactly(71, 72).inOrder()
        assertThat(syncedParent.subtasks.first { it.id == 71 }.isDone).isTrue()
        assertThat(syncedParent.subtasks.first { it.id == 72 }.isDone).isTrue()
    }

    @Test
    fun `repeated sync retry does not create duplicate task after lost response`() = runBlocking {
        dispatcher.disconnectAfterNextCreate = true
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Idempotent create",
                priority = "not_urgent_important",
                repeat = "none",
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        val firstAttempt = repository.syncCurrentBaseUrl(forceRefresh = true)
        val secondAttempt = repository.syncCurrentBaseUrl(forceRefresh = true)
        val baseUrl = preferencesStore.currentPreferences().baseUrl

        assertThat(firstAttempt.success).isFalse()
        assertThat(firstAttempt.shouldRetry).isTrue()
        assertThat(secondAttempt.success).isTrue()
        assertThat(dispatcher.tasks).hasSize(1)
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
        assertThat(repository.observeTasks().first().single().title).isEqualTo("Idempotent create")
        assertThat(dispatcher.createCalls).isEqualTo(2)
    }

    @Test
    fun `repeated sync retry does not double toggle task`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)
        dispatcher.tasks = mutableListOf(
            serverTask(id = 7, title = "Cached task", isDone = false),
        )

        repository.toggleTask(7)
        dispatcher.disconnectAfterNextToggle = true

        val firstAttempt = repository.syncCurrentBaseUrl(forceRefresh = true)
        val secondAttempt = repository.syncCurrentBaseUrl(forceRefresh = true)

        assertThat(firstAttempt.success).isFalse()
        assertThat(secondAttempt.success).isTrue()
        assertThat(dispatcher.toggleCalls).isEqualTo(1)
        assertThat(dispatcher.tasks.single().isDone).isTrue()
    }

    @Test
    fun `failed sync keeps pending queue`() = runBlocking {
        dispatcher.failNextCreateWithStatus = 500
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Retry later",
                priority = "not_urgent_unimportant",
                repeat = "none",
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val baseUrl = preferencesStore.currentPreferences().baseUrl

        assertThat(result.success).isFalse()
        assertThat(result.shouldRetry).isTrue()
        assertThat(dao.getPendingSyncOperations(baseUrl)).hasSize(1)
        assertThat(repository.observeTasks().first().single().id).isLessThan(0)
    }

    @Test
    fun `successful sync clears only confirmed operations`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)
        dispatcher.tasks = mutableListOf(
            serverTask(id = 7, title = "Cached task", isDone = false),
        )
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Second task",
                priority = "not_urgent_unimportant",
                repeat = "none",
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )
        repository.toggleTask(7)
        dispatcher.failNextToggleWithStatus = 500

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val pendingOps = dao.getPendingSyncOperations(baseUrl)
        val tasks = repository.observeTasks().first()

        assertThat(result.success).isFalse()
        assertThat(pendingOps).hasSize(1)
        assertThat(pendingOps.single().operationType).isEqualTo("set_task_completion")
        assertThat(tasks.map { it.title }).containsExactly("Cached task", "Second task")
        assertThat(tasks.first { it.title == "Cached task" }.isDone).isTrue()
        assertThat(tasks.first { it.title == "Second task" }.id).isGreaterThan(0)
    }

    @Test
    fun `offline update on synced task updates cache and coalesces pending update`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)

        repository.updateTask(
            7,
            updatePayload(
                "due_date" to JsonPrimitive("2026-03-20"),
                "priority" to JsonPrimitive("urgent_important"),
                "reminder_time" to JsonPrimitive("08:30"),
            ),
        )
        repository.updateTask(
            7,
            updatePayload(
                "title" to JsonPrimitive("Updated offline"),
                "priority" to JsonPrimitive("urgent_unimportant"),
            ),
        )

        val updatedTask = repository.observeTasks().first().single()
        val pendingOps = dao.getPendingSyncOperations(baseUrl)

        assertThat(updatedTask.title).isEqualTo("Updated offline")
        assertThat(updatedTask.dueDate).isEqualTo("2026-03-20")
        assertThat(updatedTask.reminderTime).isEqualTo("08:30")
        assertThat(updatedTask.priority.wire).isEqualTo("urgent_unimportant")
        assertThat(pendingOps).hasSize(1)
        assertThat(pendingOps.single().operationType).isEqualTo("update_task")
    }

    @Test
    fun `offline delete stores tombstone survives restart and does not return after sync`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)

        repository.deleteTask(7)

        assertThat(repository.observeTasks().first()).isEmpty()
        assertThat(dao.getPendingSyncOperations(baseUrl).single().operationType).isEqualTo("delete_task")
        assertThat(dao.getTasks(baseUrl).single { it.id == 7 }.deletedAt).isNotNull()

        database.close()
        database = buildDatabase(databaseName)
        dao = database.taskManagerDao()
        repository = createRepository(database, dao, RecordingTaskSyncScheduler())

        assertThat(repository.observeTasks().first()).isEmpty()

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)

        assertThat(result.success).isTrue()
        assertThat(dispatcher.deleteCalls).isEqualTo(1)
        assertThat(dispatcher.tasks).isEmpty()
        assertThat(repository.observeTasks().first()).isEmpty()
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
        assertThat(dao.getTasks(baseUrl).single { it.id == 7 }.deletedAt).isNotNull()
    }

    @Test
    fun `offline recurring update survives restart and syncs to server`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        val recurringTask = serverTask(
            id = 7,
            title = "Weekly review",
            isDone = false,
            dueDate = "2026-03-20",
            reminderTime = "08:00",
            repeat = "weekly",
            repeatUntil = "2026-05-01",
        )
        seedCache(baseUrl, listOf(recurringTask))

        repository.updateTask(
            7,
            updatePayload(
                "title" to JsonPrimitive("Monthly review"),
                "due_date" to JsonPrimitive("2026-03-21"),
                "reminder_time" to JsonPrimitive("09:15"),
                "repeat" to JsonPrimitive("monthly"),
                "repeat_until" to JsonPrimitive("2026-06-01"),
            ),
        )

        database.close()
        database = buildDatabase(databaseName)
        dao = database.taskManagerDao()
        repository = createRepository(database, dao, RecordingTaskSyncScheduler())

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val syncedTask = repository.observeTasks().first().single()

        assertThat(result.success).isTrue()
        assertThat(dispatcher.updateCalls).isEqualTo(1)
        assertThat(dispatcher.tasks.single().title).isEqualTo("Monthly review")
        assertThat(dispatcher.tasks.single().repeat).isEqualTo("monthly")
        assertThat(dispatcher.tasks.single().repeatUntil).isEqualTo("2026-06-01")
        assertThat(syncedTask.title).isEqualTo("Monthly review")
        assertThat(syncedTask.repeat.wire).isEqualTo("monthly")
        assertThat(syncedTask.repeatUntil).isEqualTo("2026-06-01")
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
    }

    @Test
    fun `offline reorder subtasks with local ids survives sync`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(
            baseUrl,
            listOf(
                serverTask(
                    id = 7,
                    title = "Parent task",
                    isDone = false,
                    subtasks = listOf(
                        serverSubtask(id = 71, parentId = 7, title = "First", position = 0),
                        serverSubtask(id = 72, parentId = 7, title = "Second", position = 1),
                    ),
                ),
            ),
        )

        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Third",
                priority = "not_urgent_unimportant",
                repeat = "none",
                parentId = 7,
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )

        val localSubtaskId = repository.observeTasks().first().single()
            .subtasks.first { it.title == "Third" }
            .id

        repository.reorderSubtasks(7, listOf(72, localSubtaskId, 71))

        val localParent = repository.observeTasks().first().single()
        assertThat(localParent.subtasks.map { it.title }).containsExactly("Second", "Third", "First").inOrder()

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val syncedParent = repository.observeTasks().first().single()

        assertThat(result.success).isTrue()
        assertThat(syncedParent.subtasks.map { it.title }).containsExactly("Second", "Third", "First").inOrder()
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
    }

    @Test
    fun `calendar checkbox completion works for recurring and non recurring tasks`() {
        runBlocking {
            val baseUrl = preferencesStore.currentPreferences().baseUrl
            seedCache(
                baseUrl,
                listOf(
                    serverTask(id = 7, title = "One-off task", isDone = false, dueDate = "2026-03-17"),
                    serverTask(
                        id = 8,
                        title = "Daily review",
                        isDone = false,
                        dueDate = "2026-03-17",
                        repeat = "daily",
                        repeatUntil = "2026-03-19",
                        subtasks = listOf(serverSubtask(id = 81, parentId = 8, title = "Prep notes", position = 0)),
                    ),
                ),
            )

            repository.toggleTask(7)
            repository.toggleTask(8)

            val localTasks = repository.observeTasks().first()
            assertThat(localTasks.first { it.id == 7 }.isDone).isTrue()
            assertThat(localTasks.first { it.id == 8 }.isDone).isTrue()

            val result = repository.syncCurrentBaseUrl(forceRefresh = true)
            val syncedTasks = repository.observeTasks().first()
            val historicalRecurring = syncedTasks.first { it.id == 8 }
            val nextRecurring = syncedTasks.first { it.id != 8 && it.title == "Daily review" }

            assertThat(result.success).isTrue()
            assertThat(dispatcher.toggleCalls).isEqualTo(2)
            assertThat(syncedTasks.first { it.id == 7 }.isDone).isTrue()
            assertThat(historicalRecurring.isDone).isTrue()
            assertThat(nextRecurring.isDone).isFalse()
            assertThat(nextRecurring.dueDate).isEqualTo("2026-03-18")
            assertThat(nextRecurring.subtasks.map { it.title }).containsExactly("Prep notes").inOrder()
        }
    }

    @Test
    fun `offline recurring delete syncs without resurrecting the task`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(
            baseUrl,
            listOf(
                serverTask(
                    id = 7,
                    title = "Pay rent",
                    isDone = false,
                    dueDate = "2026-03-20",
                    repeat = "monthly",
                ),
            ),
        )

        repository.deleteTask(7)
        val result = repository.syncCurrentBaseUrl(forceRefresh = true)

        assertThat(result.success).isTrue()
        assertThat(dispatcher.deleteCalls).isEqualTo(1)
        assertThat(dispatcher.tasks).isEmpty()
        assertThat(repository.observeTasks().first()).isEmpty()
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
    }

    @Test
    fun `delete retry does not duplicate operation after lost response`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)
        dispatcher.disconnectAfterNextDelete = true

        repository.deleteTask(7)

        val firstAttempt = repository.syncCurrentBaseUrl(forceRefresh = true)
        val secondAttempt = repository.syncCurrentBaseUrl(forceRefresh = true)

        assertThat(firstAttempt.success).isFalse()
        assertThat(firstAttempt.shouldRetry).isTrue()
        assertThat(secondAttempt.success).isTrue()
        assertThat(dispatcher.deleteCalls).isEqualTo(1)
        assertThat(dispatcher.tasks).isEmpty()
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
        assertThat(repository.observeTasks().first()).isEmpty()
    }

    @Test
    fun `offline update on unsynced task rewrites pending create payload`() = runBlocking {
        repository.createTask(
            payload = ApiTaskCreatePayload(
                title = "Draft task",
                priority = "not_urgent_unimportant",
                repeat = "none",
            ),
            editorContext = TaskEditorContext(viewTarget = TaskViewTarget.All),
            todayString = "2026-03-17",
            tomorrowString = "2026-03-18",
            newTaskPlacement = NewTaskPlacement.END,
        )
        val localTaskId = repository.observeTasks().first().single().id

        repository.updateTask(
            localTaskId,
            updatePayload(
                "title" to JsonPrimitive("Retitled draft"),
                "due_date" to JsonPrimitive("2026-03-19"),
                "reminder_time" to JsonPrimitive("09:15"),
            ),
        )

        val pendingOp = dao.getPendingSyncOperations(preferencesStore.currentPreferences().baseUrl).single()
        assertThat(repository.observeTasks().first().single().title).isEqualTo("Retitled draft")
        assertThat(pendingOp.operationType).isEqualTo("create_task")
        assertThat(pendingOp.payloadJson).contains("Retitled draft")
        assertThat(pendingOp.payloadJson).contains("09:15")
    }

    @Test
    fun `sync uploads pending task update and refreshes cache`() = runBlocking {
        val baseUrl = preferencesStore.currentPreferences().baseUrl
        seedCache(baseUrl)
        dispatcher.tasks = mutableListOf(
            serverTask(id = 7, title = "Cached task", isDone = false),
        )

        repository.updateTask(
            7,
            updatePayload(
                "title" to JsonPrimitive("Edited locally"),
                "due_date" to JsonPrimitive("2026-03-21"),
                "reminder_time" to JsonPrimitive("11:45"),
            ),
        )

        val result = repository.syncCurrentBaseUrl(forceRefresh = true)
        val syncedTask = repository.observeTasks().first().single()

        assertThat(result.success).isTrue()
        assertThat(dispatcher.updateCalls).isEqualTo(1)
        assertThat(syncedTask.title).isEqualTo("Edited locally")
        assertThat(syncedTask.dueDate).isEqualTo("2026-03-21")
        assertThat(syncedTask.reminderTime).isEqualTo("11:45")
        assertThat(dao.getPendingSyncOperations(baseUrl)).isEmpty()
    }

    private fun createRepository(
        database: TaskManagerDatabase,
        dao: TaskManagerDao,
        scheduler: TaskSyncScheduler,
    ): TaskManagerRepository = TaskManagerRepository(
        apiServiceFactory = ApiServiceFactory(json, okHttpClient),
        preferencesStore = preferencesStore,
        json = json,
        okHttpClient = okHttpClient,
        database = database,
        dao = dao,
        localCacheMapper = mapper,
        notificationManager = TaskNotificationManager(
            context = context,
            alarmManager = context.getSystemService(AlarmManager::class.java),
            notificationManager = NotificationManagerCompat.from(context),
            dao = dao,
            localCacheMapper = mapper,
            preferencesStore = preferencesStore,
            clock = fixedClock,
        ),
        syncScheduler = scheduler,
        clock = fixedClock,
    )

    private fun updatePayload(vararg pairs: Pair<String, JsonElement>): JsonObject =
        buildJsonObject {
            pairs.forEach { (key, value) -> put(key, value) }
        }

    private fun buildDatabase(name: String): TaskManagerDatabase =
        Room.databaseBuilder(context, TaskManagerDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private suspend fun seedCache(
        baseUrl: String,
        tasks: List<ApiTask> = listOf(serverTask(id = 7, title = "Cached task", isDone = false)),
    ) {
        database.withTransaction {
            dao.replaceLists(
                baseUrl,
                mapper.toListEntities(
                    baseUrl,
                    listOf(
                        ApiListItem(
                            id = 1,
                            name = "Inbox",
                            color = "#2563EB",
                            position = 0,
                            createdAt = "2026-03-17T10:00:00Z",
                            updatedAt = "2026-03-17T10:00:00Z",
                        ),
                    ),
                ),
            )
            dao.replaceRemoteTasks(
                baseUrl,
                mapper.toTaskEntities(
                    baseUrl,
                    tasks,
                ),
            )
        }
        dispatcher.tasks = tasks.toMutableList()
    }

    private fun serverTask(
        id: Int,
        title: String,
        isDone: Boolean,
        priority: String = "not_urgent_unimportant",
        dueDate: String? = null,
        reminderTime: String? = null,
        repeat: String = "none",
        repeatUntil: String? = null,
        listId: Int? = null,
        subtasks: List<ApiTask> = emptyList(),
    ): ApiTask = ApiTask(
        id = id,
        title = title,
        description = null,
        descriptionBlocks = emptyList(),
        dueDate = dueDate,
        reminderTime = reminderTime,
        repeatUntil = repeatUntil,
        isDone = isDone,
        isPinned = false,
        priority = priority,
        repeat = repeat,
        parentId = null,
        position = id,
        listId = listId,
        createdAt = "2026-03-17T10:00:00Z",
        updatedAt = "2026-03-17T10:00:00Z",
        subtasks = subtasks,
    )

    private fun serverSubtask(
        id: Int,
        parentId: Int,
        title: String,
        isDone: Boolean = false,
        position: Int = 0,
    ): ApiTask = ApiTask(
        id = id,
        title = title,
        description = null,
        descriptionBlocks = emptyList(),
        dueDate = null,
        reminderTime = null,
        repeatUntil = null,
        isDone = isDone,
        isPinned = false,
        priority = "not_urgent_unimportant",
        repeat = "none",
        parentId = parentId,
        position = position,
        listId = null,
        createdAt = "2026-03-17T10:00:00Z",
        updatedAt = "2026-03-17T10:00:00Z",
        subtasks = emptyList(),
    )

    private class RecordingTaskSyncScheduler : TaskSyncScheduler {
        var enqueueCalls: Int = 0

        override fun enqueuePendingSync() {
            enqueueCalls += 1
        }
    }

    private class TaskApiDispatcher : Dispatcher() {
        private val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "kind"
            encodeDefaults = false
        }
        private val taskListSerializer = ListSerializer(ApiTask.serializer())
        private val listSerializer = ListSerializer(ApiListItem.serializer())
        var tasks: MutableList<ApiTask> = mutableListOf()
        var lists: MutableList<ApiListItem> = mutableListOf(
            ApiListItem(
                id = 1,
                name = "Inbox",
                color = "#2563EB",
                position = 0,
                createdAt = "2026-03-17T10:00:00Z",
                updatedAt = "2026-03-17T10:00:00Z",
            ),
        )
        var nextTaskId: Int = 100
        var createCalls: Int = 0
        var updateCalls: Int = 0
        var toggleCalls: Int = 0
        var deleteCalls: Int = 0
        var failNextCreateWithStatus: Int? = null
        var failNextToggleWithStatus: Int? = null
        var failNextDeleteWithStatus: Int? = null
        var disconnectAfterNextCreate: Boolean = false
        var disconnectAfterNextToggle: Boolean = false
        var disconnectAfterNextDelete: Boolean = false
        var eventStreamResponse: MockResponse? = null
        private val taskIdsByClientRequestId = linkedMapOf<String, Int>()

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl?.encodedPath ?: return MockResponse().setResponseCode(404)
            return when {
                request.method == "GET" && path == "/api/events" ->
                    eventStreamResponse ?: MockResponse().setResponseCode(204)
                request.method == "GET" && path == "/api/lists" -> jsonResponse(
                    json.encodeToString(listSerializer, lists),
                )
                request.method == "GET" && path == "/api/tasks" -> jsonResponse(
                    json.encodeToString(taskListSerializer, tasks),
                )
                request.method == "POST" && path == "/api/tasks" -> handleCreate(request)
                request.method == "POST" && path.matches(Regex("/api/tasks/\\d+/subtasks/reorder")) -> handleSubtaskReorder(path, request)
                request.method == "PATCH" && path.matches(Regex("/api/tasks/\\d+")) -> handleUpdate(path, request)
                request.method == "DELETE" && path.matches(Regex("/api/tasks/\\d+")) -> handleDelete(path)
                request.method == "POST" && path.matches(Regex("/api/tasks/\\d+/toggle")) -> handleToggle(path)
                else -> MockResponse().setResponseCode(404)
            }
        }

        private fun handleCreate(request: RecordedRequest): MockResponse {
            createCalls += 1
            failNextCreateWithStatus?.let { status ->
                failNextCreateWithStatus = null
                return MockResponse().setResponseCode(status).setBody("{\"detail\":\"Server error\"}")
            }

            val payload = json.decodeFromString<ApiTaskCreatePayload>(request.body.readUtf8())
            val existingId = payload.clientRequestId?.let(taskIdsByClientRequestId::get)
            val createdTask = if (existingId != null) {
                tasks.first { it.id == existingId }
            } else {
                val rootId = nextTaskId++
                val created = ApiTask(
                    id = rootId,
                    title = payload.title,
                    description = payload.description,
                    descriptionBlocks = payload.descriptionBlocks,
                    dueDate = payload.dueDate,
                    reminderTime = payload.reminderTime,
                    repeatUntil = payload.repeatUntil,
                    isDone = payload.isDone,
                    isPinned = payload.isPinned,
                    priority = payload.priority,
                    repeat = payload.repeat,
                    parentId = payload.parentId,
                    position = tasks.size,
                    listId = payload.listId,
                    createdAt = "2026-03-17T12:00:00Z",
                    updatedAt = "2026-03-17T12:00:00Z",
                    subtasks = payload.subtasks.mapIndexed { index, subtask ->
                        ApiTask(
                            id = nextTaskId++,
                            title = subtask.title,
                            description = subtask.description,
                            descriptionBlocks = subtask.descriptionBlocks,
                            dueDate = subtask.dueDate,
                            reminderTime = subtask.reminderTime,
                            repeatUntil = null,
                            isDone = subtask.isDone,
                            isPinned = false,
                            priority = payload.priority,
                            repeat = "none",
                            parentId = rootId,
                            position = index,
                            listId = payload.listId,
                            createdAt = "2026-03-17T12:00:00Z",
                            updatedAt = "2026-03-17T12:00:00Z",
                            subtasks = emptyList(),
                        )
                    },
                )
                payload.clientRequestId?.let { taskIdsByClientRequestId[it] = rootId }
                val parentId = payload.parentId
                if (parentId == null) {
                    tasks += created
                } else {
                    tasks = replaceTask(tasks, parentId) { parent ->
                        parent.copy(
                            subtasks = (parent.subtasks + created.copy(subtasks = emptyList()))
                                .mapIndexed { index, subtask -> subtask.copy(position = index) },
                        )
                    }.toMutableList()
                }
                created
            }

            if (disconnectAfterNextCreate) {
                disconnectAfterNextCreate = false
                return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }

            return jsonResponse(json.encodeToString(ApiTask.serializer(), createdTask), responseCode = 201)
        }

        private fun handleToggle(path: String): MockResponse {
            toggleCalls += 1
            failNextToggleWithStatus?.let { status ->
                failNextToggleWithStatus = null
                return MockResponse().setResponseCode(status).setBody("{\"detail\":\"Toggle failed\"}")
            }
            val taskId = path.removeSuffix("/toggle").substringAfterLast("/").toInt()
            val current = findTask(taskId)
            if (current == null) {
                return MockResponse().setResponseCode(404).setBody("{\"detail\":\"Task not found.\"}")
            }
            val toggled = current.copy(isDone = !current.isDone, updatedAt = "2026-03-17T12:05:00Z")
            tasks = replaceTask(tasks, taskId) { toggled }.toMutableList()

            if (!current.isDone && current.repeat != "none") {
                spawnNextRecurringTask(current)?.let { spawnedTask ->
                    val parentId = current.parentId
                    tasks = if (parentId == null) {
                        (tasks + spawnedTask).toMutableList()
                    } else {
                        replaceTask(tasks, parentId) { parent ->
                            parent.copy(
                                subtasks = (parent.subtasks + spawnedTask.copy(subtasks = emptyList()))
                                    .mapIndexed { index, subtask -> subtask.copy(position = index) },
                            )
                        }.toMutableList()
                    }
                }
            }

            if (disconnectAfterNextToggle) {
                disconnectAfterNextToggle = false
                return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }

            return jsonResponse(json.encodeToString(ApiTask.serializer(), toggled))
        }

        private fun handleUpdate(path: String, request: RecordedRequest): MockResponse {
            updateCalls += 1
            val taskId = path.substringAfterLast("/").toInt()
            val current = findTask(taskId)
            if (current == null) {
                return MockResponse().setResponseCode(404).setBody("{\"detail\":\"Task not found.\"}")
            }

            val payload = json.decodeFromString(com.taskmanager.android.data.api.ApiTaskUpdatePayload.serializer(), request.body.readUtf8())
            val updated = current.copy(
                title = payload.title ?: current.title,
                description = payload.description,
                descriptionBlocks = payload.descriptionBlocks ?: current.descriptionBlocks,
                dueDate = payload.dueDate,
                reminderTime = payload.reminderTime,
                repeatUntil = payload.repeatUntil,
                isPinned = payload.isPinned ?: current.isPinned,
                priority = payload.priority ?: current.priority,
                repeat = payload.repeat ?: current.repeat,
                listId = payload.listId,
                updatedAt = "2026-03-17T12:06:00Z",
                subtasks = if (current.parentId == null) {
                    current.subtasks.map { subtask ->
                        subtask.copy(listId = payload.listId ?: subtask.listId)
                    }
                } else {
                    current.subtasks
                },
            )
            tasks = replaceTask(tasks, taskId) { updated }.toMutableList()
            return jsonResponse(json.encodeToString(ApiTask.serializer(), updated))
        }

        private fun handleDelete(path: String): MockResponse {
            deleteCalls += 1
            failNextDeleteWithStatus?.let { status ->
                failNextDeleteWithStatus = null
                return MockResponse().setResponseCode(status).setBody("{\"detail\":\"Delete failed\"}")
            }

            val taskId = path.substringAfterLast("/").toInt()
            tasks = deleteTask(tasks, taskId).toMutableList()

            if (disconnectAfterNextDelete) {
                disconnectAfterNextDelete = false
                return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            }

            return MockResponse().setResponseCode(204)
        }

        private fun handleSubtaskReorder(path: String, request: RecordedRequest): MockResponse {
            val parentId = path.substringBeforeLast("/subtasks/reorder").substringAfterLast("/").toInt()
            val parentTask = findTask(parentId)
                ?: return MockResponse().setResponseCode(404).setBody("{\"detail\":\"Task not found.\"}")
            val payload = json.decodeFromString(ApiSubtaskReorderPayload.serializer(), request.body.readUtf8())
            val currentIds = parentTask.subtasks.map(ApiTask::id)
            if (payload.subtaskIds.sorted() != currentIds.sorted()) {
                return MockResponse().setResponseCode(400).setBody("{\"detail\":\"Subtask reorder payload must contain the exact current subtask ids.\"}")
            }

            val subtasksById = parentTask.subtasks.associateBy(ApiTask::id)
            val reorderedParent = parentTask.copy(
                updatedAt = "2026-03-17T12:07:00Z",
                subtasks = payload.subtaskIds.mapIndexed { index, subtaskId ->
                    subtasksById.getValue(subtaskId).copy(position = index)
                },
            )
            tasks = replaceTask(tasks, parentId) { reorderedParent }.toMutableList()
            return jsonResponse(json.encodeToString(ApiTask.serializer(), reorderedParent))
        }

        private fun jsonResponse(body: String, responseCode: Int = 200): MockResponse =
            MockResponse()
                .setResponseCode(responseCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body)

        private fun findTask(taskId: Int): ApiTask? {
            fun search(taskItems: List<ApiTask>): ApiTask? {
                taskItems.forEach { task ->
                    if (task.id == taskId) {
                        return task
                    }
                    search(task.subtasks)?.let { return it }
                }
                return null
            }

            return search(tasks)
        }

        private fun replaceTask(
            taskItems: List<ApiTask>,
            taskId: Int,
            transform: (ApiTask) -> ApiTask,
        ): List<ApiTask> = taskItems.map { task ->
            when {
                task.id == taskId -> transform(task)
                task.subtasks.isNotEmpty() -> task.copy(subtasks = replaceTask(task.subtasks, taskId, transform))
                else -> task
            }
        }

        private fun deleteTask(taskItems: List<ApiTask>, taskId: Int): List<ApiTask> = taskItems
            .filterNot { it.id == taskId }
            .map { task ->
                if (task.subtasks.isEmpty()) {
                    task
                } else {
                    task.copy(
                        subtasks = deleteTask(task.subtasks, taskId).mapIndexed { index, subtask ->
                            subtask.copy(position = index)
                        },
                    )
                }
            }

        private fun spawnNextRecurringTask(task: ApiTask): ApiTask? {
            val dueDate = task.dueDate?.let(LocalDate::parse) ?: return null
            val nextDueDate = when (task.repeat) {
                "daily" -> dueDate.plusDays(1)
                "weekly" -> dueDate.plusWeeks(1)
                "monthly" -> dueDate.plusMonths(1)
                "yearly" -> dueDate.plusYears(1)
                else -> dueDate
            }
            val repeatUntil = task.repeatUntil?.let(LocalDate::parse)
            if (repeatUntil != null && nextDueDate.isAfter(repeatUntil)) {
                return null
            }

            val spawnedTaskId = nextTaskId++
            return task.copy(
                id = spawnedTaskId,
                dueDate = nextDueDate.toString(),
                isDone = false,
                isPinned = if (task.parentId != null) false else task.isPinned,
                position = task.parentId?.let { parentId -> findTask(parentId)?.subtasks?.size ?: 0 } ?: tasks.size,
                createdAt = "2026-03-17T12:05:00Z",
                updatedAt = "2026-03-17T12:05:00Z",
                subtasks = if (task.parentId == null) {
                    task.subtasks.mapIndexed { index, subtask ->
                        subtask.copy(
                            id = nextTaskId++,
                            parentId = spawnedTaskId,
                            position = index,
                            isDone = false,
                            createdAt = "2026-03-17T12:05:00Z",
                            updatedAt = "2026-03-17T12:05:00Z",
                        )
                    }
                } else {
                    emptyList()
                },
            )
        }
    }
}
