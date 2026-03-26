import { type DragEvent as ReactDragEvent, type PointerEvent, useCallback, useRef, useState } from "react";
import { GripVertical, Inbox, Layers3, ListTodo, PanelLeftClose, Pencil, Plus, SunMedium, Sunrise } from "lucide-react";
import { Link, NavLink } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { areDropIndicatorsEqual, moveOrderedIds, resolveReorderInsertDirection, type ReorderDropIndicator } from "@/lib/task-reorder";
import type { ListItem } from "@/lib/types";
import { cn } from "@/lib/utils";

type SidebarCounts = {
  all: number;
  today: number;
  tomorrow: number;
  inbox: number;
  lists: number;
  listTaskCounts: Record<number, number>;
};

type SidebarProps = {
  lists: ListItem[];
  isLoading: boolean;
  counts?: SidebarCounts;
  currentSpace: "tasks" | "calendar";
  taskSpacePath: string;
  onCreateList: () => void;
  onEditList: (list: ListItem) => void;
  onReorderLists: (listIds: number[]) => Promise<void>;
  onHide: () => void;
  onResizeStart: (event: PointerEvent<HTMLButtonElement>) => void;
};

function sidebarLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    "flex items-center gap-3 rounded-[1.2rem] px-4 py-3 text-sm transition-colors duration-100",
    isActive
      ? "bg-primary text-primary-foreground shadow-panel"
      : "text-foreground hover:bg-muted/70",
  );
}

function CountBadge({ count, active = false }: { count: number; active?: boolean }) {
  return (
    <span
      className={cn(
        "ml-auto rounded-full px-2 py-0.5 text-[11px] font-medium",
        active ? "bg-primary-foreground/15 text-primary-foreground" : "bg-muted/60 text-muted-foreground",
      )}
    >
      {count}
    </span>
  );
}

