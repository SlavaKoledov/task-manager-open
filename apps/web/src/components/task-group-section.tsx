import { memo, type DragEvent as ReactDragEvent, useCallback, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Pin, Plus } from "lucide-react";

import { TaskCard } from "@/components/task-card";
import { Button } from "@/components/ui/button";
import { getTaskPriorityOption } from "@/lib/task-options";
import {
  areTaskDropIndicatorsEqual,
  insertOrderedId,
  insertOrderedIdRelative,
  moveOrderedIds,
  resolveTaskDropDirection,
  type ReorderDropIndicator,
  type TaskDropIndicator,
} from "@/lib/task-reorder";
import type { DraggedTaskItem, ListItem, TaskItem, TaskSectionId, TaskSubtask, TaskTopLevelReorderScope } from "@/lib/types";

type TaskGroupSectionProps = {
  title: string;
  sectionId: TaskSectionId;
  tasks: TaskItem[];
  collapsed: boolean;
  listById: Map<number, ListItem>;
  todayString: string;
  tomorrowString: string;
  collapsedSubtasks: Record<number, boolean>;
  onTaskToggle: (task: TaskItem) => Promise<unknown>;
  onSubtaskToggle: (subtask: TaskSubtask) => Promise<unknown>;
  onTaskEdit: (task: TaskItem) => void;
  onToggleSubtasks: (taskId: number) => void;
  onToggleCollapsed: () => void;
  onCreateTaskInSection?: (sectionId: TaskSectionId) => void;
  taskReorderScope?: TaskTopLevelReorderScope;
  onReorderTasks?: (scope: TaskTopLevelReorderScope, taskIds: number[]) => Promise<unknown>;
  draggedTask: DraggedTaskItem | null;
  onTaskDragStart: (task: DraggedTaskItem) => void;
  onTaskDragEnd: () => void;
  onMoveTaskToParent: (taskId: number, parentTaskId: number, orderedIds: number[]) => Promise<unknown>;
  onMoveTaskToTopLevel: (taskId: number, scope: TaskTopLevelReorderScope, orderedIds: number[]) => Promise<unknown>;
};

