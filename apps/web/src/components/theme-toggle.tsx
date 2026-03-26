import { Check, MoreHorizontal, Moon, Sun } from "lucide-react";
import { useCallback, useRef, useState } from "react";

import { PopoverPanel } from "@/components/popover-panel";
import { Button } from "@/components/ui/button";
import { useClickOutside } from "@/lib/use-click-outside";
import type { NewTaskPlacementPreference, Theme } from "@/lib/types";

type ThemeToggleProps = {
  theme: Theme;
  showCompleted: boolean;
  newTaskPlacement: NewTaskPlacementPreference;
  onToggle: () => void;
  onToggleShowCompleted: () => void;
  onChangeNewTaskPlacement: (value: NewTaskPlacementPreference) => void;
  onOpenNotifications: () => void;
};

export function ThemeToggle({
  theme,
  showCompleted,
  newTaskPlacement,
  onToggle,
  onToggleShowCompleted,
  onChangeNewTaskPlacement,
  onOpenNotifications,
}: ThemeToggleProps) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);

  const closeMenu = useCallback(() => {
    setMenuOpen(false);
  }, []);

  useClickOutside([menuButtonRef, panelRef], menuOpen, closeMenu);

  return (
    <div className="relative flex items-center gap-2">
      <Button
        variant="outline"
        size="icon"
        aria-label={theme === "light" ? "Enable dark theme" : "Enable light theme"}
        onClick={onToggle}
      >
        {theme === "light" ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />}
      </Button>

      <Button
        ref={menuButtonRef}
        variant="outline"
        size="icon"
        aria-label="Open display options"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((current) => !current)}
      >
        <MoreHorizontal className="h-4 w-4" />
      </Button>

      {menuOpen ? (
        <PopoverPanel anchorRef={menuButtonRef} panelRef={panelRef} align="end" className="min-w-[16rem] p-2">
          <div className="space-y-1">
            <button
              type="button"
              className="flex w-full items-center justify-between rounded-[0.95rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
              onClick={() => {
                onToggleShowCompleted();
                closeMenu();
              }}
            >
              <span>{showCompleted ? "Hide Completed" : "Show Completed"}</span>
            </button>

            <div className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              New tasks
            </div>

            <button
              type="button"
              className="flex w-full items-center justify-between rounded-[0.95rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
              onClick={() => {
                onChangeNewTaskPlacement("start");
                closeMenu();
              }}
            >
              <span>Add task to the beginning</span>
              {newTaskPlacement === "start" ? <Check className="h-4 w-4 text-primary" /> : null}
            </button>

            <button
              type="button"
              className="flex w-full items-center justify-between rounded-[0.95rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
              onClick={() => {
                onChangeNewTaskPlacement("end");
                closeMenu();
              }}
            >
              <span>Add task to the end</span>
              {newTaskPlacement === "end" ? <Check className="h-4 w-4 text-primary" /> : null}
            </button>

            <div className="px-3 pb-1 pt-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
              Notifications
            </div>

            <button
              type="button"
              className="flex w-full items-center justify-between rounded-[0.95rem] px-3 py-2 text-left text-sm text-foreground transition-colors hover:bg-muted/70"
              onClick={() => {
                onOpenNotifications();
                closeMenu();
              }}
            >
              <span>Notifications</span>
            </button>
          </div>
        </PopoverPanel>
      ) : null}
    </div>
  );
}
