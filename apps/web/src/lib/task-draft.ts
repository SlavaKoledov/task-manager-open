import { createEmptyDescription } from "@/lib/task-description";
import type { TaskDraft, TaskPriority, ViewMode } from "@/lib/types";

export type TaskDraftOverrides = {
  due_date?: string | null;
  list_id?: number | null;
  priority?: TaskPriority;
};

export function buildTaskDraft(
  mode: ViewMode,
  todayString: string,
  tomorrowString: string,
  listId?: number | null,
  overrides?: TaskDraftOverrides,
): TaskDraft {
  const defaultDueDate = mode === "today" ? todayString : mode === "tomorrow" ? tomorrowString : "";
  const resolvedDueDate = overrides?.due_date === undefined ? defaultDueDate : overrides.due_date ?? "";
  const defaultListId = mode === "list" && listId ? listId : null;
  const resolvedListId = overrides?.list_id === undefined ? defaultListId : overrides.list_id ?? null;
  const resolvedPriority = overrides?.priority ?? "not_urgent_unimportant";

  return {
    title: "",
    description_blocks: createEmptyDescription(),
    due_date: resolvedDueDate,
    reminder_time: "",
    repeat_config: null,
    repeat_until: "",
    is_done: false,
    is_pinned: false,
    priority: resolvedPriority,
    repeat: "none",
    list_id: resolvedListId ? String(resolvedListId) : "",
  };
}
