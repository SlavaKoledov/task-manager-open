import { ensureDescriptionBlocks } from "@/lib/task-description";
import type {
  DescriptionBlock,
  LiveEvent,
  ListItem,
  ListPayload,
  TaskCreatePayload,
  TaskItem,
  TaskMovePayload,
  TaskMoveResult,
  TaskSubtask,
  TaskTopLevelReorderScope,
  TaskUpdatePayload,
} from "@/lib/types";

const RAW_API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";
const API_BASE_URL = RAW_API_BASE_URL.endsWith("/") ? RAW_API_BASE_URL.slice(0, -1) : RAW_API_BASE_URL;

type ApiTask = Omit<TaskSubtask, "description_blocks"> & {
  description_blocks?: DescriptionBlock[] | null;
  subtasks?: ApiTask[];
};

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export function getApiBaseUrl(): string {
  return `${API_BASE_URL}/`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : await response.text();

  if (!response.ok) {
    const detail =
      typeof body === "string"
        ? body
        : typeof body?.detail === "string"
          ? body.detail
          : "The request failed.";

    throw new ApiError(detail, response.status);
  }

  return body as T;
}

function normalizeSubtask(task: ApiTask): TaskSubtask {
  return {
    id: task.id,
    title: task.title,
    description: task.description,
    description_blocks: ensureDescriptionBlocks(task.description_blocks, task.description),
    due_date: task.due_date,
    start_time: task.start_time ?? null,
    end_time: task.end_time ?? null,
    reminder_time: task.reminder_time ?? null,
    repeat_config: task.repeat_config ?? null,
    repeat_until: task.repeat_until,
    is_done: task.is_done,
    is_pinned: task.is_pinned,
    priority: task.priority,
    repeat: task.repeat,
    parent_id: task.parent_id,
    position: task.position,
    list_id: task.list_id,
    created_at: task.created_at,
    updated_at: task.updated_at,
  };
}

function normalizeTask(task: ApiTask): TaskItem {
  return {
    ...normalizeSubtask(task),
    subtasks: (task.subtasks ?? []).map(normalizeSubtask),
  };
}

function prepareTaskPayload<T extends TaskCreatePayload | TaskUpdatePayload>(payload: T): T {
  if (!("description_blocks" in payload) || payload.description_blocks === undefined) {
    return payload;
  }

  return {
    ...payload,
    description_blocks: payload.description_blocks,
    repeat_config: "repeat_config" in payload ? payload.repeat_config ?? null : undefined,
  } as T;
}

export function getLists() {
  return request<ListItem[]>("/lists");
}

export function createList(payload: ListPayload) {
  return request<ListItem>("/lists", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateList(id: number, payload: Partial<ListPayload>) {
  return request<ListItem>(`/lists/${id}`, {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export async function reorderLists(listIds: number[]) {
  return await request<ListItem[]>("/lists/reorder", {
    method: "POST",
    body: JSON.stringify({ list_ids: listIds }),
  });
}

export function deleteList(id: number) {
  return request<void>(`/lists/${id}`, {
    method: "DELETE",
  });
}

export async function getTasks() {
  return (await request<ApiTask[]>("/tasks")).map(normalizeTask);
}

export async function getTodayTasks(today: string) {
  return (await request<ApiTask[]>(`/tasks/today?today=${encodeURIComponent(today)}`)).map(normalizeTask);
}

export async function getTomorrowTasks(tomorrow: string) {
  return (await request<ApiTask[]>(`/tasks/tomorrow?tomorrow=${encodeURIComponent(tomorrow)}`)).map(normalizeTask);
}

export async function getInboxTasks() {
  return (await request<ApiTask[]>("/tasks/inbox")).map(normalizeTask);
}

export async function getListTasks(listId: number) {
  return (await request<ApiTask[]>(`/lists/${listId}/tasks`)).map(normalizeTask);
}

export async function createTask(payload: TaskCreatePayload) {
  return normalizeTask(
    await request<ApiTask>("/tasks", {
      method: "POST",
      body: JSON.stringify(prepareTaskPayload(payload)),
    }),
  );
}

export async function updateTask(id: number, payload: TaskUpdatePayload) {
  return normalizeTask(
    await request<ApiTask>(`/tasks/${id}`, {
      method: "PATCH",
      body: JSON.stringify(prepareTaskPayload(payload)),
    }),
  );
}

export function deleteTask(id: number) {
  return request<void>(`/tasks/${id}`, {
    method: "DELETE",
  });
}

export async function toggleTask(id: number) {
  return normalizeTask(
    await request<ApiTask>(`/tasks/${id}/toggle`, {
      method: "POST",
    }),
  );
}

export async function reorderTopLevelTasks(taskIds: number[], scope: TaskTopLevelReorderScope) {
  return (await request<ApiTask[]>("/tasks/reorder", {
    method: "POST",
    body: JSON.stringify({ task_ids: taskIds, scope }),
  })).map(normalizeTask);
}

export async function reorderSubtasks(parentTaskId: number, subtaskIds: number[]) {
  return normalizeTask(
    await request<ApiTask>(`/tasks/${parentTaskId}/subtasks/reorder`, {
      method: "POST",
      body: JSON.stringify({ subtask_ids: subtaskIds }),
    }),
  );
}

export async function moveTask(taskId: number, payload: TaskMovePayload) {
  const response = await request<{
    task: ApiTask;
    affected_tasks: ApiTask[];
    removed_top_level_task_ids: number[];
  }>(`/tasks/${taskId}/move`, {
    method: "POST",
    body: JSON.stringify(payload),
  });

  const normalizedResult: TaskMoveResult = {
    task: normalizeSubtask(response.task),
    affected_tasks: response.affected_tasks.map(normalizeTask),
    removed_top_level_task_ids: response.removed_top_level_task_ids,
  };

  return normalizedResult;
}

export function createLiveEventSource() {
  return new EventSource(`${getApiBaseUrl()}events`);
}

export function parseLiveEvent(rawEvent: MessageEvent<string>): LiveEvent | null {
  try {
    const parsed = JSON.parse(rawEvent.data) as LiveEvent;
    if (parsed.entity_type !== "task" && parsed.entity_type !== "list") {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}
