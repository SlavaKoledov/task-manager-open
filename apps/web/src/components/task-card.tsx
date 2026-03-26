import { memo, type DragEvent, type KeyboardEvent, type MouseEvent, useMemo } from "react";
import { AlignLeft, CalendarDays, CheckCircle2, ChevronDown, ChevronRight, GripVertical, Pin, Repeat2 } from "lucide-react";

import { PriorityCheckbox } from "@/components/priority-checkbox";
import { Badge } from "@/components/ui/badge";
import { formatDueDateLabel } from "@/lib/date";
import { hasMeaningfulDescription } from "@/lib/task-description";
import { getTaskRepeatOption } from "@/lib/task-options";
import { getSubtaskProgressSummary } from "@/lib/task-progress";
import type { ReorderInsertDirection, TaskDropDirection } from "@/lib/task-reorder";
import { isTaskOverdue } from "@/lib/task-groups";
import type { ListItem, TaskItem, TaskSubtask } from "@/lib/types";
import { cn } from "@/lib/utils";

type TaskCardProps = {
  task: TaskItem;
  list?: ListItem;
  todayString: string;
  tomorrowString: string;
  subtasksCollapsed: boolean;
  onToggle: (task: TaskItem) => Promise<unknown>;
  onToggleSubtask: (subtask: TaskSubtask) => Promise<unknown>;
  onEdit: (task: TaskItem) => void;
  onToggleSubtasks: (taskId: number) => void;
  isDragging?: boolean;
  dropIndicator?: TaskDropDirection | null;
  dragHandleProps?: {
    label: string;
    onDragStart: (event: DragEvent<HTMLButtonElement>) => void;
    onDragEnd: () => void;
  };
  subtaskDragHandleProps?: {
    getLabel: (subtask: TaskSubtask) => string;
    onDragStart: (subtask: TaskSubtask, event: DragEvent<HTMLButtonElement>) => void;
    onDragEnd: () => void;
  };
  subtaskDropIndicator?: {
    targetId: number;
    direction: ReorderInsertDirection;
  } | null;
};

