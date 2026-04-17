import { useCallback, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarRange, Inbox, ListTodo, Plus, Sunrise } from "lucide-react";
import { useParams } from "react-router-dom";

import { AllTaskGroupColumn } from "@/components/all-task-group-column";
import { EmptyState } from "@/components/empty-state";
import { TaskDialog } from "@/components/task-dialog";
import { TaskDoneSection } from "@/components/task-done-section";
import { TaskGroupSection } from "@/components/task-group-section";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  createTask,
  deleteTask,
  getInboxTasks,
  getListTasks,
  getTasks,
  moveTask,
  reorderTopLevelTasks,
  getTodayTasks,
  getTomorrowTasks,
  reorderSubtasks,
  toggleTask,
  updateTask,
} from "@/lib/api";
import { getLocalDateString, getOffsetLocalDateString, getTomorrowDateString } from "@/lib/date";
import {
  applyTaskMoveResultInCaches,
  findTaskInTaskCaches,
  removeSubtaskFromCaches,
  removeTaskFromCaches,
  upsertSubtaskInCaches,
  upsertTaskInCaches,
  upsertTasksInCaches,
} from "@/lib/task-cache";
import { buildTaskDraft, type TaskDraftOverrides } from "@/lib/task-draft";
import {
  type AllTaskGroup,
  buildTopLevelTaskDropScope,
  buildTopLevelTaskReorderScopeForTask,
  buildTaskSectionCollapseKey,
  filterVisibleTasks,
  getTopLevelTaskIdsForReorderScope,
  getVisibleAllTaskGroups,
  getVisibleTodayTaskGroups,
  getVisibleTaskCollection,
} from "@/lib/task-groups";
import { insertOrderedId } from "@/lib/task-reorder";
import type {
  AllTaskGroupId,
  DraggedTaskItem,
  ListItem,
  NewTaskPlacementPreference,
  TaskCreatePayload,
  TaskDraft,
  TaskItem,
  TaskMovePayload,
  TaskSectionId,
  TaskSubtask,
  TaskTopLevelReorderScope,
  TaskUpdatePayload,
  ViewMode,
} from "@/lib/types";

type TasksPageProps = {
  mode: ViewMode;
  lists: ListItem[];
  listsLoading: boolean;
  showCompleted: boolean;
  newTaskPlacement: NewTaskPlacementPreference;
  todayString: string;
  tomorrowString: string;
};

export const ALL_VIEW_COLUMNS_SCROLLER_CLASSNAME = "h-full min-h-0 overflow-x-auto overflow-y-hidden pb-3";
export const ALL_VIEW_COLUMNS_ROW_CLASSNAME = "flex h-full min-h-0 min-w-max items-stretch gap-4";

const SUBTASKS_COLLAPSED_STORAGE_KEY = "task-manager.subtasks.collapsed";
const TASK_SECTIONS_COLLAPSED_STORAGE_KEY = "task-manager.task-sections.collapsed";

function getStoredSubtasksCollapsed(): Record<number, boolean> {
  if (typeof window === "undefined") {
    return {};
  }

  const storedValue = window.localStorage.getItem(SUBTASKS_COLLAPSED_STORAGE_KEY);
  if (!storedValue) {
    return {};
  }

  try {
    return JSON.parse(storedValue) as Record<number, boolean>;
  } catch {
    return {};
  }
}

function getStoredTaskSectionsCollapsed(): Record<string, boolean> {
  if (typeof window === "undefined") {
    return {};
  }

  const storedValue = window.localStorage.getItem(TASK_SECTIONS_COLLAPSED_STORAGE_KEY);
  if (!storedValue) {
    return {};
  }

  try {
    return JSON.parse(storedValue) as Record<string, boolean>;
  } catch {
    return {};
  }
}

function getTaskSectionScope(mode: ViewMode, listId: number | null): string {
  if (mode === "list" && listId) {
    return `list:${listId}`;
  }

  return mode;
}

