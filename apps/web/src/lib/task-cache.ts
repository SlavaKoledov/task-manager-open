import type { QueryClient, QueryKey } from "@tanstack/react-query";

import type { TaskItem, TaskMoveResult, TaskPriority, TaskSubtask } from "@/lib/types";

const TASK_PRIORITY_ORDER: Record<TaskPriority, number> = {
  urgent_important: 0,
  not_urgent_important: 1,
  urgent_unimportant: 2,
  not_urgent_unimportant: 3,
};

type TaskQueryDescriptor =
  | { kind: "all" }
  | { kind: "today"; value: string }
  | { kind: "tomorrow"; value: string }
  | { kind: "inbox" }
  | { kind: "list"; value: number };

function compareTasks(left: TaskItem, right: TaskItem) {
  if (left.is_pinned !== right.is_pinned) {
    return left.is_pinned ? -1 : 1;
  }

  if (left.is_done !== right.is_done) {
    return left.is_done ? 1 : -1;
  }

  const priorityDelta = TASK_PRIORITY_ORDER[left.priority] - TASK_PRIORITY_ORDER[right.priority];
  if (priorityDelta !== 0) {
    return priorityDelta;
  }

  if (left.position !== right.position) {
    return left.position - right.position;
  }

  const createdAtDelta = right.created_at.localeCompare(left.created_at);
  if (createdAtDelta !== 0) {
    return createdAtDelta;
  }

  return right.id - left.id;
}

function compareSubtasks(left: TaskSubtask, right: TaskSubtask) {
  if (left.position !== right.position) {
    return left.position - right.position;
  }

  const createdAtDelta = left.created_at.localeCompare(right.created_at);
  if (createdAtDelta !== 0) {
    return createdAtDelta;
  }

  return left.id - right.id;
}

function sortTasks(tasks: TaskItem[]) {
  return [...tasks].sort(compareTasks);
}

function normalizeSubtasks(subtasks: TaskSubtask[]) {
  return [...subtasks].sort(compareSubtasks);
}

function normalizeTask(task: TaskItem): TaskItem {
  return {
    ...task,
    subtasks: normalizeSubtasks(task.subtasks),
  };
}

function parseTaskQueryDescriptor(queryKey: QueryKey): TaskQueryDescriptor | null {
  if (!Array.isArray(queryKey) || queryKey[0] !== "tasks") {
    return null;
  }

  if (queryKey[1] === "all") {
    return { kind: "all" };
  }

  if (queryKey[1] === "today" && typeof queryKey[2] === "string") {
    return { kind: "today", value: queryKey[2] };
  }

  if (queryKey[1] === "tomorrow" && typeof queryKey[2] === "string") {
    return { kind: "tomorrow", value: queryKey[2] };
  }

  if (queryKey[1] === "inbox") {
    return { kind: "inbox" };
  }

  if (queryKey[1] === "list" && typeof queryKey[2] === "number") {
    return { kind: "list", value: queryKey[2] };
  }

  return null;
}

function taskMatchesQuery(task: TaskItem, descriptor: TaskQueryDescriptor) {
  if (descriptor.kind === "all") {
    return true;
  }

  if (descriptor.kind === "today" || descriptor.kind === "tomorrow") {
    return task.due_date === descriptor.value;
  }

  if (descriptor.kind === "inbox") {
    return task.due_date === null;
  }

  return task.list_id === descriptor.value;
}

function updateTaskQueries(
  queryClient: QueryClient,
  updater: (tasks: TaskItem[], descriptor: TaskQueryDescriptor) => TaskItem[],
) {
  for (const [queryKey, tasks] of queryClient.getQueriesData<TaskItem[]>({ queryKey: ["tasks"] })) {
    const descriptor = parseTaskQueryDescriptor(queryKey);

    if (!descriptor || !tasks) {
      continue;
    }

    const nextTasks = updater(tasks, descriptor);
    if (nextTasks !== tasks) {
      queryClient.setQueryData(queryKey, nextTasks);
    }
  }
}

export function findTaskInTaskCaches(queryClient: QueryClient, taskId: number): TaskItem | null {
  for (const [, tasks] of queryClient.getQueriesData<TaskItem[]>({ queryKey: ["tasks"] })) {
    if (!tasks) {
      continue;
    }

    const matchingTask = tasks.find((task) => task.id === taskId);
    if (matchingTask) {
      return matchingTask;
    }
  }

  return null;
}