function TaskCardInner({
  task,
  list,
  todayString,
  tomorrowString,
  subtasksCollapsed,
  onToggle,
  onToggleSubtask,
  onEdit,
  onToggleSubtasks,
  isDragging = false,
  dropIndicator = null,
  dragHandleProps,
  subtaskDragHandleProps,
  subtaskDropIndicator = null,
}: TaskCardProps) {
  const dueDateLabel = formatDueDateLabel(task.due_date, todayString, tomorrowString);
  const activeSubtasks = useMemo(() => task.subtasks.filter((subtask) => !subtask.is_done), [task.subtasks]);
  const doneSubtasks = useMemo(() => task.subtasks.filter((subtask) => subtask.is_done), [task.subtasks]);
  const subtaskProgress = useMemo(() => getSubtaskProgressSummary(task.subtasks), [task.subtasks]);
  const isOverdue = useMemo(() => isTaskOverdue(task, todayString), [task, todayString]);

  function stopPropagation(event: MouseEvent<HTMLElement>) {
    event.stopPropagation();
  }

  function handleCardKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.currentTarget !== event.target) {
      return;
    }

    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      onEdit(task);
    }
  }

  return (
    <article
      role="button"
      tabIndex={0}
      className={cn(
        "relative rounded-[1.3rem] border border-border/70 bg-card/90 p-4 text-left shadow-panel transition-opacity focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        isDragging && "opacity-50",
      )}
      onClick={() => onEdit(task)}
      onKeyDown={handleCardKeyDown}
    >
      {dropIndicator === "inside" ? (
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-2 z-10 rounded-[1rem] border-2 border-sky-500/90 bg-sky-500/10 shadow-[0_0_0_1px_rgba(14,165,233,0.4),0_0_18px_rgba(14,165,233,0.28)]"
        />
      ) : null}
      {dropIndicator === "before" || dropIndicator === "after" ? (
        <div
          aria-hidden="true"
          className={cn(
            "pointer-events-none absolute left-4 right-4 z-10 h-1 rounded-full bg-sky-500 shadow-[0_0_0_1px_rgba(14,165,233,0.4),0_0_16px_rgba(14,165,233,0.35)]",
            dropIndicator === "before" ? "top-1.5" : "bottom-1.5",
          )}
        />
      ) : null}

      <div className="flex items-start gap-3">
        <PriorityCheckbox checked={task.is_done} priority={task.priority} onChange={() => void onToggle(task)} />

        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-3">
            <div className="min-w-0">
              <div className="flex items-start gap-2">
                <h3 className={cn("text-base font-semibold text-foreground", task.is_done && "text-muted-foreground line-through")}>
                  {task.title}
                </h3>
                {hasMeaningfulDescription(task.description_blocks, task.description) ? (
                  <AlignLeft className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-label="Has description" />
                ) : null}
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-1">
              {dragHandleProps ? (
                <button
                  type="button"
                  draggable
                  className="inline-flex h-8 w-8 cursor-grab items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground active:cursor-grabbing"
                  aria-label={dragHandleProps.label}
                  onClick={stopPropagation}
                  onPointerDown={(event) => event.stopPropagation()}
                  onDragStart={(event) => {
                    event.stopPropagation();
                    dragHandleProps.onDragStart(event);
                  }}
                  onDragEnd={dragHandleProps.onDragEnd}
                >
                  <GripVertical className="h-4 w-4" />
                </button>
              ) : null}

              {task.is_pinned ? (
                <span className="inline-flex h-8 w-8 items-center justify-center rounded-full bg-amber-500/10 text-amber-500">
                  <Pin className="h-4 w-4" />
                </span>
              ) : null}
            </div>
          </div>

          <div className="mt-3 flex flex-wrap gap-2">
            {dueDateLabel ? (
              <Badge
                className={cn(
                  isOverdue && "border-rose-300/80 bg-rose-500/10 text-rose-700 dark:border-rose-400/40 dark:text-rose-200",
                )}
              >
                <CalendarDays className="mr-1 h-3.5 w-3.5" />
                {dueDateLabel}
              </Badge>
            ) : (
              <Badge>No date</Badge>
            )}
            {list ? (
              <Badge style={{ borderColor: `${list.color}55`, backgroundColor: `${list.color}1a`, color: list.color }}>
                {list.name}
              </Badge>
            ) : (
              <Badge>Inbox</Badge>
            )}
            {task.repeat !== "none" ? (
              <Badge>
                <Repeat2 className="mr-1 h-3.5 w-3.5" />
                {getTaskRepeatOption(task.repeat).label}
              </Badge>
            ) : null}
          </div>

          {task.subtasks.length > 0 ? (
            <div className="mt-4 rounded-[1.1rem] border border-border/70 bg-muted/20 p-2.5">
              <button
                type="button"
                className="flex w-full items-center gap-2 rounded-full px-2 py-1 text-left text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground"
                onClick={(event) => {
                  stopPropagation(event);
                  onToggleSubtasks(task.id);
                }}
              >
                {subtasksCollapsed ? <ChevronRight className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
                <span>{task.subtasks.length} subtasks</span>
                {subtaskProgress.percent !== null ? <span>{subtaskProgress.percent}%</span> : null}
              </button>

              {!subtasksCollapsed ? (
                <div className="mt-2 space-y-2">
                  {activeSubtasks.map((subtask) => (
                    <div
                      key={subtask.id}
                      data-subtask-drop-target-id={subtask.id}
                      className="relative flex items-center gap-3 rounded-[1rem] border border-border/70 bg-card/70 px-3 py-2"
                      onClick={stopPropagation}
                    >
                      {subtaskDropIndicator?.targetId === subtask.id ? (
                        <div
                          aria-hidden="true"
                          className={cn(
                            "pointer-events-none absolute left-3 right-3 z-10 h-1 rounded-full bg-sky-500 shadow-[0_0_0_1px_rgba(14,165,233,0.4),0_0_12px_rgba(14,165,233,0.3)]",
                            subtaskDropIndicator.direction === "before" ? "top-1" : "bottom-1",
                          )}
                        />
                      ) : null}
                      {subtaskDragHandleProps ? (
                        <button
                          type="button"
                          draggable
                          className="inline-flex h-7 w-7 cursor-grab items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground active:cursor-grabbing"
                          aria-label={subtaskDragHandleProps.getLabel(subtask)}
                          onClick={stopPropagation}
                          onPointerDown={(event) => event.stopPropagation()}
                          onDragStart={(event) => {
                            event.stopPropagation();
                            subtaskDragHandleProps.onDragStart(subtask, event);
                          }}
                          onDragEnd={subtaskDragHandleProps.onDragEnd}
                        >
                          <GripVertical className="h-4 w-4" />
                        </button>
                      ) : null}
                      <PriorityCheckbox
                        checked={subtask.is_done}
                        priority={subtask.priority}
                        className="h-5 w-5"
                        onChange={() => void onToggleSubtask(subtask)}
                        title={`Toggle ${subtask.title}`}
                      />
                            <div className="min-w-0 flex-1">
                        <div className="flex items-start gap-2">
                          <p className="truncate text-sm text-foreground">{subtask.title}</p>
                          {hasMeaningfulDescription(subtask.description_blocks, subtask.description) ? (
                            <AlignLeft className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-label="Has description" />
                          ) : null}
                        </div>
                      </div>
                    </div>
                  ))}

                  {doneSubtasks.length > 0 ? (
                    <details className="group rounded-[1rem] border border-border/60 bg-background/40" onClick={stopPropagation}>
                      <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3 py-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                        <span className="flex items-center gap-2">
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          <span>Done</span>
                          <span className="rounded-full bg-muted/70 px-2 py-0.5 text-[11px] font-medium normal-case tracking-normal">
                            {doneSubtasks.length}
                          </span>
                        </span>
                        <ChevronDown className="h-3.5 w-3.5 transition-transform duration-150 group-open:rotate-180" />
                      </summary>

                      <div className="space-y-2 border-t border-border/60 px-2.5 pb-2.5 pt-2">
                        {doneSubtasks.map((subtask) => (
                          <div
                            key={subtask.id}
                            className="flex items-center gap-3 rounded-[1rem] border border-border/70 bg-card/55 px-3 py-2"
                            onClick={stopPropagation}
                          >
                            <PriorityCheckbox
                              checked={subtask.is_done}
                              priority={subtask.priority}
                              className="h-5 w-5"
                              onChange={() => void onToggleSubtask(subtask)}
                              title={`Toggle ${subtask.title}`}
                            />
                            <div className="min-w-0 flex-1">
                              <div className="flex items-start gap-2">
                                <p className="truncate text-sm text-muted-foreground line-through">{subtask.title}</p>
                                {hasMeaningfulDescription(subtask.description_blocks, subtask.description) ? (
                                  <AlignLeft className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-label="Has description" />
                                ) : null}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </details>
                  ) : null}
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      </div>
    </article>
  );
}

export const TaskCard = memo(TaskCardInner);