function getPageMeta(mode: ViewMode, selectedList?: ListItem | null) {
  if (mode === "today") {
    return {
      title: "Today",
      emptyTitle: "Nothing due or overdue",
      emptyDescription: "Add a task here to prefill today's date and keep both overdue and due work in view.",
      icon: CalendarRange,
    };
  }

  if (mode === "tomorrow") {
    return {
      title: "Tomorrow",
      emptyTitle: "Nothing queued for tomorrow",
      emptyDescription: "Create a task here to prefill tomorrow's date.",
      icon: Sunrise,
    };
  }

  if (mode === "inbox") {
    return {
      title: "Inbox",
      emptyTitle: "Inbox is clear",
      emptyDescription: "Tasks without a date land here. Capture first, organize second.",
      icon: Inbox,
    };
  }

  if (mode === "list") {
    return {
      title: selectedList?.name ?? "List",
      emptyTitle: selectedList ? `${selectedList.name} is empty` : "List is empty",
      emptyDescription: "Create a task from this view to keep it in the current list.",
      icon: ListTodo,
    };
  }

  return {
    title: "All",
    emptyTitle: "No tasks yet",
    emptyDescription: "Create your first task and the board will start to fill itself.",
    icon: ListTodo,
  };
}

function getAllGroupDraftOverrides(groupId: AllTaskGroupId): TaskDraftOverrides {
  if (groupId === "overdue") {
    return { due_date: getOffsetLocalDateString(-1) };
  }

  if (groupId === "today") {
    return { due_date: getLocalDateString() };
  }

  if (groupId === "tomorrow") {
    return { due_date: getTomorrowDateString() };
  }

  if (groupId === "next_7_days") {
    return { due_date: getOffsetLocalDateString(2) };
  }

  if (groupId === "later") {
    return { due_date: getOffsetLocalDateString(8) };
  }

  return { due_date: null };
}

