import { memo } from "react";
import { CheckCircle2, ChevronDown } from "lucide-react";

import { TaskCard } from "@/components/task-card";
import type { ListItem, TaskItem, TaskSubtask } from "@/lib/types";
import { cn } from "@/lib/utils";

type TaskDoneSectionProps = {
  className?: string;
  tasks: TaskItem[];
  listById: Map<number, ListItem>;
  todayString: string;
  tomorrowString: string;
  collapsedSubtasks: Record<number, boolean>;
  onTaskToggle: (task: TaskItem) => Promise<unknown>;
  onSubtaskToggle: (subtask: TaskSubtask) => Promise<unknown>;
  onTaskEdit: (task: TaskItem) => void;
  onToggleSubtasks: (taskId: number) => void;
};

function TaskDoneSectionInner({
  className,
  tasks,
  listById,
  todayString,
  tomorrowString,
  collapsedSubtasks,
  onTaskToggle,
  onSubtaskToggle,
  onTaskEdit,
  onToggleSubtasks,
}: TaskDoneSectionProps) {
  if (tasks.length === 0) {
    return null;
  }

  return (
    <details
      className={cn(
        "group rounded-[1.35rem] border border-border/70 bg-muted/15",
        className,
      )}
    >
      <summary className="shrink-0 flex cursor-pointer list-none items-center justify-between gap-3 px-4 py-3 text-sm font-semibold text-muted-foreground">
        <span className="flex items-center gap-2">
          <span className="inline-flex h-7 w-7 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-300">
            <CheckCircle2 className="h-4 w-4" />
          </span>
          <span>Done</span>
          <span className="rounded-full bg-background/70 px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
            {tasks.length}
          </span>
        </span>
        <ChevronDown className="h-4 w-4 transition-transform duration-150 group-open:rotate-180" />
      </summary>

      <div className="border-t border-border/60 px-3 pb-3 pt-3">
        <div className="space-y-3">
          {tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              list={task.list_id ? listById.get(task.list_id) : undefined}
              todayString={todayString}
              tomorrowString={tomorrowString}
              subtasksCollapsed={collapsedSubtasks[task.id] ?? false}
              onToggle={onTaskToggle}
              onToggleSubtask={onSubtaskToggle}
              onEdit={onTaskEdit}
              onToggleSubtasks={onToggleSubtasks}
            />
          ))}
        </div>
      </div>
    </details>
  );
}

export const TaskDoneSection = memo(TaskDoneSectionInner);
