import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { flushSync } from "react-dom";

import { PriorityCheckbox } from "@/components/priority-checkbox";
import { SubtaskList, type SubtaskListHandle } from "@/components/subtask-list";
import { TaskActionsMenu } from "@/components/task-actions-menu";
import { TaskDescriptionDialog } from "@/components/task-description-dialog";
import { TaskDateRepeatControl } from "@/components/task-date-repeat-control";
import { TaskListMoveMenu } from "@/components/task-list-move-menu";
import { TaskPrioritySelector } from "@/components/task-priority-selector";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api";
import { descriptionBlocksToText, descriptionTextToBlocks, ensureDescriptionBlocks } from "@/lib/task-description";
import {
  buildTaskCreatePayloadFromDraft,
  buildTaskUpdatePayloadFromDraft,
  validateTaskDraft,
} from "@/lib/task-dialog-state";
import { buildDefaultCustomRepeatConfig } from "@/lib/task-repeat";
import { getSubtaskProgressSummary } from "@/lib/task-progress";
import type {
  ListItem,
  TaskCreatePayload,
  TaskDraft,
  TaskItem,
  TaskSubtask,
  TaskUpdatePayload,
} from "@/lib/types";

type TaskDialogProps = {
  open: boolean;
  task: TaskItem | null;
  defaultDraft: TaskDraft;
  lists: ListItem[];
  subtasksCollapsed: boolean;
  onOpenChange: (open: boolean) => void;
  onCreateTask: (payload: TaskCreatePayload) => Promise<void>;
  onUpdateTask: (task: TaskItem, payload: TaskUpdatePayload) => Promise<TaskItem>;
  onDelete?: (task: TaskItem) => Promise<void>;
  onCreateSubtask: (task: TaskItem, title: string) => Promise<TaskSubtask>;
  onUpdateSubtask: (subtask: TaskSubtask, payload: TaskUpdatePayload) => Promise<TaskSubtask>;
  onToggleSubtask: (subtask: TaskSubtask) => Promise<TaskSubtask>;
  onDeleteSubtask: (subtask: TaskSubtask) => Promise<void>;
  onReorderSubtasks: (task: TaskItem, subtaskIds: number[]) => Promise<TaskItem>;
  onToggleSubtasks: (taskId: number) => void;
};

const TITLE_TEXTAREA_MAX_HEIGHT = 144;

function resizeTitleTextarea(textarea: HTMLTextAreaElement | null) {
  if (!textarea) {
    return;
  }

  textarea.style.height = "0px";
  const nextHeight = Math.min(textarea.scrollHeight, TITLE_TEXTAREA_MAX_HEIGHT);
  textarea.style.height = `${nextHeight}px`;
  textarea.style.overflowY = textarea.scrollHeight > TITLE_TEXTAREA_MAX_HEIGHT ? "auto" : "hidden";
}

function buildLocalDraftSubtask(id: number, title: string, position: number, draft: TaskDraft): TaskSubtask {
  return {
    id,
    title,
    description: null,
    description_blocks: [],
    due_date: null,
    start_time: null,
    end_time: null,
    reminder_time: null,
    repeat_config: null,
    repeat_until: null,
    is_done: false,
    is_pinned: false,
    priority: draft.priority,
    repeat: "none",
    parent_id: null,
    position,
    list_id: draft.list_id ? Number(draft.list_id) : null,
    created_at: "",
    updated_at: "",
  };
}

