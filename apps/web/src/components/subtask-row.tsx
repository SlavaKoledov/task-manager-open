import { memo, useEffect, useState } from "react";
import { GripVertical, Trash2 } from "lucide-react";

import { Checkbox } from "@/components/ui/checkbox";
import type { ReorderInsertDirection } from "@/lib/task-reorder";
import type { TaskSubtask } from "@/lib/types";
import { cn } from "@/lib/utils";

type SubtaskRowProps = {
  subtask: TaskSubtask;
  isDragging: boolean;
  dragEnabled?: boolean;
  onToggle: (subtask: TaskSubtask) => void;
  onUpdateTitle: (subtask: TaskSubtask, title: string) => void;
  onDelete: (subtask: TaskSubtask) => void;
  onDragStart: (subtaskId: number) => void;
  onDragEnd: () => void;
  dropIndicator?: ReorderInsertDirection | null;
};

function SubtaskRowInner({
  subtask,
  isDragging,
  dragEnabled = true,
  onToggle,
  onUpdateTitle,
  onDelete,
  onDragStart,
  onDragEnd,
  dropIndicator = null,
}: SubtaskRowProps) {
  const [title, setTitle] = useState(subtask.title);

  useEffect(() => {
    setTitle(subtask.title);
  }, [subtask.title]);

  return (
    <div
      draggable={dragEnabled}
      className={cn(
        "relative flex items-center gap-3 rounded-[1rem] border border-border/70 bg-card/60 px-3 py-2.5 shadow-sm",
        isDragging && "opacity-50",
        !dragEnabled && "bg-card/45",
      )}
      onDragStart={(event) => {
        if (!dragEnabled) {
          return;
        }

        event.dataTransfer.effectAllowed = "move";
        onDragStart(subtask.id);
      }}
      onDragEnd={onDragEnd}
    >
      {dropIndicator ? (
        <div
          aria-hidden="true"
          className={cn(
            "pointer-events-none absolute left-3 right-3 z-10 h-1 rounded-full bg-sky-500 shadow-[0_0_0_1px_rgba(14,165,233,0.4),0_0_16px_rgba(14,165,233,0.35)]",
            dropIndicator === "before" ? "top-1" : "bottom-1",
          )}
        />
      ) : null}

      <GripVertical className={cn("h-4 w-4 shrink-0 text-muted-foreground", !dragEnabled && "opacity-35")} />
      <Checkbox checked={subtask.is_done} className="h-4 w-4 shrink-0" onChange={() => onToggle(subtask)} />
      <input
        value={title}
        className={cn(
          "min-w-0 flex-1 border-0 bg-transparent p-0 text-sm text-foreground outline-none placeholder:text-muted-foreground",
          subtask.is_done && "text-muted-foreground line-through",
        )}
        placeholder="Subtask title"
        onChange={(event) => setTitle(event.target.value)}
        onBlur={() => {
          if (title.trim() && title.trim() !== subtask.title) {
            onUpdateTitle(subtask, title.trim());
          } else {
            setTitle(subtask.title);
          }
        }}
        onKeyDown={(event) => {
          if (event.key !== "Enter") {
            return;
          }

          event.preventDefault();
          if (!title.trim()) {
            setTitle(subtask.title);
            return;
          }

          onUpdateTitle(subtask, title.trim());
          event.currentTarget.blur();
        }}
      />
      <button
        type="button"
        className="inline-flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted/70 hover:text-rose-600"
        aria-label={`Delete ${subtask.title}`}
        onClick={() => onDelete(subtask)}
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </div>
  );
}

export const SubtaskRow = memo(SubtaskRowInner);
