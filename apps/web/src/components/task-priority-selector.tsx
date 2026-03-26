import { useRef, useState } from "react";
import { Check, Flag } from "lucide-react";

import { PopoverPanel } from "@/components/popover-panel";
import { getTaskPriorityOption, TASK_PRIORITY_OPTIONS } from "@/lib/task-options";
import type { TaskPriority } from "@/lib/types";
import { useClickOutside } from "@/lib/use-click-outside";
import { cn } from "@/lib/utils";

type TaskPrioritySelectorProps = {
  value: TaskPriority;
  onChange: (nextPriority: TaskPriority) => void;
};

export function TaskPrioritySelector({ value, onChange }: TaskPrioritySelectorProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const selectedOption = getTaskPriorityOption(value);

  useClickOutside([rootRef, panelRef], open, () => setOpen(false));

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card/80 px-3 py-2 text-sm text-foreground shadow-sm transition-colors duration-100 hover:bg-muted/60"
        onClick={() => setOpen((current) => !current)}
      >
        <Flag className="h-4 w-4" style={{ color: selectedOption.accentColor }} />
        <span>{selectedOption.shortLabel}</span>
      </button>

      {open ? (
        <PopoverPanel anchorRef={triggerRef} panelRef={panelRef} align="end" className="w-[16rem]">
          <div className="mb-2 px-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
            Priority
          </div>
          <div className="space-y-1">
            {TASK_PRIORITY_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                className={cn(
                  "flex w-full items-center justify-between rounded-[1rem] border px-3 py-2 text-left text-sm font-medium transition-colors",
                  option.buttonClassName,
                )}
                onClick={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
              >
                <span className="flex items-center gap-2">
                  <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${option.accentClassName}`} />
                  {option.label}
                </span>
                {option.value === value ? <Check className="h-4 w-4" /> : null}
              </button>
            ))}
          </div>
        </PopoverPanel>
      ) : null}
    </div>
  );
}
