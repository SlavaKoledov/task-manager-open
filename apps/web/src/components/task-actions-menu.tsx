import { useRef, useState } from "react";
import { Eye, EyeOff, Pin, PinOff, Plus, Trash2 } from "lucide-react";

import { PopoverPanel } from "@/components/popover-panel";
import { useClickOutside } from "@/lib/use-click-outside";

type TaskActionsMenuProps = {
  isPinned: boolean;
  hasSubtasks: boolean;
  subtasksCollapsed: boolean;
  isDisabled?: boolean;
  canDelete?: boolean;
  onPinToggle: () => void;
  onAddSubtask: () => void;
  onToggleSubtasks: () => void;
  onDelete: () => void;
};

export function TaskActionsMenu({
  isPinned,
  hasSubtasks,
  subtasksCollapsed,
  isDisabled = false,
  canDelete = true,
  onPinToggle,
  onAddSubtask,
  onToggleSubtasks,
  onDelete,
}: TaskActionsMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);

  useClickOutside([rootRef, panelRef], open, () => setOpen(false));

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        aria-label="Task actions"
        className="inline-flex h-10 w-10 items-center justify-center rounded-full border border-border/80 bg-card/80 text-muted-foreground shadow-sm transition-colors duration-100 hover:bg-muted/60 hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50"
        disabled={isDisabled}
        onClick={() => setOpen((current) => !current)}
      >
        <span className="text-lg leading-none">...</span>
      </button>

      {open ? (
        <PopoverPanel anchorRef={triggerRef} panelRef={panelRef} align="end" className="w-[14rem]">
          <div className="space-y-1">
            <button
              type="button"
              className="flex w-full items-center gap-3 rounded-[1rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
              onClick={() => {
                onPinToggle();
                setOpen(false);
              }}
            >
              {isPinned ? <PinOff className="h-4 w-4" /> : <Pin className="h-4 w-4" />}
              <span>{isPinned ? "Unpin task" : "Pin task"}</span>
            </button>

            <button
              type="button"
              className="flex w-full items-center gap-3 rounded-[1rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
              onClick={() => {
                onAddSubtask();
                setOpen(false);
              }}
            >
              <Plus className="h-4 w-4" />
              <span>Add subtask</span>
            </button>

            {hasSubtasks ? (
              <button
                type="button"
                className="flex w-full items-center gap-3 rounded-[1rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
                onClick={() => {
                  onToggleSubtasks();
                  setOpen(false);
                }}
              >
                {subtasksCollapsed ? <Eye className="h-4 w-4" /> : <EyeOff className="h-4 w-4" />}
                <span>{subtasksCollapsed ? "Show subtasks" : "Hide subtasks"}</span>
              </button>
            ) : null}

            {canDelete ? (
              <button
                type="button"
                className="flex w-full items-center gap-3 rounded-[1rem] px-3 py-2 text-left text-sm text-rose-600 transition-colors hover:bg-rose-500/10 dark:text-rose-200"
                onClick={() => {
                  onDelete();
                  setOpen(false);
                }}
              >
                <Trash2 className="h-4 w-4" />
                <span>Delete task</span>
              </button>
            ) : null}
          </div>
        </PopoverPanel>
      ) : null}
    </div>
  );
}