function upsertSubtask(subtasks: TaskSubtask[], subtask: TaskSubtask) {
  const nextSubtask = { ...subtask };
  const currentIndex = subtasks.findIndex((item) => item.id === nextSubtask.id);

  if (currentIndex === -1) {
    return normalizeSubtasks([...subtasks, nextSubtask]);
  }

  const nextSubtasks = [...subtasks];
  nextSubtasks[currentIndex] = nextSubtask;
  return normalizeSubtasks(nextSubtasks);
}

export function upsertTaskInCaches(queryClient: QueryClient, task: TaskItem) {
  const nextTask = normalizeTask(task);

  updateTaskQueries(queryClient, (tasks, descriptor) => {
    const currentIndex = tasks.findIndex((item) => item.id === nextTask.id);
    const shouldInclude = taskMatchesQuery(nextTask, descriptor);

    if (!shouldInclude && currentIndex === -1) {
      return tasks;
    }

    if (!shouldInclude) {
      return tasks.filter((item) => item.id !== nextTask.id);
    }

    if (currentIndex === -1) {
      return sortTasks([...tasks, nextTask]);
    }

    const nextTasks = [...tasks];
    nextTasks[currentIndex] = nextTask;
    return sortTasks(nextTasks);
  });
}

export function upsertTasksInCaches(queryClient: QueryClient, nextTasks: TaskItem[]) {
  if (nextTasks.length === 0) {
    return;
  }

  const tasksById = new Map(nextTasks.map((task) => [task.id, normalizeTask(task)]));

  updateTaskQueries(queryClient, (tasks, descriptor) => {
    let changed = false;

    const updatedTasks = tasks.map((task) => {
      const nextTask = tasksById.get(task.id);

      if (!nextTask) {
        return task;
      }

      if (!taskMatchesQuery(nextTask, descriptor)) {
        changed = true;
        return null;
      }

      changed = true;
      return nextTask;
    });

    if (!changed) {
      return tasks;
    }

    return sortTasks(updatedTasks.filter((task): task is TaskItem => task !== null));
  });
}

export function removeTaskFromCaches(queryClient: QueryClient, taskId: number) {
  updateTaskQueries(queryClient, (tasks) => {
    const currentIndex = tasks.findIndex((item) => item.id === taskId);

    if (currentIndex === -1) {
      return tasks;
    }

    return tasks.filter((item) => item.id !== taskId);
  });
}

export function upsertSubtaskInCaches(queryClient: QueryClient, parentTaskId: number, subtask: TaskSubtask) {
  updateTaskQueries(queryClient, (tasks) => {
    let changed = false;

    const nextTasks = tasks.map((task) => {
      if (task.id !== parentTaskId) {
        return task;
      }

      changed = true;
      return {
        ...task,
        subtasks: upsertSubtask(task.subtasks, subtask),
      };
    });

    return changed ? nextTasks : tasks;
  });
}

export function removeSubtaskFromCaches(queryClient: QueryClient, parentTaskId: number, subtaskId: number) {
  updateTaskQueries(queryClient, (tasks) => {
    let changed = false;

    const nextTasks = tasks.map((task) => {
      if (task.id !== parentTaskId) {
        return task;
      }

      const remainingSubtasks = task.subtasks.filter((subtask) => subtask.id !== subtaskId);
      if (remainingSubtasks.length === task.subtasks.length) {
        return task;
      }

      changed = true;
      return {
        ...task,
        subtasks: normalizeSubtasks(
          remainingSubtasks.map((subtask, index) =>
            subtask.position === index ? subtask : { ...subtask, position: index },
          ),
        ),
      };
    });

    return changed ? nextTasks : tasks;
  });
}

export function clearListFromTaskCaches(queryClient: QueryClient, listId: number) {
  const allTasks = queryClient.getQueryData<TaskItem[]>(["tasks", "all"]) ?? [];
  const affectedTasks = allTasks.filter((task) => task.list_id === listId);

  for (const task of affectedTasks) {
    upsertTaskInCaches(queryClient, {
      ...task,
      list_id: null,
      subtasks: task.subtasks.map((subtask) =>
        subtask.list_id === listId ? { ...subtask, list_id: null } : subtask,
      ),
    });
  }

  queryClient.setQueryData(["tasks", "list", listId], []);
}

export function applyTaskMoveResultInCaches(queryClient: QueryClient, result: TaskMoveResult) {
  if (result.affected_tasks.length > 0) {
    upsertTasksInCaches(queryClient, result.affected_tasks);
  }

  for (const taskId of result.removed_top_level_task_ids) {
    removeTaskFromCaches(queryClient, taskId);
  }
}
