import { memo } from "react";
import { Plus } from "lucide-react";

import { TaskDoneSection } from "@/components/task-done-section";
import { TaskGroupSection } from "@/components/task-group-section";
import { Button } from "@/components/ui/button";
import { buildTaskSectionCollapseKey, buildTopLevelTaskDropScope, type AllTaskGroup } from "@/lib/task-groups";
import type { DraggedTaskItem, ListItem, TaskItem, TaskSubtask, TaskTopLevelReorderScope } from "@/lib/types";

export const ALL_TASK_GROUP_COLUMN_CLASSNAME =
  "hover-scrollbar flex h-[min(72vh,42rem)] min-h-0 w-[20rem] shrink-0 flex-col overflow-x-hidden overflow-y-auto overscroll-y-contain rounded-[1.8rem] border border-border/70 bg-card/70 shadow-panel backdrop-blur-xl lg:h-full";

export const ALL_TASK_GROUP_HEADER_CLASSNAME =
  "sticky top-0 z-10 border-b border-border/60 bg-card/95 px-4 pb-4 pt-4 backdrop-blur-xl";

type AllTaskGroupColumnProps = {
  group: AllTaskGroup;
  listById: Map<number, ListItem>;
  todayString: string;
  tomorrowString: string;
  collapsedTaskSections: Record<string, boolean>;
  collapsedSubtasks: Record<number, boolean>;
  onOpenCreateTaskDialog: (groupId: AllTaskGroup["id"]) => void;
  onTaskToggle: (task: TaskItem) => Promise<unknown>;
  onSubtaskToggle: (subtask: TaskSubtask) => Promise<unknown>;
  onTaskEdit: (task: TaskItem) => void;
  onToggleSubtasks: (taskId: number) => void;
  onToggleCollapsed: (groupId: AllTaskGroup["id"], sectionId: AllTaskGroup["sections"][number]["id"]) => void;
  onCreateTaskInSection: (groupId: AllTaskGroup["id"], sectionId: AllTaskGroup["sections"][number]["id"]) => void;
  onReorderTasks: (scope: TaskTopLevelReorderScope, taskIds: number[]) => Promise<unknown>;
  draggedTask: DraggedTaskItem | null;
  onTaskDragStart: (task: DraggedTaskItem) => void;
  onTaskDragEnd: () => void;
  onMoveTaskToParent: (taskId: number, parentTaskId: number, orderedIds: number[]) => Promise<unknown>;
  onMoveTaskToTopLevel: (taskId: number, scope: TaskTopLevelReorderScope, orderedIds: number[]) => Promise<unknown>;
};

function AllTaskGroupColumnInner({
  group,
  listById,
  todayString,
  tomorrowString,
  collapsedTaskSections,
  collapsedSubtasks,
  onOpenCreateTaskDialog,
  onTaskToggle,
  onSubtaskToggle,
  onTaskEdit,
  onToggleSubtasks,
  onToggleCollapsed,
  onCreateTaskInSection,
  onReorderTasks,
  draggedTask,
  onTaskDragStart,
  onTaskDragEnd,
  onMoveTaskToParent,
  onMoveTaskToTopLevel,
}: AllTaskGroupColumnProps) {
  return (
    <section className={ALL_TASK_GROUP_COLUMN_CLASSNAME}>
      <div className={ALL_TASK_GROUP_HEADER_CLASSNAME}>
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <h3 className="text-lg font-semibold text-foreground">{group.title}</h3>
              <span className="rounded-full bg-muted/60 px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                {group.totalCount}
              </span>
            </div>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              {group.totalCount === 0
                ? group.emptyDescription
                : group.doneCount === 0
                  ? `${group.activeCount} active`
                  : `${group.activeCount} active, ${group.doneCount} done`}
            </p>
          </div>

          <Button size="icon" aria-label={`Create task in ${group.title}`} onClick={() => onOpenCreateTaskDialog(group.id)}>
            <Plus className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="space-y-5 px-4 py-4">
        {group.totalCount > 0 ? (
          <>
            {group.sections.map((section) => (
              <TaskGroupSection
                key={`${group.id}-${section.id}`}
                title={section.title}
                sectionId={section.id}
                tasks={section.tasks}
                collapsed={collapsedTaskSections[buildTaskSectionCollapseKey(group.id, section.id)] ?? false}
                listById={listById}
                todayString={todayString}
                tomorrowString={tomorrowString}
                collapsedSubtasks={collapsedSubtasks}
                onTaskToggle={onTaskToggle}
                onSubtaskToggle={onSubtaskToggle}
                onTaskEdit={onTaskEdit}
                onToggleSubtasks={onToggleSubtasks}
                onToggleCollapsed={() => onToggleCollapsed(group.id, section.id)}
                onCreateTaskInSection={(sectionId) => onCreateTaskInSection(group.id, sectionId)}
                taskReorderScope={buildTopLevelTaskDropScope({
                  mode: "all",
                  groupId: group.id,
                  sectionId: section.id,
                  todayString,
                  tomorrowString,
                }) ?? undefined}
                onReorderTasks={onReorderTasks}
                draggedTask={draggedTask}
                onTaskDragStart={onTaskDragStart}
                onTaskDragEnd={onTaskDragEnd}
                onMoveTaskToParent={onMoveTaskToParent}
                onMoveTaskToTopLevel={onMoveTaskToTopLevel}
              />
            ))}

            <TaskDoneSection
              tasks={group.doneTasks}
              listById={listById}
              todayString={todayString}
              tomorrowString={tomorrowString}
              collapsedSubtasks={collapsedSubtasks}
              onTaskToggle={onTaskToggle}
              onSubtaskToggle={onSubtaskToggle}
              onTaskEdit={onTaskEdit}
              onToggleSubtasks={onToggleSubtasks}
            />
          </>
        ) : (
          <p className="rounded-[1.2rem] border border-dashed border-border/70 bg-muted/20 px-4 py-5 text-sm text-muted-foreground">
            {group.emptyDescription}
          </p>
        )}
      </div>
    </section>
  );
}

export const AllTaskGroupColumn = memo(AllTaskGroupColumnInner);
