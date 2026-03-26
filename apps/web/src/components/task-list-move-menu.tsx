import { useRef, useState } from "react";
import { Check, FolderInput } from "lucide-react";

import { PopoverPanel } from "@/components/popover-panel";
import type { ListItem } from "@/lib/types";
import { useClickOutside } from "@/lib/use-click-outside";
import { cn } from "@/lib/utils";

type TaskListMoveMenuProps = {
  value: number | null;
  lists: ListItem[];
  onChange: (listId: number | null) => void;
};

export function TaskListMoveMenu({ value, lists, onChange }: TaskListMoveMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const currentLabel = value ? lists.find((list) => list.id === value)?.name ?? "List" : "Inbox";

  useClickOutside([rootRef, panelRef], open, () => setOpen(false));

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={triggerRef}
        type="button"
        className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card/80 px-3 py-2 text-sm text-foreground shadow-sm transition-colors duration-100 hover:bg-muted/60"
        onClick={() => setOpen((current) => !current)}
      >
        <FolderInput className="h-4 w-4 text-primary" />
        <span>{currentLabel}</span>
      </button>

      {open ? (
        <PopoverPanel anchorRef={triggerRef} panelRef={panelRef} className="min-w-[11rem]">
          <div className="space-y-1">
            <button
              type="button"
              className={cn(
                "flex w-full items-center justify-between rounded-[1rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70",
                value === null && "bg-muted/60",
              )}
              onClick={() => {
                onChange(null);
                setOpen(false);
              }}
            >
              <span>Inbox</span>
              {value === null ? <Check className="h-4 w-4 text-primary" /> : null}
            </button>

            {lists.map((list) => (
              <button
                key={list.id}
                type="button"
                className={cn(
                  "flex w-full items-center justify-between rounded-[1rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70",
                  value === list.id && "bg-muted/60",
                )}
                onClick={() => {
                  onChange(list.id);
                  setOpen(false);
                }}
              >
                <span>{list.name}</span>
                {value === list.id ? <Check className="h-4 w-4 text-primary" /> : null}
              </button>
            ))}
          </div>
        </PopoverPanel>
      ) : null}
    </div>
  );
}