export function Sidebar({
  lists,
  isLoading,
  counts,
  currentSpace,
  taskSpacePath,
  onCreateList,
  onEditList,
  onReorderLists,
  onHide,
  onResizeStart,
}: SidebarProps) {
  const [draggingListId, setDraggingListId] = useState<number | null>(null);
  const [dropIndicator, setDropIndicator] = useState<ReorderDropIndicator | null>(null);
  const draggingListIdRef = useRef<number | null>(null);
  const canReorderLists = lists.length > 1;
  const listIds = lists.map((list) => list.id);

  const setDropIndicatorIfChanged = useCallback((nextIndicator: ReorderDropIndicator | null) => {
    setDropIndicator((currentIndicator) =>
      areDropIndicatorsEqual(currentIndicator, nextIndicator) ? currentIndicator : nextIndicator,
    );
  }, []);

  const clearDragState = useCallback(() => {
    draggingListIdRef.current = null;
    setDraggingListId(null);
    setDropIndicatorIfChanged(null);
  }, [setDropIndicatorIfChanged]);

  const handleListDragOver = useCallback(
    (event: ReactDragEvent<HTMLDivElement>) => {
      if (!canReorderLists || draggingListIdRef.current === null) {
        return;
      }

      const targetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-list-drop-target-id]");
      if (!targetElement || !event.currentTarget.contains(targetElement)) {
        setDropIndicatorIfChanged(null);
        return;
      }

      const targetId = Number(targetElement.dataset.listDropTargetId);
      if (!Number.isFinite(targetId) || targetId === draggingListIdRef.current) {
        setDropIndicatorIfChanged(null);
        return;
      }

      event.preventDefault();
      event.dataTransfer.dropEffect = "move";

      setDropIndicatorIfChanged({
        targetId,
        direction: resolveReorderInsertDirection(event.clientY, targetElement.getBoundingClientRect()),
      });
    },
    [canReorderLists, setDropIndicatorIfChanged],
  );

  const handleListDrop = useCallback(
    (event: ReactDragEvent<HTMLDivElement>) => {
      if (!canReorderLists || draggingListIdRef.current === null) {
        return;
      }

      const targetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-list-drop-target-id]");
      if (!targetElement || !event.currentTarget.contains(targetElement)) {
        clearDragState();
        return;
      }

      const targetId = Number(targetElement.dataset.listDropTargetId);
      if (!Number.isFinite(targetId) || targetId === draggingListIdRef.current) {
        clearDragState();
        return;
      }

      event.preventDefault();

      const direction =
        dropIndicator?.targetId === targetId
          ? dropIndicator.direction
          : resolveReorderInsertDirection(event.clientY, targetElement.getBoundingClientRect());
      const reorderedListIds = moveOrderedIds(listIds, draggingListIdRef.current, targetId, direction);

      clearDragState();

      if (reorderedListIds.every((listId, index) => listId === listIds[index])) {
        return;
      }

      void onReorderLists(reorderedListIds);
    },
    [canReorderLists, clearDragState, dropIndicator, listIds, onReorderLists],
  );

  return (
    <aside className="relative flex h-full min-h-0 flex-col overflow-hidden rounded-[2rem] border border-border/70 bg-card/80 p-5 shadow-panel backdrop-blur-xl">
      <div className="mb-6 shrink-0 flex items-start justify-between gap-4">
        <div className="inline-flex items-center rounded-[1.3rem] border border-border/70 bg-background/60 p-1 shadow-sm">
          <Link
            to={taskSpacePath}
            aria-current={currentSpace === "tasks" ? "page" : undefined}
            className={cn(
              "rounded-[1rem] px-4 py-2 text-sm font-semibold transition-colors",
              currentSpace === "tasks" ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
            )}
          >
            Task
          </Link>
          <NavLink
            to="/calendar"
            className={({ isActive }) =>
              cn(
                "rounded-[1rem] px-4 py-2 text-sm font-semibold transition-colors",
                isActive || currentSpace === "calendar"
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "text-muted-foreground hover:text-foreground",
              )
            }
          >
            Calendar
          </NavLink>
        </div>
        <Button variant="ghost" size="icon" aria-label="Hide sidebar" onClick={onHide}>
          <PanelLeftClose className="h-4 w-4" />
        </Button>
      </div>

      <div className="hover-scrollbar min-h-0 flex-1 overflow-y-auto overscroll-y-contain pr-1">
        <nav className="space-y-2">
          <NavLink end to="/" className={sidebarLinkClass}>
            {({ isActive }) => (
              <>
                <Layers3 className="h-4 w-4" />
                <span>All</span>
                {counts ? <CountBadge count={counts.all} active={isActive} /> : null}
              </>
            )}
          </NavLink>
          <NavLink to="/today" className={sidebarLinkClass}>
            {({ isActive }) => (
              <>
                <SunMedium className="h-4 w-4" />
                <span>Today</span>
                {counts ? <CountBadge count={counts.today} active={isActive} /> : null}
              </>
            )}
          </NavLink>
          <NavLink to="/tomorrow" className={sidebarLinkClass}>
            {({ isActive }) => (
              <>
                <Sunrise className="h-4 w-4" />
                <span>Tomorrow</span>
                {counts ? <CountBadge count={counts.tomorrow} active={isActive} /> : null}
              </>
            )}
          </NavLink>
          <NavLink to="/inbox" className={sidebarLinkClass}>
            {({ isActive }) => (
              <>
                <Inbox className="h-4 w-4" />
                <span>Inbox</span>
                {counts ? <CountBadge count={counts.inbox} active={isActive} /> : null}
              </>
            )}
          </NavLink>
        </nav>

        <div className="mt-8 pb-1">
          <div className="mb-3 flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">
              <ListTodo className="h-4 w-4" />
              <span>Lists</span>
              {counts ? <CountBadge count={counts.lists} /> : null}
            </div>
            <Button variant="ghost" size="icon" aria-label="Create list" onClick={onCreateList}>
              <Plus className="h-4 w-4" />
            </Button>
          </div>

          <div
            className="space-y-2"
            onDragOver={handleListDragOver}
            onDrop={handleListDrop}
            onDragLeave={(event) => {
              if (event.target !== event.currentTarget) {
                return;
              }

              const nextTarget = event.relatedTarget;
              if (nextTarget instanceof Node && event.currentTarget.contains(nextTarget)) {
                return;
              }

              setDropIndicatorIfChanged(null);
            }}
          >
            {isLoading ? <p className="px-2 py-3 text-sm text-muted-foreground">Loading lists...</p> : null}
            {!isLoading && lists.length === 0 ? (
              <p className="rounded-[1.2rem] border border-dashed border-border/70 px-4 py-4 text-sm text-muted-foreground">
                No lists yet. Create one to group related tasks.
              </p>
            ) : null}
            {lists.map((list) => (
              <div
                key={list.id}
                data-list-drop-target-id={list.id}
                className="group relative rounded-[1.2rem] hover:bg-muted/50"
              >
                {dropIndicator?.targetId === list.id ? (
                  <div
                    aria-hidden="true"
                    className={cn(
                      "pointer-events-none absolute left-3 right-3 z-10 h-1 rounded-full bg-sky-500 shadow-[0_0_0_1px_rgba(14,165,233,0.4),0_0_16px_rgba(14,165,233,0.35)]",
                      dropIndicator.direction === "before" ? "top-1" : "bottom-1",
                    )}
                  />
                ) : null}

                <div className="flex items-center gap-2">
                  {canReorderLists ? (
                    <button
                      type="button"
                      draggable
                      className="ml-1 inline-flex h-8 w-8 shrink-0 cursor-grab items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-muted/70 hover:text-foreground active:cursor-grabbing"
                      aria-label={`Reorder ${list.name}`}
                      onClick={(event) => event.stopPropagation()}
                      onPointerDown={(event) => event.stopPropagation()}
                      onDragStart={(event) => {
                        event.stopPropagation();
                        event.dataTransfer.effectAllowed = "move";
                        draggingListIdRef.current = list.id;
                        setDraggingListId(list.id);
                        setDropIndicatorIfChanged(null);
                      }}
                      onDragEnd={clearDragState}
                    >
                      <GripVertical className="h-4 w-4" />
                    </button>
                  ) : null}

                  <NavLink to={`/lists/${list.id}`} className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-sm">
                    {({ isActive }) => (
                      <>
                        <span
                          aria-hidden="true"
                          className={cn("h-2.5 w-2.5 rounded-full", isActive && "ring-2 ring-foreground/15")}
                          style={{ backgroundColor: list.color }}
                        />
                        <span className={cn("truncate", isActive ? "font-semibold text-foreground" : "text-foreground")}>
                          {list.name}
                        </span>
                        {counts ? <CountBadge count={counts.listTaskCounts[list.id] ?? 0} active={isActive} /> : null}
                      </>
                    )}
                  </NavLink>

                  <Button
                    variant="ghost"
                    size="icon"
                    className={cn(
                      "mr-1 opacity-100 lg:opacity-0 lg:group-hover:opacity-100",
                      draggingListId === list.id && "pointer-events-none opacity-0",
                    )}
                    aria-label={`Edit ${list.name}`}
                    onClick={() => onEditList(list)}
                  >
                    <Pencil className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <button
        type="button"
        aria-label="Resize sidebar"
        className="absolute inset-y-4 right-0 hidden w-4 -translate-x-1 cursor-col-resize items-center justify-center lg:flex"
        onPointerDown={onResizeStart}
      >
        <span className="h-16 w-1 rounded-full bg-border/80" />
      </button>
    </aside>
  );
}