export function TasksPage({
  mode,
  lists,
  listsLoading,
  showCompleted,
  newTaskPlacement,
  todayString,
  tomorrowString,
}: TasksPageProps) {
  const { listId: listIdParam } = useParams();
  const listId = mode === "list" ? Number(listIdParam) : null;
  const selectedList = mode === "list" ? lists.find((list) => list.id === listId) ?? null : null;
  const hasInvalidListRoute =
    mode === "list" &&
    (!listIdParam || Number.isNaN(listId) || (!listsLoading && selectedList === null));
  const queryClient = useQueryClient();
  const [taskDialogOpen, setTaskDialogOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<TaskItem | null>(null);
  const [createDraft, setCreateDraft] = useState<TaskDraft | null>(null);
  const [draggedTask, setDraggedTask] = useState<DraggedTaskItem | null>(null);
  const [collapsedSubtasks, setCollapsedSubtasks] = useState<Record<number, boolean>>(getStoredSubtasksCollapsed);
  const [collapsedTaskSections, setCollapsedTaskSections] = useState<Record<string, boolean>>(
    getStoredTaskSectionsCollapsed,
  );

  useEffect(() => {
    window.localStorage.setItem(SUBTASKS_COLLAPSED_STORAGE_KEY, JSON.stringify(collapsedSubtasks));
  }, [collapsedSubtasks]);

  useEffect(() => {
    window.localStorage.setItem(TASK_SECTIONS_COLLAPSED_STORAGE_KEY, JSON.stringify(collapsedTaskSections));
  }, [collapsedTaskSections]);

  const tasksQuery = useQuery({
    queryKey:
      mode === "all"
        ? ["tasks", "all"]
        : mode === "today"
          ? ["tasks", "today", todayString]
          : mode === "tomorrow"
            ? ["tasks", "tomorrow", tomorrowString]
            : mode === "inbox"
              ? ["tasks", "inbox"]
              : ["tasks", "list", listId],
    queryFn: () => {
      if (mode === "today") {
        return getTodayTasks(todayString);
      }
      if (mode === "tomorrow") {
        return getTomorrowTasks(tomorrowString);
      }
      if (mode === "inbox") {
        return getInboxTasks();
      }
      if (mode === "list" && listId) {
        return getListTasks(listId);
      }
      return getTasks();
    },
    enabled: (mode !== "list" || Boolean(listId)) && !hasInvalidListRoute,
    staleTime: 30_000,
  });

  const pageMeta = useMemo(() => getPageMeta(mode, selectedList), [mode, selectedList]);
  const baseDraft = useMemo(
    () => buildTaskDraft(mode, todayString, tomorrowString, listId),
    [listId, mode, todayString, tomorrowString],
  );
  const dialogDraft = createDraft ?? baseDraft;
  const tasks = tasksQuery.data ?? [];
  const visibleTasks = useMemo(() => filterVisibleTasks(tasks, showCompleted), [showCompleted, tasks]);
  const listById = useMemo(() => new Map(lists.map((list) => [list.id, list])), [lists]);
  const groupedTasks = useMemo<AllTaskGroup[]>(
    () => (mode === "all" ? getVisibleAllTaskGroups(tasks, todayString, showCompleted) : []),
    [mode, showCompleted, tasks, todayString],
  );
  const groupedTodayTasks = useMemo<AllTaskGroup[]>(
    () => (mode === "today" ? getVisibleTodayTaskGroups(tasks, todayString, showCompleted) : []),
    [mode, showCompleted, tasks, todayString],
  );
  const groupedSections = useMemo(
    () => (mode === "all" || mode === "today" ? null : getVisibleTaskCollection(tasks, showCompleted)),
    [mode, showCompleted, tasks],
  );
  const pageCount = hasInvalidListRoute ? null : tasksQuery.data ? visibleTasks.length : null;
  const taskSectionScope = useMemo(() => getTaskSectionScope(mode, listId), [listId, mode]);
  const headerCreateOverrides = useMemo<TaskDraftOverrides | null>(() => {
    if (mode === "today") {
      return { due_date: todayString };
    }

    if (mode === "tomorrow") {
      return { due_date: tomorrowString };
    }

    if (mode === "inbox") {
      return { due_date: null, list_id: null };
    }

    if (mode === "list" && listId) {
      return { due_date: null, list_id: listId };
    }

    return null;
  }, [listId, mode, todayString, tomorrowString]);

  const openCreateTaskDialog = useCallback(
    (overrides?: TaskDraftOverrides) => {
      setEditingTask(null);
      setCreateDraft(overrides ? buildTaskDraft(mode, todayString, tomorrowString, listId, overrides) : baseDraft);
      setTaskDialogOpen(true);
    },
    [baseDraft, listId, mode, todayString, tomorrowString],
  );

  const openEditTaskDialog = useCallback((task: TaskItem) => {
    setCreateDraft(null);
    setEditingTask(task);
    setTaskDialogOpen(true);
  }, []);

  const clearDraggedTask = useCallback(() => {
    setDraggedTask(null);
  }, []);

  const closeTaskDialog = useCallback((open: boolean) => {
    setTaskDialogOpen(open);

    if (!open) {
      setEditingTask(null);
      setCreateDraft(null);
    }
  }, []);

  const handleTaskCreate = useCallback(
    async (payload: TaskCreatePayload) => {
      const createdTask = await createTask(payload);
      upsertTaskInCaches(queryClient, createdTask);

      const reorderScope = buildTopLevelTaskReorderScopeForTask({
        task: createdTask,
        mode,
        todayString,
        targetDateString: mode === "today" ? todayString : mode === "tomorrow" ? tomorrowString : undefined,
        listId,
      });

      if (!reorderScope) {
        return;
      }

      const currentScopeTaskIds = getTopLevelTaskIdsForReorderScope(tasks, reorderScope);
      const reorderedTaskIds = insertOrderedId(currentScopeTaskIds, createdTask.id, newTaskPlacement);

      if (reorderedTaskIds.length <= 1) {
        return;
      }

      try {
        const reorderedTasks = await reorderTopLevelTasks(reorderedTaskIds, reorderScope);
        upsertTasksInCaches(queryClient, reorderedTasks);

        if (editingTask?.id) {
          const refreshedEditingTask = findTaskInTaskCaches(queryClient, editingTask.id);
          if (refreshedEditingTask) {
            setEditingTask(refreshedEditingTask);
          }
        }
      } catch {
        await queryClient.invalidateQueries({ queryKey: ["tasks"] });
      }
    },
    [editingTask?.id, listId, mode, newTaskPlacement, queryClient, tasks, todayString, tomorrowString],
  );

  const handleTaskUpdate = useCallback(
    async (task: TaskItem, payload: TaskUpdatePayload) => {
      const updatedTask = await updateTask(task.id, payload);
      upsertTaskInCaches(queryClient, updatedTask);

      if (editingTask?.id === updatedTask.id) {
        setEditingTask(updatedTask);
      }

      return updatedTask;
    },
    [editingTask?.id, queryClient],
  );

  const handleTaskDelete = useCallback(
    async (task: TaskItem) => {
      await deleteTask(task.id);
      removeTaskFromCaches(queryClient, task.id);
      setEditingTask(null);
    },
    [queryClient],
  );

  const handleTaskToggle = useCallback(
    async (task: TaskItem) => {
      const updatedTask = await toggleTask(task.id);
      upsertTaskInCaches(queryClient, updatedTask);

      if (task.repeat !== "none" && !task.is_done && updatedTask.is_done) {
        await queryClient.invalidateQueries({ queryKey: ["tasks"] });
      }

      return updatedTask;
    },
    [queryClient],
  );

  const handleCreateSubtask = useCallback(
    async (task: TaskItem, title: string) => {
      const createdSubtask = await createTask({
        title,
        description: null,
        description_blocks: [],
        due_date: null,
        reminder_time: null,
        is_done: false,
        is_pinned: false,
        priority: task.priority,
        repeat: "none",
        parent_id: task.id,
        list_id: task.list_id,
        repeat_until: null,
        subtasks: [],
      });

      upsertSubtaskInCaches(queryClient, task.id, createdSubtask);
      return createdSubtask;
    },
    [queryClient],
  );

  const handleUpdateSubtask = useCallback(
    async (subtask: TaskSubtask, payload: TaskUpdatePayload) => {
      const updatedSubtask = await updateTask(subtask.id, payload);

      if (subtask.parent_id !== null) {
        upsertSubtaskInCaches(queryClient, subtask.parent_id, updatedSubtask);
      }

      return updatedSubtask;
    },
    [queryClient],
  );

  const handleToggleSubtask = useCallback(
    async (subtask: TaskSubtask) => {
      const updatedSubtask = await toggleTask(subtask.id);

      if (subtask.parent_id !== null) {
        upsertSubtaskInCaches(queryClient, subtask.parent_id, updatedSubtask);

        if (subtask.repeat !== "none" && !subtask.is_done && updatedSubtask.is_done) {
          await queryClient.invalidateQueries({ queryKey: ["tasks"] });

          if (editingTask?.id === subtask.parent_id) {
            const refreshedParent = findTaskInTaskCaches(queryClient, subtask.parent_id);
            if (refreshedParent) {
              setEditingTask(refreshedParent);
            }
          }
        }
      }

      return updatedSubtask;
    },
    [editingTask?.id, queryClient],
  );

  const handleDeleteSubtask = useCallback(
    async (subtask: TaskSubtask) => {
      await deleteTask(subtask.id);

      if (subtask.parent_id !== null) {
        removeSubtaskFromCaches(queryClient, subtask.parent_id, subtask.id);
      }
    },
    [queryClient],
  );

  const handleReorderSubtasks = useCallback(
    async (task: TaskItem, subtaskIds: number[]) => {
      const updatedTask = await reorderSubtasks(task.id, subtaskIds);
      upsertTaskInCaches(queryClient, updatedTask);
      return updatedTask;
    },
    [queryClient],
  );

  const handleReorderTopLevelTasks = useCallback(
    async (scope: TaskTopLevelReorderScope, taskIds: number[]) => {
      const updatedTasks = await reorderTopLevelTasks(taskIds, scope);
      upsertTasksInCaches(queryClient, updatedTasks);

      if (editingTask) {
        const updatedEditingTask = updatedTasks.find((task) => task.id === editingTask.id);

        if (updatedEditingTask) {
          setEditingTask(updatedEditingTask);
        }
      }
    },
    [editingTask, queryClient],
  );

  const toggleTaskSubtasks = useCallback((taskId: number) => {
    setCollapsedSubtasks((current) => ({
      ...current,
      [taskId]: !current[taskId],
    }));
  }, []);

  const toggleTaskSection = useCallback((scope: string, sectionId: TaskSectionId) => {
    const storageKey = buildTaskSectionCollapseKey(scope, sectionId);

    setCollapsedTaskSections((current) => ({
      ...current,
      [storageKey]: !current[storageKey],
    }));
  }, []);

  const openCreateTaskDialogForSection = useCallback(
    (sectionId: TaskSectionId, overrides?: TaskDraftOverrides) => {
      if (sectionId === "pinned") {
        openCreateTaskDialog(overrides);
        return;
      }

      openCreateTaskDialog({
        ...overrides,
        priority: sectionId,
      });
    },
    [openCreateTaskDialog],
  );

  const openCreateTaskDialogForAllGroup = useCallback(
    (groupId: AllTaskGroupId) => {
      openCreateTaskDialog(getAllGroupDraftOverrides(groupId));
    },
    [openCreateTaskDialog],
  );

  const openCreateTaskDialogForAllGroupSection = useCallback(
    (groupId: AllTaskGroupId, sectionId: TaskSectionId) => {
      openCreateTaskDialogForSection(sectionId, getAllGroupDraftOverrides(groupId));
    },
    [openCreateTaskDialogForSection],
  );

  const handleRefetchTasks = useCallback(() => {
    void tasksQuery.refetch();
  }, [tasksQuery.refetch]);

  const Icon = pageMeta.icon;

  const syncEditingTask = useCallback(async () => {
    if (!editingTask) {
      return;
    }

    const refreshedEditingTask = findTaskInTaskCaches(queryClient, editingTask.id);
    if (refreshedEditingTask) {
      setEditingTask(refreshedEditingTask);
      return;
    }

    await queryClient.invalidateQueries({ queryKey: ["tasks"] });
    const invalidatedEditingTask = findTaskInTaskCaches(queryClient, editingTask.id);
    if (invalidatedEditingTask) {
      setEditingTask(invalidatedEditingTask);
    }
  }, [editingTask, queryClient]);

  const handleMoveTaskToParent = useCallback(
    async (taskId: number, parentTaskId: number, orderedIds: number[]) => {
      const result = await moveTask(taskId, {
        destination_parent_id: parentTaskId,
        ordered_ids: orderedIds,
      });

      applyTaskMoveResultInCaches(queryClient, result);
      await queryClient.invalidateQueries({ queryKey: ["tasks"] });
      await syncEditingTask();
    },
    [queryClient, syncEditingTask],
  );

  const handleMoveTaskToTopLevel = useCallback(
    async (taskId: number, scope: TaskTopLevelReorderScope, orderedIds: number[]) => {
      const result = await moveTask(taskId, {
        destination_scope: scope,
        ordered_ids: orderedIds,
      });

      applyTaskMoveResultInCaches(queryClient, result);
      await queryClient.invalidateQueries({ queryKey: ["tasks"] });
      await syncEditingTask();
    },
    [queryClient, syncEditingTask],
  );

  const pageContent = useMemo(
    () => (
      <div className="flex min-h-0 flex-col gap-4 lg:h-full">
        <div className="flex items-center gap-2">
          <div className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-card/80 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground shadow-sm backdrop-blur-xl">
            <Icon className="h-4 w-4" />
            <span>{pageMeta.title}</span>
            {pageCount !== null ? (
              <span className="rounded-full bg-background/80 px-2 py-0.5 text-[11px] font-medium normal-case tracking-normal text-muted-foreground">
                {pageCount}
              </span>
            ) : null}
          </div>

          {headerCreateOverrides ? (
            <Button size="icon" aria-label={`Create task in ${pageMeta.title}`} onClick={() => openCreateTaskDialog(headerCreateOverrides)}>
              <Plus className="h-4 w-4" />
            </Button>
          ) : null}
        </div>

        <section className="min-h-0 flex-1 overflow-hidden">
          {tasksQuery.isLoading || listsLoading ? (
            <div className="flex min-h-48 items-center justify-center rounded-[2rem] border border-border/70 bg-card/75 shadow-panel lg:h-full">
              <div className="flex items-center gap-3 text-sm text-muted-foreground">
                <Spinner />
                Loading tasks...
              </div>
            </div>
          ) : null}

          {tasksQuery.isError ? (
            <EmptyState
              title="Could not load tasks"
              description="The API request failed. Check that the backend is running and the database is reachable."
              action={
                <Button onClick={handleRefetchTasks}>
                  Retry
                </Button>
              }
            />
          ) : null}

          {!tasksQuery.isLoading && !tasksQuery.isError && hasInvalidListRoute ? (
            <EmptyState
              title="List not found"
              description="The selected list does not exist. Choose another list from the sidebar or create a new one."
              action={
                <Button onClick={() => openCreateTaskDialog()}>
                  <Plus className="h-4 w-4" />
                  Create task without a list
                </Button>
              }
            />
          ) : null}

          {!tasksQuery.isLoading && !tasksQuery.isError && !hasInvalidListRoute && visibleTasks.length === 0 ? (
            <EmptyState
              title={pageMeta.emptyTitle}
              description={pageMeta.emptyDescription}
              action={
                <Button onClick={() => openCreateTaskDialog(headerCreateOverrides ?? undefined)}>
                  <Plus className="h-4 w-4" />
                  Create task
                </Button>
              }
            />
          ) : null}

          {!tasksQuery.isLoading && !tasksQuery.isError && !hasInvalidListRoute && visibleTasks.length > 0 ? (
            mode === "all" ? (
              <div className={ALL_VIEW_COLUMNS_SCROLLER_CLASSNAME}>
                <div className={ALL_VIEW_COLUMNS_ROW_CLASSNAME}>
                  {groupedTasks.map((group) => (
                    <AllTaskGroupColumn
                      key={group.id}
                      group={group}
                      listById={listById}
                      todayString={todayString}
                      tomorrowString={tomorrowString}
                      collapsedTaskSections={collapsedTaskSections}
                      collapsedSubtasks={collapsedSubtasks}
                      onOpenCreateTaskDialog={openCreateTaskDialogForAllGroup}
                      onTaskToggle={handleTaskToggle}
                      onSubtaskToggle={handleToggleSubtask}
                      onTaskEdit={openEditTaskDialog}
                      onToggleSubtasks={toggleTaskSubtasks}
                      onToggleCollapsed={toggleTaskSection}
                      onCreateTaskInSection={openCreateTaskDialogForAllGroupSection}
                      onReorderTasks={handleReorderTopLevelTasks}
                      draggedTask={draggedTask}
                      onTaskDragStart={setDraggedTask}
                      onTaskDragEnd={clearDraggedTask}
                      onMoveTaskToParent={handleMoveTaskToParent}
                      onMoveTaskToTopLevel={handleMoveTaskToTopLevel}
                    />
                  ))}
                </div>
              </div>
            ) : mode === "today" ? (
              <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-[1.8rem] border border-border/70 bg-card/70 p-4 shadow-panel backdrop-blur-xl">
                <div className="hover-scrollbar min-h-0 flex-1 overflow-y-auto overscroll-y-contain pr-1">
                  <div className="space-y-6 pb-1">
                    {groupedTodayTasks.map((group) => (
                      <section key={group.id} className="space-y-4">
                        <div>
                          <div className="flex items-center gap-2">
                            <h3 className="text-lg font-semibold text-foreground">{group.title}</h3>
                            <span className="rounded-full bg-muted/60 px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                              {group.totalCount}
                            </span>
                          </div>
                          <p className="mt-1 text-sm text-muted-foreground">
                            {group.totalCount === 0
                              ? group.emptyDescription
                              : group.doneCount === 0
                                ? `${group.activeCount} active`
                                : `${group.activeCount} active, ${group.doneCount} done`}
                          </p>
                        </div>

                        {group.sections.map((section) => (
                          <TaskGroupSection
                            key={`${group.id}:${section.id}`}
                            title={section.title}
                            sectionId={section.id}
                            tasks={section.tasks}
                            collapsed={collapsedTaskSections[buildTaskSectionCollapseKey(group.id, section.id)] ?? false}
                            listById={listById}
                            todayString={todayString}
                            tomorrowString={tomorrowString}
                            collapsedSubtasks={collapsedSubtasks}
                            onTaskToggle={handleTaskToggle}
                            onSubtaskToggle={handleToggleSubtask}
                            onTaskEdit={openEditTaskDialog}
                            onToggleSubtasks={toggleTaskSubtasks}
                            onToggleCollapsed={() => toggleTaskSection(group.id, section.id)}
                            onCreateTaskInSection={
                              group.id === "today"
                                ? (sectionId) => openCreateTaskDialogForSection(sectionId, headerCreateOverrides ?? undefined)
                                : undefined
                            }
                            taskReorderScope={
                              group.id === "overdue"
                                ? {
                                    view: "all",
                                    group_id: "overdue",
                                    reference_date: todayString,
                                    section_id: section.id,
                                  }
                                : {
                                    view: "today",
                                    target_date: todayString,
                                    section_id: section.id,
                                  }
                            }
                            onReorderTasks={handleReorderTopLevelTasks}
                            draggedTask={draggedTask}
                            onTaskDragStart={setDraggedTask}
                            onTaskDragEnd={clearDraggedTask}
                            onMoveTaskToParent={handleMoveTaskToParent}
                            onMoveTaskToTopLevel={handleMoveTaskToTopLevel}
                          />
                        ))}

                        <TaskDoneSection
                          tasks={group.doneTasks}
                          listById={listById}
                          todayString={todayString}
                          tomorrowString={tomorrowString}
                          collapsedSubtasks={collapsedSubtasks}
                          onTaskToggle={handleTaskToggle}
                          onSubtaskToggle={handleToggleSubtask}
                          onTaskEdit={openEditTaskDialog}
                          onToggleSubtasks={toggleTaskSubtasks}
                        />
                      </section>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div className="flex h-full min-h-0 flex-col overflow-hidden rounded-[1.8rem] border border-border/70 bg-card/70 p-4 shadow-panel backdrop-blur-xl">
                <div className="hover-scrollbar min-h-0 flex-1 overflow-y-auto overscroll-y-contain pr-1">
                  <div className="space-y-5 pb-1">
                    {groupedSections?.sections.map((section) => (
                      <TaskGroupSection
                        key={section.id}
                        title={section.title}
                        sectionId={section.id}
                        tasks={section.tasks}
                        collapsed={collapsedTaskSections[buildTaskSectionCollapseKey(taskSectionScope, section.id)] ?? false}
                        listById={listById}
                        todayString={todayString}
                        tomorrowString={tomorrowString}
                        collapsedSubtasks={collapsedSubtasks}
                        onTaskToggle={handleTaskToggle}
                        onSubtaskToggle={handleToggleSubtask}
                        onTaskEdit={openEditTaskDialog}
                        onToggleSubtasks={toggleTaskSubtasks}
                        onToggleCollapsed={() => toggleTaskSection(taskSectionScope, section.id)}
                        onCreateTaskInSection={(sectionId) =>
                          openCreateTaskDialogForSection(sectionId, headerCreateOverrides ?? undefined)
                        }
                        taskReorderScope={
                          buildTopLevelTaskDropScope({
                            mode,
                            sectionId: section.id,
                            todayString,
                            tomorrowString,
                            listId,
                          }) ?? undefined
                        }
                        onReorderTasks={handleReorderTopLevelTasks}
                        draggedTask={draggedTask}
                        onTaskDragStart={setDraggedTask}
                        onTaskDragEnd={clearDraggedTask}
                        onMoveTaskToParent={handleMoveTaskToParent}
                        onMoveTaskToTopLevel={handleMoveTaskToTopLevel}
                      />
                    ))}

                    {groupedSections ? (
                      <TaskDoneSection
                        tasks={groupedSections.doneTasks}
                        listById={listById}
                        todayString={todayString}
                        tomorrowString={tomorrowString}
                        collapsedSubtasks={collapsedSubtasks}
                        onTaskToggle={handleTaskToggle}
                        onSubtaskToggle={handleToggleSubtask}
                        onTaskEdit={openEditTaskDialog}
                        onToggleSubtasks={toggleTaskSubtasks}
                      />
                    ) : null}
                  </div>
                </div>
              </div>
            )
          ) : null}
        </section>
      </div>
    ),
    [
      Icon,
      clearDraggedTask,
      collapsedSubtasks,
      collapsedTaskSections,
      draggedTask,
      groupedSections,
      groupedTasks,
      groupedTodayTasks,
      handleMoveTaskToParent,
      handleMoveTaskToTopLevel,
      handleReorderTopLevelTasks,
      handleRefetchTasks,
      handleTaskToggle,
      handleToggleSubtask,
      hasInvalidListRoute,
      headerCreateOverrides,
      listById,
      listId,
      listsLoading,
      mode,
      openCreateTaskDialog,
      openCreateTaskDialogForAllGroup,
      openCreateTaskDialogForAllGroupSection,
      openCreateTaskDialogForSection,
      openEditTaskDialog,
      pageCount,
      pageMeta.emptyDescription,
      pageMeta.emptyTitle,
      pageMeta.title,
      taskSectionScope,
      tasksQuery.isError,
      tasksQuery.isLoading,
      toggleTaskSection,
      toggleTaskSubtasks,
      todayString,
      tomorrowString,
      visibleTasks.length,
    ],
  );

  return (
    <>
      {pageContent}

      <TaskDialog
        open={taskDialogOpen}
        task={editingTask}
        defaultDraft={dialogDraft}
        lists={lists}
        subtasksCollapsed={editingTask ? collapsedSubtasks[editingTask.id] ?? false : false}
        onOpenChange={closeTaskDialog}
        onCreateTask={handleTaskCreate}
        onUpdateTask={handleTaskUpdate}
        onToggleTask={handleTaskToggle}
        onDelete={handleTaskDelete}
        onCreateSubtask={handleCreateSubtask}
        onUpdateSubtask={handleUpdateSubtask}
        onToggleSubtask={handleToggleSubtask}
        onDeleteSubtask={handleDeleteSubtask}
        onReorderSubtasks={handleReorderSubtasks}
        onToggleSubtasks={toggleTaskSubtasks}
      />
    </>
  );
}