function TaskGroupSectionInner({
  title,
  sectionId,
  tasks,
  collapsed,
  listById,
  todayString,
  tomorrowString,
  collapsedSubtasks,
  onTaskToggle,
  onSubtaskToggle,
  onTaskEdit,
  onToggleSubtasks,
  onToggleCollapsed,
  onCreateTaskInSection,
  taskReorderScope,
  onReorderTasks,
  draggedTask,
  onTaskDragStart,
  onTaskDragEnd,
  onMoveTaskToParent,
  onMoveTaskToTopLevel,
}: TaskGroupSectionProps) {
  const [draggingTaskId, setDraggingTaskId] = useState<number | null>(null);
  const [dropIndicator, setDropIndicator] = useState<TaskDropIndicator | null>(null);
  const [subtaskDropIndicator, setSubtaskDropIndicator] = useState<ReorderDropIndicator | null>(null);
  const [sectionDropActive, setSectionDropActive] = useState(false);

  const priorityOption = sectionId === "pinned" ? null : getTaskPriorityOption(sectionId);
  const canCreateTaskInSection = sectionId !== "pinned" && onCreateTaskInSection !== undefined;
  const taskIds = useMemo(() => tasks.map((task) => task.id), [tasks]);
  const taskById = useMemo(() => new Map(tasks.map((task) => [task.id, task])), [tasks]);
  const parentTaskBySubtaskId = useMemo(() => {
    const parentLookup = new Map<number, TaskItem>();

    for (const task of tasks) {
      for (const subtask of task.subtasks) {
        parentLookup.set(subtask.id, task);
      }
    }

    return parentLookup;
  }, [tasks]);

  const setDropIndicatorIfChanged = useCallback((nextIndicator: TaskDropIndicator | null) => {
    setDropIndicator((currentIndicator) =>
      areTaskDropIndicatorsEqual(currentIndicator, nextIndicator) ? currentIndicator : nextIndicator,
    );
  }, []);

  const clearDragState = useCallback(() => {
    setDraggingTaskId(null);
    setSectionDropActive(false);
    setSubtaskDropIndicator(null);
    setDropIndicatorIfChanged(null);
    onTaskDragEnd();
  }, [onTaskDragEnd, setDropIndicatorIfChanged]);

  const handleTaskListDragOver = useCallback(
    (event: ReactDragEvent<HTMLDivElement>) => {
      if (draggedTask === null) {
        return;
      }

      const subtaskTargetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-subtask-drop-target-id]");
      if (subtaskTargetElement && event.currentTarget.contains(subtaskTargetElement)) {
        const subtaskId = Number(subtaskTargetElement.dataset.subtaskDropTargetId);
        if (!Number.isFinite(subtaskId) || subtaskId === draggedTask.task_id) {
          setSubtaskDropIndicator(null);
          setDropIndicatorIfChanged(null);
          return;
        }

        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
        setSectionDropActive(false);
        setDropIndicatorIfChanged(null);
        setSubtaskDropIndicator({
          targetId: subtaskId,
          direction: draggedTask.parent_id === null && draggedTask.has_subtasks
            ? "after"
            : event.clientY < subtaskTargetElement.getBoundingClientRect().top + subtaskTargetElement.getBoundingClientRect().height / 2
              ? "before"
              : "after",
        });
        return;
      }

      const targetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-task-drop-target-id]");
      if (!targetElement || !event.currentTarget.contains(targetElement)) {
        if (taskReorderScope) {
          event.preventDefault();
          event.dataTransfer.dropEffect = "move";
          setSectionDropActive(true);
        }
        setSubtaskDropIndicator(null);
        setDropIndicatorIfChanged(null);
        return;
      }

      const targetId = Number(targetElement.dataset.taskDropTargetId);
      if (!Number.isFinite(targetId) || targetId === draggedTask.task_id) {
        setSectionDropActive(false);
        setDropIndicatorIfChanged(null);
        return;
      }

      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      setSectionDropActive(false);
      setSubtaskDropIndicator(null);

      const targetTask = taskById.get(targetId);
      if (!targetTask) {
        setDropIndicatorIfChanged(null);
        return;
      }

      setDropIndicatorIfChanged({
        targetId,
        direction: resolveTaskDropDirection(
          event.clientY,
          targetElement.getBoundingClientRect(),
          !draggedTask.has_subtasks,
        ),
      });
    },
    [draggedTask, setDropIndicatorIfChanged, taskById, taskReorderScope],
  );

  const handleTaskListDrop = useCallback(
    (event: ReactDragEvent<HTMLDivElement>) => {
      if (draggedTask === null) {
        return;
      }

      const subtaskTargetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-subtask-drop-target-id]");
      if (subtaskTargetElement && event.currentTarget.contains(subtaskTargetElement)) {
        const targetSubtaskId = Number(subtaskTargetElement.dataset.subtaskDropTargetId);
        const parentTask = parentTaskBySubtaskId.get(targetSubtaskId);

        clearDragState();

        if (!Number.isFinite(targetSubtaskId) || !parentTask || targetSubtaskId === draggedTask.task_id || draggedTask.has_subtasks) {
          return;
        }

        const destinationIds = parentTask.subtasks.map((subtask) => subtask.id);
        const reorderedIds = destinationIds.includes(draggedTask.task_id)
          ? moveOrderedIds(
              destinationIds,
              draggedTask.task_id,
              targetSubtaskId,
              subtaskDropIndicator?.targetId === targetSubtaskId ? subtaskDropIndicator.direction : "after",
            )
          : insertOrderedIdRelative(
              destinationIds,
              draggedTask.task_id,
              targetSubtaskId,
              subtaskDropIndicator?.targetId === targetSubtaskId ? subtaskDropIndicator.direction : "after",
            );

        void onMoveTaskToParent(draggedTask.task_id, parentTask.id, reorderedIds);
        return;
      }

      const targetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-task-drop-target-id]");
      if (!targetElement || !event.currentTarget.contains(targetElement)) {
        if (!taskReorderScope) {
          clearDragState();
          return;
        }

        event.preventDefault();
        const reorderedTaskIds = insertOrderedId(taskIds.filter((taskId) => taskId !== draggedTask.task_id), draggedTask.task_id, "end");
        clearDragState();

        if (reorderedTaskIds.length === 0 || reorderedTaskIds.every((taskId, index) => taskId === taskIds[index])) {
          return;
        }

        if (draggedTask.parent_id === null && taskIds.includes(draggedTask.task_id) && onReorderTasks) {
          void onReorderTasks(taskReorderScope, reorderedTaskIds);
          return;
        }

        void onMoveTaskToTopLevel(draggedTask.task_id, taskReorderScope, reorderedTaskIds);
        return;
      }

      const targetId = Number(targetElement.dataset.taskDropTargetId);
      if (!Number.isFinite(targetId) || targetId === draggedTask.task_id) {
        clearDragState();
        return;
      }

      event.preventDefault();

      const targetTask = taskById.get(targetId);
      if (!targetTask) {
        clearDragState();
        return;
      }

      const direction =
        dropIndicator?.targetId === targetId
          ? dropIndicator.direction
          : resolveTaskDropDirection(
              event.clientY,
              targetElement.getBoundingClientRect(),
              !draggedTask.has_subtasks,
            );

      clearDragState();

      if (direction === "inside") {
        if (draggedTask.has_subtasks) {
          return;
        }

        const orderedIds = insertOrderedId(targetTask.subtasks.map((subtask) => subtask.id), draggedTask.task_id, "end");
        void onMoveTaskToParent(draggedTask.task_id, targetTask.id, orderedIds);
        return;
      }

      if (!taskReorderScope) {
        return;
      }

      const insertDirection = direction === "before" ? "before" : "after";
      const reorderedTaskIds = taskIds.includes(draggedTask.task_id)
        ? moveOrderedIds(taskIds, draggedTask.task_id, targetId, insertDirection)
        : insertOrderedIdRelative(taskIds, draggedTask.task_id, targetId, insertDirection);

      if (reorderedTaskIds.every((taskId, index) => taskId === taskIds[index])) {
        return;
      }

      if (draggedTask.parent_id === null && taskIds.includes(draggedTask.task_id) && onReorderTasks) {
        void onReorderTasks(taskReorderScope, reorderedTaskIds);
        return;
      }

      void onMoveTaskToTopLevel(draggedTask.task_id, taskReorderScope, reorderedTaskIds);
    },
    [
      clearDragState,
      draggedTask,
      dropIndicator,
      onMoveTaskToParent,
      onMoveTaskToTopLevel,
      onReorderTasks,
      parentTaskBySubtaskId,
      taskById,
      taskIds,
      taskReorderScope,
    ],
  );

  if (tasks.length === 0) {
    return null;
  }

  return (
    <section className="rounded-[1.35rem] border border-border/70 bg-muted/10 p-3">
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0 flex items-center gap-2">
          <div className="shrink-0">
            {sectionId === "pinned" ? (
              <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-amber-500/10 text-amber-500">
                <Pin className="h-3.5 w-3.5" />
              </span>
            ) : (
              <span
                aria-hidden="true"
                className={`h-2.5 w-2.5 rounded-full ${priorityOption?.accentClassName ?? "bg-zinc-400"}`}
              />
            )}
          </div>
          <h4 className="truncate text-base font-semibold text-foreground">{title}</h4>
          <span className="rounded-full bg-background/80 px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
            {tasks.length}
          </span>
        </div>

        <div className="flex items-center gap-1">
          {canCreateTaskInSection ? (
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-zinc-500 hover:bg-muted/70 hover:text-foreground"
              aria-label={`Create ${title} task`}
              onClick={() => onCreateTaskInSection?.(sectionId)}
            >
              <Plus className="h-4 w-4" />
            </Button>
          ) : null}

          <button
            type="button"
            className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground"
            aria-expanded={!collapsed}
            aria-label={`${collapsed ? "Expand" : "Collapse"} ${title} section`}
            onClick={onToggleCollapsed}
          >
            {collapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </button>
        </div>
      </div>

      {!collapsed ? (
        <div
          className="relative mt-3 space-y-3"
          onDragOver={handleTaskListDragOver}
          onDrop={handleTaskListDrop}
          onDragLeave={(event) => {
            if (event.target !== event.currentTarget) {
              return;
            }

            const nextTarget = event.relatedTarget;

            if (nextTarget instanceof Node && event.currentTarget.contains(nextTarget)) {
              return;
            }

            setSectionDropActive(false);
            setSubtaskDropIndicator(null);
            setDropIndicatorIfChanged(null);
          }}
        >
          {sectionDropActive ? (
            <div
              aria-hidden="true"
              className="pointer-events-none absolute inset-x-3 bottom-0 z-10 h-1 rounded-full bg-sky-500 shadow-[0_0_0_1px_rgba(14,165,233,0.4),0_0_16px_rgba(14,165,233,0.35)]"
            />
          ) : null}
          {tasks.map((task) => (
            <div key={task.id} data-task-drop-target-id={task.id}>
              <TaskCard
                task={task}
                list={task.list_id ? listById.get(task.list_id) : undefined}
                todayString={todayString}
                tomorrowString={tomorrowString}
                subtasksCollapsed={collapsedSubtasks[task.id] ?? false}
                onToggle={onTaskToggle}
                onToggleSubtask={onSubtaskToggle}
                onEdit={onTaskEdit}
                onToggleSubtasks={onToggleSubtasks}
                isDragging={draggingTaskId === task.id}
                dropIndicator={dropIndicator?.targetId === task.id ? dropIndicator.direction : null}
                subtaskDropIndicator={subtaskDropIndicator && task.subtasks.some((subtask) => subtask.id === subtaskDropIndicator.targetId) ? subtaskDropIndicator : null}
                dragHandleProps={
                  taskReorderScope
                    ? {
                        label: `Drag ${task.title}`,
                        onDragStart: (event) => {
                          event.dataTransfer.effectAllowed = "move";
                          setDraggingTaskId(task.id);
                          onTaskDragStart({
                            task_id: task.id,
                            parent_id: null,
                            has_subtasks: task.subtasks.length > 0,
                          });
                          setDropIndicatorIfChanged(null);
                          setSectionDropActive(false);
                        },
                        onDragEnd: clearDragState,
                      }
                    : undefined
                }
                subtaskDragHandleProps={{
                  getLabel: (subtask) => `Drag ${subtask.title}`,
                  onDragStart: (subtask, event) => {
                    event.dataTransfer.effectAllowed = "move";
                    setDraggingTaskId(subtask.id);
                    onTaskDragStart({
                      task_id: subtask.id,
                      parent_id: task.id,
                      has_subtasks: false,
                    });
                    setDropIndicatorIfChanged(null);
                    setSubtaskDropIndicator(null);
                    setSectionDropActive(false);
                  },
                  onDragEnd: clearDragState,
                }}
              />
            </div>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export const TaskGroupSection = memo(TaskGroupSectionInner);