export function TaskDialog({
  open,
  task,
  defaultDraft,
  lists,
  onOpenChange,
  onCreateTask,
  onUpdateTask,
  onDelete,
  onCreateSubtask,
  onUpdateSubtask,
  onToggleSubtask,
  onDeleteSubtask,
  onReorderSubtasks,
}: TaskDialogProps) {
  const [draft, setDraft] = useState<TaskDraft>(defaultDraft);
  const [subtasks, setSubtasks] = useState<TaskSubtask[]>(task?.subtasks ?? []);
  const [subtaskAddKey, setSubtaskAddKey] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [descriptionDialogOpen, setDescriptionDialogOpen] = useState(false);
  const [editorSubtasksCollapsed, setEditorSubtasksCollapsed] = useState(false);
  const titleRef = useRef<HTMLTextAreaElement | null>(null);
  const subtaskListRef = useRef<SubtaskListHandle | null>(null);
  const localSubtaskIdRef = useRef(-1);
  const subtaskProgress = getSubtaskProgressSummary(subtasks);

  useEffect(() => {
    if (!open) {
      return;
    }

    if (task) {
      const nextDescriptionBlocks = ensureDescriptionBlocks(task.description_blocks, task.description);

        setDraft({
          title: task.title,
          description_blocks: nextDescriptionBlocks,
          due_date: task.due_date ?? "",
          start_time: task.start_time ?? "",
          end_time: task.end_time ?? "",
          reminder_time: task.reminder_time ?? "",
          repeat_config: task.repeat_config ?? null,
          repeat_until: task.repeat_until ?? "",
        is_done: task.is_done,
        is_pinned: task.is_pinned,
        priority: task.priority,
        repeat: task.repeat,
        list_id: task.list_id ? String(task.list_id) : "",
      });
      setSubtasks(task.subtasks);
      localSubtaskIdRef.current = -1;
    } else {
      setDraft(defaultDraft);
      setSubtasks([]);
      localSubtaskIdRef.current = -1;
    }

    setDescriptionDialogOpen(false);
    setEditorSubtasksCollapsed(false);
    setError(null);
    setIsSubmitting(false);

    window.requestAnimationFrame(() => resizeTitleTextarea(titleRef.current));
  }, [defaultDraft, open, task]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const pendingSubtaskTitle = await subtaskListRef.current?.commitDraft();
    const createModeSubtasks =
      !task && pendingSubtaskTitle
        ? [...subtasks, buildLocalDraftSubtask(0, pendingSubtaskTitle, subtasks.length, draft)]
        : subtasks;

    const validationError = validateTaskDraft(draft);
    if (validationError) {
      setError(validationError);
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      if (task) {
        await onUpdateTask(task, buildTaskUpdatePayloadFromDraft(draft));
      } else {
        await onCreateTask(buildTaskCreatePayloadFromDraft(draft, undefined, createModeSubtasks));
      }

      onOpenChange(false);
    } catch (caughtError) {
      setError(caughtError instanceof ApiError ? caughtError.message : "Failed to save the task.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!task || !onDelete) {
      return;
    }

    if (!window.confirm(`Delete "${task.title}"?`)) {
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await onDelete(task);
      onOpenChange(false);
    } catch (caughtError) {
      setError(caughtError instanceof ApiError ? caughtError.message : "Failed to delete the task.");
    } finally {
      setIsSubmitting(false);
    }
  }

  const handleInlineUpdate = useCallback(
    async (payload: TaskUpdatePayload, applyToDraft?: (nextTask: TaskItem) => void) => {
      if (!task) {
        return;
      }

      setError(null);

      try {
        const updatedTask = await onUpdateTask(task, payload);
        applyToDraft?.(updatedTask);
      } catch (caughtError) {
        setError(caughtError instanceof ApiError ? caughtError.message : "Failed to update the task.");
      }
    },
    [onUpdateTask, task],
  );

  const handleDescriptionTextChange = useCallback((nextText: string) => {
    setDraft((current) => ({ ...current, description_blocks: descriptionTextToBlocks(nextText) }));
  }, []);

  const handleTitleChange = useCallback((nextTitle: string, textarea: HTMLTextAreaElement | null) => {
    resizeTitleTextarea(textarea);
    setDraft((current) => ({ ...current, title: nextTitle }));
  }, []);

  const handleDoneChange = useCallback((checked: boolean) => {
    setDraft((current) => ({ ...current, is_done: checked }));
  }, []);

  const handleDateChange = useCallback((nextDate: string) => {
    setDraft((current) => ({
      ...current,
      due_date: nextDate,
      start_time: nextDate ? current.start_time : "",
      end_time: nextDate ? current.end_time : "",
      reminder_time: nextDate ? current.reminder_time : "",
      repeat_until: !nextDate || (current.repeat_until && current.repeat_until < nextDate) ? "" : current.repeat_until,
      repeat: nextDate ? current.repeat : "none",
      repeat_config: current.repeat_config,
    }));
  }, []);

  const handleStartTimeChange = useCallback((nextStartTime: string) => {
    setDraft((current) => ({
      ...current,
      start_time: nextStartTime,
      end_time: nextStartTime ? current.end_time : "",
    }));
  }, []);

  const handleEndTimeChange = useCallback((nextEndTime: string) => {
    setDraft((current) => ({ ...current, end_time: nextEndTime }));
  }, []);

  const handleReminderTimeChange = useCallback((nextReminderTime: string) => {
    setDraft((current) => ({ ...current, reminder_time: nextReminderTime }));
  }, []);

  const handleRepeatChange = useCallback((nextRepeat: TaskDraft["repeat"]) => {
    setDraft((current) => ({
      ...current,
      repeat: nextRepeat,
      repeat_until: nextRepeat === "none" ? "" : current.repeat_until,
      repeat_config:
        nextRepeat === "custom"
          ? current.repeat_config ?? buildDefaultCustomRepeatConfig("day", current.due_date || defaultDraft.due_date || "")
          : current.repeat_config,
    }));
  }, [defaultDraft.due_date]);

  const handleRepeatConfigChange = useCallback((nextRepeatConfig: TaskDraft["repeat_config"]) => {
    setDraft((current) => ({ ...current, repeat_config: nextRepeatConfig }));
  }, []);

  const handleRepeatUntilChange = useCallback((nextRepeatUntil: string) => {
    setDraft((current) => ({ ...current, repeat_until: nextRepeatUntil }));
  }, []);

  const handlePriorityChange = useCallback((nextPriority: TaskDraft["priority"]) => {
    setDraft((current) => ({ ...current, priority: nextPriority }));
  }, []);

  const handleListChange = useCallback(
    (nextListId: number | null) => {
      setDraft((current) => ({ ...current, list_id: nextListId ? String(nextListId) : "" }));

      if (!task) {
        return;
      }

      void handleInlineUpdate({ list_id: nextListId }, () => undefined);
    },
    [handleInlineUpdate, task],
  );

  const handlePinToggle = useCallback(() => {
    const nextPinned = !draft.is_pinned;
    setDraft((current) => ({ ...current, is_pinned: nextPinned }));

    if (!task) {
      return;
    }

    void handleInlineUpdate({ is_pinned: nextPinned }, () => undefined);
  }, [draft.is_pinned, handleInlineUpdate, task]);

  const handleSubtaskCreate = useCallback(
    async (title: string) => {
      if (!title.trim()) {
        return;
      }

      if (!task) {
        flushSync(() => {
          setSubtasks((current) => [
            ...current,
            buildLocalDraftSubtask(localSubtaskIdRef.current--, title.trim(), current.length, draft),
          ]);
        });
        return;
      }

      const createdSubtask = await onCreateSubtask(task, title);
      setSubtasks((current) => [...current, createdSubtask].sort((left, right) => left.position - right.position));
    },
    [draft, onCreateSubtask, task],
  );

  const handleSubtaskUpdate = useCallback(
    async (subtask: TaskSubtask, title: string) => {
      if (!task) {
        const nextTitle = title.trim();
        if (!nextTitle) {
          return;
        }

        setSubtasks((current) => current.map((item) => (item.id === subtask.id ? { ...item, title: nextTitle } : item)));
        return;
      }

      const updatedSubtask = await onUpdateSubtask(subtask, { title });
      setSubtasks((current) => current.map((item) => (item.id === updatedSubtask.id ? updatedSubtask : item)));
    },
    [onUpdateSubtask, task],
  );

  const handleSubtaskToggle = useCallback(
    async (subtask: TaskSubtask) => {
      if (!task) {
        setSubtasks((current) =>
          current.map((item) => (item.id === subtask.id ? { ...item, is_done: !item.is_done } : item)),
        );
        return;
      }

      const updatedSubtask = await onToggleSubtask(subtask);
      setSubtasks((current) => current.map((item) => (item.id === updatedSubtask.id ? updatedSubtask : item)));
    },
    [onToggleSubtask, task],
  );

  const handleSubtaskDelete = useCallback(
    async (subtask: TaskSubtask) => {
      if (!task) {
        setSubtasks((current) =>
          current
            .filter((item) => item.id !== subtask.id)
            .map((item, index) => ({ ...item, position: index })),
        );
        return;
      }

      await onDeleteSubtask(subtask);
      setSubtasks((current) => current.filter((item) => item.id !== subtask.id));
    },
    [onDeleteSubtask, task],
  );

  const handleSubtaskReorder = useCallback(
    async (subtaskIds: number[]) => {
      if (!task) {
        const positions = new Map(subtaskIds.map((subtaskId, index) => [subtaskId, index]));
        setSubtasks((current) =>
          current
            .map((item) => ({ ...item, position: positions.get(item.id) ?? item.position }))
            .sort((left, right) => left.position - right.position),
        );
        return;
      }

      const updatedTask = await onReorderSubtasks(task, subtaskIds);
      setSubtasks(updatedTask.subtasks);
    },
    [onReorderSubtasks, task],
  );

  const handleAddSubtaskRequest = useCallback(() => {
    setSubtaskAddKey((current) => current + 1);
    if (editorSubtasksCollapsed) {
      setEditorSubtasksCollapsed(false);
    }
  }, [editorSubtasksCollapsed]);

  const handleToggleSubtasks = useCallback(() => {
    setEditorSubtasksCollapsed((current) => !current);
  }, []);

  const descriptionPreviewText = descriptionBlocksToText(draft.description_blocks);

  return (
    <>
      <Dialog
        open={open}
        title={task ? "Edit task" : "Create task"}
        onOpenChange={onOpenChange}
        contentClassName="max-h-[min(92dvh,46rem)] w-full max-w-[42rem] rounded-[1.8rem] p-0"
        headerClassName="border-b border-border/70 px-5 py-3.5"
        titleClassName="text-[1.2rem]"
      >
        <form className="flex min-h-0 flex-1 flex-col space-y-0 overflow-hidden" onSubmit={handleSubmit}>
          <div className="hover-scrollbar min-h-0 flex-1 overflow-y-auto overscroll-y-contain px-5 py-4">
            <div className="space-y-5">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex flex-wrap items-center gap-3">
                <PriorityCheckbox
                  checked={draft.is_done}
                  priority={draft.priority}
                  className="h-7 w-7"
                  title="Toggle task status"
                  onChange={handleDoneChange}
                />
                <TaskDateRepeatControl
                  value={draft.due_date}
                  startTime={draft.start_time}
                  endTime={draft.end_time}
                  reminderTime={draft.reminder_time}
                  repeat={draft.repeat}
                  repeatConfig={draft.repeat_config}
                  repeatUntil={draft.repeat_until}
                  onDateChange={handleDateChange}
                  onStartTimeChange={handleStartTimeChange}
                  onEndTimeChange={handleEndTimeChange}
                  onReminderTimeChange={handleReminderTimeChange}
                  onRepeatChange={handleRepeatChange}
                  onRepeatConfigChange={handleRepeatConfigChange}
                  onRepeatUntilChange={handleRepeatUntilChange}
                />
              </div>

              <TaskPrioritySelector value={draft.priority} onChange={handlePriorityChange} />
            </div>

              <div className="space-y-3">
                <Textarea
                  ref={titleRef}
                  value={draft.title}
                  rows={1}
                  maxLength={200}
                  placeholder="Task title"
                  className="min-h-0 resize-none overflow-hidden border-0 bg-transparent px-0 py-0 text-2xl font-semibold leading-tight shadow-none focus:border-transparent focus:ring-0 sm:text-3xl"
                  onChange={(event) => handleTitleChange(event.target.value, event.currentTarget)}
                />

                <div className="space-y-3 rounded-[1.35rem] border border-border/80 bg-muted/15 p-4">
                  <div>
                    <h3 className="text-base font-semibold text-foreground">Description</h3>
                  </div>

                  <button
                    type="button"
                    className="w-full rounded-[1.1rem] border border-border/70 bg-card/75 px-4 py-3 text-left transition-colors hover:bg-card"
                    aria-label="Open description editor"
                    onClick={() => setDescriptionDialogOpen(true)}
                  >
                    {descriptionPreviewText ? (
                      <p className="max-h-24 overflow-hidden whitespace-pre-wrap text-sm leading-6 text-foreground break-words [overflow-wrap:anywhere]">
                        {descriptionPreviewText}
                      </p>
                    ) : (
                      <p className="text-sm leading-6 text-muted-foreground">Add description</p>
                    )}
                  </button>
                </div>
              </div>

            <div className="space-y-3 rounded-[1.35rem] border border-border/80 bg-muted/15 p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-base font-semibold text-foreground">Subtasks</h3>
                  <p className="text-sm text-muted-foreground">
                    {subtaskProgress.total > 0 ? `${subtaskProgress.percent}% complete. ` : null}
                    {task
                      ? "Active subtasks can be reordered. Updates save immediately."
                      : "Add subtasks now and they will be created together with the task."}
                  </p>
                </div>
              </div>

              {!task || !editorSubtasksCollapsed ? (
                <SubtaskList
                  ref={subtaskListRef}
                  subtasks={subtasks}
                  addRequestKey={subtaskAddKey}
                  onCreate={handleSubtaskCreate}
                  onUpdate={handleSubtaskUpdate}
                  onToggle={handleSubtaskToggle}
                  onDelete={handleSubtaskDelete}
                  onReorder={handleSubtaskReorder}
                />
              ) : (
                <p className="text-sm text-muted-foreground">Subtasks are hidden for this task.</p>
              )}
            </div>

              {error ? <p className="text-sm text-rose-600 dark:text-rose-200">{error}</p> : null}
            </div>
          </div>
          <div className="sticky bottom-0 z-10 shrink-0 border-t border-border/70 bg-card/95 px-5 py-4 backdrop-blur-xl">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <TaskListMoveMenu value={draft.list_id ? Number(draft.list_id) : null} lists={lists} onChange={handleListChange} />

              <div className="flex flex-wrap items-center justify-end gap-2">
                <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                  Cancel
                </Button>
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? "Saving..." : task ? "Save changes" : "Create task"}
                </Button>
                <TaskActionsMenu
                  isPinned={draft.is_pinned}
                  hasSubtasks={task ? subtasks.length > 0 : false}
                  subtasksCollapsed={editorSubtasksCollapsed}
                  isDisabled={isSubmitting}
                  canDelete={Boolean(task && onDelete)}
                  onPinToggle={handlePinToggle}
                  onAddSubtask={handleAddSubtaskRequest}
                  onToggleSubtasks={handleToggleSubtasks}
                  onDelete={() => void handleDelete()}
                />
              </div>
            </div>
          </div>
        </form>
      </Dialog>

      <TaskDescriptionDialog
        open={descriptionDialogOpen}
        blocks={draft.description_blocks}
        onChange={handleDescriptionTextChange}
        onOpenChange={setDescriptionDialogOpen}
      />
    </>
  );
}
