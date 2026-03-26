import {
  type ForwardedRef,
  forwardRef,
  memo,
  type DragEvent as ReactDragEvent,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from "react";
import { CheckCircle2, ChevronDown, Plus } from "lucide-react";

import { SubtaskRow } from "@/components/subtask-row";
import { Button } from "@/components/ui/button";
import {
  areDropIndicatorsEqual,
  moveOrderedIds,
  resolveReorderInsertDirection,
  type ReorderDropIndicator,
} from "@/lib/task-reorder";
import type { TaskSubtask } from "@/lib/types";

type SubtaskListProps = {
  subtasks: TaskSubtask[];
  addRequestKey: number;
  onCreate: (title: string) => Promise<void>;
  onUpdate: (subtask: TaskSubtask, title: string) => Promise<void>;
  onToggle: (subtask: TaskSubtask) => Promise<void>;
  onDelete: (subtask: TaskSubtask) => Promise<void>;
  onReorder: (subtaskIds: number[]) => Promise<void>;
};

export type SubtaskListHandle = {
  commitDraft: () => Promise<string | null>;
};

function SubtaskListInner({
  subtasks,
  addRequestKey,
  onCreate,
  onUpdate,
  onToggle,
  onDelete,
  onReorder,
}: SubtaskListProps,
ref: ForwardedRef<SubtaskListHandle>,
) {
  const [draftTitle, setDraftTitle] = useState<string | null>(null);
  const [draggingId, setDraggingId] = useState<number | null>(null);
  const [dropIndicator, setDropIndicator] = useState<ReorderDropIndicator | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const draggingIdRef = useRef<number | null>(null);
  const activeSubtasks = useMemo(() => subtasks.filter((subtask) => !subtask.is_done), [subtasks]);
  const doneSubtasks = useMemo(() => subtasks.filter((subtask) => subtask.is_done), [subtasks]);
  const activeSubtaskIds = useMemo(() => activeSubtasks.map((subtask) => subtask.id), [activeSubtasks]);
  const doneSubtaskIds = useMemo(() => doneSubtasks.map((subtask) => subtask.id), [doneSubtasks]);

  useEffect(() => {
    if (addRequestKey === 0) {
      return;
    }

    setDraftTitle("");
    window.requestAnimationFrame(() => inputRef.current?.focus());
  }, [addRequestKey]);

  async function commitDraft() {
    if (!draftTitle || !draftTitle.trim()) {
      setDraftTitle(null);
      return null;
    }

    const nextTitle = draftTitle.trim();
    await onCreate(nextTitle);
    setDraftTitle(null);
    return nextTitle;
  }

  useImperativeHandle(
    ref,
    () => ({
      commitDraft,
    }),
    [draftTitle, onCreate],
  );

  const setDropIndicatorIfChanged = useCallback((nextIndicator: ReorderDropIndicator | null) => {
    setDropIndicator((currentIndicator) =>
      areDropIndicatorsEqual(currentIndicator, nextIndicator) ? currentIndicator : nextIndicator,
    );
  }, []);

  const clearDragState = useCallback(() => {
    draggingIdRef.current = null;
    setDraggingId(null);
    setDropIndicatorIfChanged(null);
  }, [setDropIndicatorIfChanged]);

  const handleActiveSubtasksDragOver = useCallback(
    (event: ReactDragEvent<HTMLDivElement>) => {
      if (draggingIdRef.current === null) {
        return;
      }

      const targetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-subtask-drop-target-id]");
      if (!targetElement || !event.currentTarget.contains(targetElement)) {
        setDropIndicatorIfChanged(null);
        return;
      }

      const targetId = Number(targetElement.dataset.subtaskDropTargetId);
      if (!Number.isFinite(targetId) || targetId === draggingIdRef.current) {
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
    [setDropIndicatorIfChanged],
  );

  const handleActiveSubtasksDrop = useCallback(
    (event: ReactDragEvent<HTMLDivElement>) => {
      if (draggingIdRef.current === null) {
        return;
      }

      const targetElement = (event.target as HTMLElement).closest<HTMLElement>("[data-subtask-drop-target-id]");
      if (!targetElement || !event.currentTarget.contains(targetElement)) {
        clearDragState();
        return;
      }

      const targetId = Number(targetElement.dataset.subtaskDropTargetId);
      if (!Number.isFinite(targetId) || targetId === draggingIdRef.current) {
        clearDragState();
        return;
      }

      event.preventDefault();

      const direction =
        dropIndicator?.targetId === targetId
          ? dropIndicator.direction
          : resolveReorderInsertDirection(event.clientY, targetElement.getBoundingClientRect());
      const reorderedActiveIds = moveOrderedIds(activeSubtaskIds, draggingIdRef.current, targetId, direction);

      clearDragState();

      if (reorderedActiveIds.every((subtaskId, index) => subtaskId === activeSubtaskIds[index])) {
        return;
      }

      void onReorder([...reorderedActiveIds, ...doneSubtaskIds]);
    },
    [activeSubtaskIds, clearDragState, doneSubtaskIds, dropIndicator, onReorder],
  );

  return (
    <div className="space-y-2">
      <div
        className="space-y-2"
        onDragOver={handleActiveSubtasksDragOver}
        onDrop={handleActiveSubtasksDrop}
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
        {activeSubtasks.map((subtask) => (
          <div key={subtask.id} data-subtask-drop-target-id={subtask.id}>
            <SubtaskRow
              subtask={subtask}
              isDragging={draggingId === subtask.id}
              dropIndicator={dropIndicator?.targetId === subtask.id ? dropIndicator.direction : null}
              onToggle={(nextSubtask) => void onToggle(nextSubtask)}
              onUpdateTitle={(nextSubtask, title) => void onUpdate(nextSubtask, title)}
              onDelete={(nextSubtask) => void onDelete(nextSubtask)}
              onDragStart={(subtaskId) => {
                draggingIdRef.current = subtaskId;
                setDraggingId(subtaskId);
                setDropIndicatorIfChanged(null);
              }}
              onDragEnd={clearDragState}
            />
          </div>
        ))}
      </div>

      {doneSubtasks.length > 0 ? (
        <details className="group rounded-[1rem] border border-border/70 bg-background/35">
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
              <SubtaskRow
                key={subtask.id}
                subtask={subtask}
                isDragging={false}
                dragEnabled={false}
                onToggle={(nextSubtask) => void onToggle(nextSubtask)}
                onUpdateTitle={(nextSubtask, title) => void onUpdate(nextSubtask, title)}
                onDelete={(nextSubtask) => void onDelete(nextSubtask)}
                onDragStart={() => undefined}
                onDragEnd={() => undefined}
              />
            ))}
          </div>
        </details>
      ) : null}

      {draftTitle !== null ? (
        <div className="flex items-center gap-2 rounded-[1rem] border border-dashed border-border/70 bg-card/40 px-3 py-2.5">
          <Plus className="h-4 w-4 text-muted-foreground" />
          <input
            ref={inputRef}
            value={draftTitle}
            className="min-w-0 flex-1 border-0 bg-transparent p-0 text-sm text-foreground outline-none placeholder:text-muted-foreground"
            placeholder="New subtask"
            onChange={(event) => setDraftTitle(event.target.value)}
            onBlur={() => void commitDraft()}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                void commitDraft();
              }

              if (event.key === "Escape") {
                setDraftTitle(null);
              }
            }}
          />
        </div>
      ) : null}

      {draftTitle === null ? (
        <Button variant="outline" size="sm" onClick={() => setDraftTitle("")}>
          <Plus className="h-3.5 w-3.5" />
          Add subtask
        </Button>
      ) : null}
    </div>
  );
}

export const SubtaskList = memo(forwardRef(SubtaskListInner));
