import { parseLocalDateString } from "@/lib/date";
import { descriptionBlocksToText, stripDescriptionBlocks } from "@/lib/task-description";
import type { TaskCreatePayload, TaskDraft, TaskSubtask, TaskUpdatePayload } from "@/lib/types";

export function validateTaskDraft(
  draft: Pick<TaskDraft, "title" | "due_date" | "reminder_time" | "repeat" | "repeat_until">,
): string | null {
  if (!draft.title.trim()) {
    return "Task title is required.";
  }

  if (draft.repeat_until) {
    if (draft.repeat === "none") {
      return "Choose a repeat schedule before setting a repeat end date.";
    }

    if (!draft.due_date) {
      return "Choose a due date before setting a repeat end date.";
    }

    const dueDate = parseLocalDateString(draft.due_date);
    const repeatUntil = parseLocalDateString(draft.repeat_until);

    if (dueDate && repeatUntil && repeatUntil < dueDate) {
      return "Repeat end date cannot be earlier than the due date.";
    }
  }

  if (!draft.due_date && draft.repeat !== "none") {
    return "Choose a due date before setting a repeat schedule.";
  }

  if (!draft.due_date && draft.reminder_time) {
    return "Choose a due date before setting a reminder.";
  }

  return null;
}

function buildNormalizedTaskFields(
  draft: TaskDraft,
  descriptionBlocks = draft.description_blocks,
): Omit<TaskCreatePayload, "parent_id" | "subtasks"> {
  const normalizedBlocks = stripDescriptionBlocks(descriptionBlocks);

  return {
    title: draft.title.trim(),
    description: descriptionBlocksToText(normalizedBlocks),
    description_blocks: normalizedBlocks,
    due_date: draft.due_date || null,
    reminder_time: draft.due_date ? draft.reminder_time || null : null,
    repeat_until: draft.repeat === "none" ? null : draft.repeat_until || null,
    is_done: draft.is_done,
    is_pinned: draft.is_pinned,
    priority: draft.priority,
    repeat: draft.repeat,
    list_id: draft.list_id ? Number(draft.list_id) : null,
  };
}

export function buildTaskCreatePayloadFromDraft(
  draft: TaskDraft,
  descriptionBlocks = draft.description_blocks,
  subtasks: TaskSubtask[] = [],
): TaskCreatePayload {
  return {
    ...buildNormalizedTaskFields(draft, descriptionBlocks),
    parent_id: null,
    subtasks: subtasks
      .map((subtask) => {
        const normalizedBlocks = stripDescriptionBlocks(subtask.description_blocks);

        return {
          title: subtask.title.trim(),
          description: descriptionBlocksToText(normalizedBlocks),
          description_blocks: normalizedBlocks,
          due_date: subtask.due_date,
          reminder_time: subtask.reminder_time,
          is_done: subtask.is_done,
        };
      })
      .filter((subtask) => subtask.title.length > 0),
  };
}

export function buildTaskUpdatePayloadFromDraft(
  draft: TaskDraft,
  descriptionBlocks = draft.description_blocks,
): TaskUpdatePayload {
  return buildNormalizedTaskFields(draft, descriptionBlocks);
}
