import { memo, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type PointerEvent as ReactPointerEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { AlarmClock, CalendarDays, ChevronLeft, ChevronRight, LayoutGrid, Plus, Rows3 } from "lucide-react";

import { EmptyState } from "@/components/empty-state";
import { TaskDialog } from "@/components/task-dialog";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  createTask,
  deleteTask,
  reorderSubtasks,
  reorderTopLevelTasks,
  toggleTask,
  updateTask,
} from "@/lib/api";
import { addDays, getLocalDateString, parseLocalDateString } from "@/lib/date";
import { buildTaskDraft, type TaskDraftOverrides } from "@/lib/task-draft";
import { findTaskInTaskCaches, removeSubtaskFromCaches, removeTaskFromCaches, upsertSubtaskInCaches, upsertTaskInCaches, upsertTasksInCaches } from "@/lib/task-cache";
import {
  addCalendarMonths,
  buildTaskOccurrencesInRange,
  compareTaskOccurrences,
  getCalendarDays,
  getCalendarWeekDays,
  getCalendarWeekRange,
  getCalendarMonthRange,
  groupTaskOccurrencesByDate,
  type TaskOccurrence,
} from "@/lib/task-calendar";
import { buildTopLevelTaskReorderScopeForTask, getTopLevelTaskIdsForReorderScope } from "@/lib/task-groups";
import { getTaskPriorityOption } from "@/lib/task-options";
import { formatTaskTimeRange } from "@/lib/task-time";
import { insertOrderedId } from "@/lib/task-reorder";
import type { ListItem, NewTaskPlacementPreference, TaskCreatePayload, TaskDraft, TaskItem, TaskSubtask, TaskUpdatePayload } from "@/lib/types";
import { cn } from "@/lib/utils";

type CalendarPageProps = {
  tasks: TaskItem[];
  tasksLoading: boolean;
  tasksError: boolean;
  onRetry: () => void;
  lists: ListItem[];
  showCompleted: boolean;
  newTaskPlacement: NewTaskPlacementPreference;
  todayString: string;
  tomorrowString: string;
};

type CalendarPageViewProps = {
  tasks: TaskItem[];
  lists: ListItem[];
  showCompleted: boolean;
  todayString: string;
  onOpenTask: (task: TaskItem) => void;
  onCreateTask: (dateString: string) => void;
};

type CalendarViewMode = "month" | "week";

const WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const MONTH_DAY_OCCURRENCE_LIMIT = 3;
const MONTH_AGENDA_MIN_WIDTH = 240;
const MONTH_AGENDA_MAX_WIDTH = 440;
const MONTH_CALENDAR_MIN_WIDTH = 640;
const WEEK_CALENDAR_MIN_HEIGHT = 240;
const WEEK_CALENDAR_MAX_HEIGHT = 520;
const WEEK_AGENDA_MIN_HEIGHT = 220;
const DAY_FORMATTER = new Intl.DateTimeFormat("en-US", { weekday: "short", month: "short", day: "numeric" });
const MONTH_TITLE_FORMATTER = new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" });
const WEEK_TITLE_MONTH_FORMATTER = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" });
const WEEK_TITLE_FULL_FORMATTER = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" });

function getDefaultMonthAgendaWidth(): number {
  if (typeof window === "undefined") {
    return 260;
  }

  return Math.round(Math.min(MONTH_AGENDA_MAX_WIDTH, Math.max(MONTH_AGENDA_MIN_WIDTH, window.innerWidth * 0.16)));
}

function clampMonthAgendaWidth(width: number, containerWidth: number): number {
  const maxWidth = Math.max(
    MONTH_AGENDA_MIN_WIDTH,
    Math.min(MONTH_AGENDA_MAX_WIDTH, containerWidth - MONTH_CALENDAR_MIN_WIDTH),
  );

  return Math.min(Math.max(width, MONTH_AGENDA_MIN_WIDTH), maxWidth);
}

function getDefaultWeekCalendarHeight(): number {
  if (typeof window === "undefined") {
    return 340;
  }

  return Math.round(Math.min(WEEK_CALENDAR_MAX_HEIGHT, Math.max(WEEK_CALENDAR_MIN_HEIGHT, window.innerHeight * 0.38)));
}

function clampWeekCalendarHeight(height: number, containerHeight: number): number {
  const maxHeight = Math.max(
    WEEK_CALENDAR_MIN_HEIGHT,
    Math.min(WEEK_CALENDAR_MAX_HEIGHT, containerHeight - WEEK_AGENDA_MIN_HEIGHT),
  );

  return Math.min(Math.max(height, WEEK_CALENDAR_MIN_HEIGHT), maxHeight);
}

function parseHexColor(rawColor: string | null | undefined): { red: number; green: number; blue: number } | null {
  if (!rawColor) {
    return null;
  }

  const normalized = rawColor.trim().replace("#", "");
  const fullHex =
    normalized.length === 3
      ? normalized
          .split("")
          .map((part) => `${part}${part}`)
          .join("")
      : normalized;

  if (!/^[0-9a-fA-F]{6}$/.test(fullHex)) {
    return null;
  }

  const parsed = Number.parseInt(fullHex, 16);

  return {
    red: (parsed >> 16) & 0xff,
    green: (parsed >> 8) & 0xff,
    blue: parsed & 0xff,
  };
}

function toColorAlpha(rawColor: string, alpha: number): string {
  const color = parseHexColor(rawColor);
  if (!color) {
    return rawColor;
  }

  return `rgba(${color.red}, ${color.green}, ${color.blue}, ${alpha})`;
}

function getDaysInMonth(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
}

function getMonthSelectionDate(selectedDateString: string, nextMonthDate: Date): Date {
  const selectedDate = parseLocalDateString(selectedDateString) ?? nextMonthDate;
  const nextDayOfMonth = Math.min(selectedDate.getDate(), getDaysInMonth(nextMonthDate));
  return new Date(nextMonthDate.getFullYear(), nextMonthDate.getMonth(), nextDayOfMonth);
}

function formatWeekTitle(start: Date, end: Date): string {
  if (start.getFullYear() === end.getFullYear() && start.getMonth() === end.getMonth()) {
    return `${WEEK_TITLE_MONTH_FORMATTER.format(start)} - ${end.getDate()}, ${end.getFullYear()}`;
  }

  if (start.getFullYear() === end.getFullYear()) {
    return `${WEEK_TITLE_MONTH_FORMATTER.format(start)} - ${WEEK_TITLE_FULL_FORMATTER.format(end)}`;
  }

  return `${WEEK_TITLE_FULL_FORMATTER.format(start)} - ${WEEK_TITLE_FULL_FORMATTER.format(end)}`;
}

function formatSelectedDayLabel(dateString: string): string {
  const date = parseLocalDateString(dateString);
  return date ? DAY_FORMATTER.format(date) : dateString;
}

function buildOccurrenceChipStyle(occurrence: TaskOccurrence, listById: Map<number, ListItem>) {
  const listColor = occurrence.task.list_id ? listById.get(occurrence.task.list_id)?.color : null;
  const accentColor = listColor || getTaskPriorityOption(occurrence.task.priority).accentColor;

  return {
    borderColor: toColorAlpha(accentColor, occurrence.task.is_done ? 0.24 : 0.42),
    backgroundColor: toColorAlpha(accentColor, occurrence.task.is_done ? 0.12 : 0.18),
    color: accentColor,
  };
}

function buildOccurrenceTimeBadgeStyle(task: TaskItem) {
  const accentColor = getTaskPriorityOption(task.priority).accentColor;

  return {
    borderColor: toColorAlpha(accentColor, 0.42),
    backgroundColor: toColorAlpha(accentColor, 0.16),
    color: accentColor,
  };
}

function CalendarTaskChip({
  occurrence,
  listById,
  onOpenTask,
  variant = "month",
}: {
  occurrence: TaskOccurrence;
  listById: Map<number, ListItem>;
  onOpenTask: (task: TaskItem) => void;
  variant?: "month" | "week";
}) {
  const timeLabel = formatTaskTimeRange(occurrence.task.start_time, occurrence.task.end_time);
  const showWeekTimeBadge = variant === "week" && Boolean(timeLabel);
  const showMonthTimeIcon = variant === "month" && Boolean(timeLabel);

  return (
    <button
      type="button"
      className={cn(
        "flex w-full rounded-xl border px-2.5 py-1.5 text-left transition-transform duration-100 hover:translate-y-[-1px]",
        showWeekTimeBadge ? "flex-col items-start gap-2" : "items-center gap-2",
        occurrence.task.is_done && "opacity-70",
      )}
      style={buildOccurrenceChipStyle(occurrence, listById)}
      onClick={(event) => {
        event.stopPropagation();
        onOpenTask(occurrence.task);
      }}
      >
      {showWeekTimeBadge ? (
        <span
          className="inline-flex min-h-7 min-w-[6.5rem] max-w-[72%] items-center justify-center rounded-full border px-3 py-1 text-[11px] font-semibold"
          style={buildOccurrenceTimeBadgeStyle(occurrence.task)}
        >
          {timeLabel}
        </span>
      ) : null}

      <div className="flex w-full items-center gap-2">
        <span className={cn("min-w-0 flex-1 truncate text-[13px] font-medium", occurrence.task.is_done && "line-through")}>
          {occurrence.task.title}
        </span>
        {showMonthTimeIcon ? (
          <AlarmClock className="h-3.5 w-3.5 shrink-0 opacity-85" aria-label={`Scheduled at ${timeLabel}`} />
        ) : null}
      </div>
    </button>
  );
}

function CalendarAgendaSection({
  title,
  occurrences,
  listById,
  onOpenTask,
}: {
  title: string;
  occurrences: TaskOccurrence[];
  listById: Map<number, ListItem>;
  onOpenTask: (task: TaskItem) => void;
}) {
  if (occurrences.length === 0) {
    return null;
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-muted-foreground">{title}</h3>
        <span className="rounded-full bg-muted/70 px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
          {occurrences.length}
        </span>
      </div>

      <div className="space-y-2">
        {occurrences.map((occurrence) => (
          <button
            key={`${occurrence.task.id}:${occurrence.dateString}`}
            type="button"
            className="flex w-full items-start gap-3 rounded-[1.2rem] border border-border/70 bg-background/40 px-4 py-3 text-left transition-colors hover:bg-background/70"
            onClick={() => onOpenTask(occurrence.task)}
          >
            <span
              aria-hidden="true"
              className="mt-1 h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: buildOccurrenceChipStyle(occurrence, listById).color }}
            />
            <div className="min-w-0 flex-1">
              {formatTaskTimeRange(occurrence.task.start_time, occurrence.task.end_time) ? (
                <span
                  className="mb-2 inline-flex min-h-7 min-w-[6.5rem] max-w-[38%] items-center justify-center rounded-full border px-3 py-1 text-[11px] font-semibold"
                  style={buildOccurrenceTimeBadgeStyle(occurrence.task)}
                >
                  {formatTaskTimeRange(occurrence.task.start_time, occurrence.task.end_time)}
                </span>
              ) : null}
              <div className="flex items-start gap-3">
                <span className={cn("min-w-0 flex-1 truncate text-sm font-semibold text-foreground", occurrence.task.is_done && "text-muted-foreground line-through")}>
                  {occurrence.task.title}
                </span>
              </div>
              <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                <span>{occurrence.task.list_id ? listById.get(occurrence.task.list_id)?.name ?? "List" : "Inbox"}</span>
                {occurrence.isRecurring ? <span>Recurring</span> : null}
              </div>
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

export const CalendarPageView = memo(function CalendarPageView({
  tasks,
  lists,
  showCompleted,
  todayString,
  onOpenTask,
  onCreateTask,
}: CalendarPageViewProps) {
  const todayDate = useMemo(() => parseLocalDateString(todayString) ?? new Date(), [todayString]);
  const [viewMode, setViewMode] = useState<CalendarViewMode>("month");
  const [anchorDate, setAnchorDate] = useState<Date>(todayDate);
  const [selectedDateString, setSelectedDateString] = useState(todayString);
  const [monthAgendaWidth, setMonthAgendaWidth] = useState(getDefaultMonthAgendaWidth);
  const [weekCalendarHeight, setWeekCalendarHeight] = useState(getDefaultWeekCalendarHeight);
  const [isResizingMonth, setIsResizingMonth] = useState(false);
  const [isResizingWeek, setIsResizingWeek] = useState(false);
  const monthLayoutRef = useRef<HTMLDivElement | null>(null);
  const weekLayoutRef = useRef<HTMLDivElement | null>(null);
  const weekCalendarScrollRef = useRef<HTMLDivElement | null>(null);
  const monthResizeStateRef = useRef<{ startX: number; startWidth: number; containerWidth: number } | null>(null);
  const weekResizeStateRef = useRef<{ startY: number; startHeight: number; containerHeight: number } | null>(null);
  const listById = useMemo(() => new Map(lists.map((list) => [list.id, list])), [lists]);
  const visibleTasks = useMemo(() => (showCompleted ? tasks : tasks.filter((task) => !task.is_done)), [showCompleted, tasks]);
  const visibleRange = useMemo(
    () => (viewMode === "month" ? getCalendarMonthRange(anchorDate) : getCalendarWeekRange(anchorDate)),
    [anchorDate, viewMode],
  );
  const visibleDays = useMemo(
    () => (viewMode === "month" ? getCalendarDays(anchorDate) : getCalendarWeekDays(anchorDate)),
    [anchorDate, viewMode],
  );
  const occurrencesByDate = useMemo(() => {
    const groupedOccurrences = groupTaskOccurrencesByDate(buildTaskOccurrencesInRange(visibleTasks, visibleRange));

    for (const [dateString, dateOccurrences] of groupedOccurrences) {
      groupedOccurrences.set(dateString, [...dateOccurrences].sort(compareTaskOccurrences));
    }

    return groupedOccurrences;
  }, [visibleRange, visibleTasks]);

  const selectedOccurrences = occurrencesByDate.get(selectedDateString) ?? [];
  const activeOccurrences = useMemo(
    () => selectedOccurrences.filter((occurrence) => !occurrence.task.is_done),
    [selectedOccurrences],
  );
  const completedOccurrences = useMemo(
    () => selectedOccurrences.filter((occurrence) => occurrence.task.is_done),
    [selectedOccurrences],
  );
  const periodTitle = useMemo(() => {
    if (viewMode === "month") {
      return MONTH_TITLE_FORMATTER.format(anchorDate);
    }

    return formatWeekTitle(visibleRange.start, visibleRange.end);
  }, [anchorDate, viewMode, visibleRange.end, visibleRange.start]);

  useEffect(() => {
    if (viewMode !== "week") {
      return;
    }

    window.requestAnimationFrame(() => {
      weekCalendarScrollRef.current?.scrollTo({ top: 0, left: 0 });
    });
  }, [anchorDate, viewMode]);

  const clampSplitLayout = useCallback(() => {
    if (monthLayoutRef.current) {
      const nextWidth = clampMonthAgendaWidth(monthAgendaWidth, monthLayoutRef.current.getBoundingClientRect().width);
      if (nextWidth !== monthAgendaWidth) {
        setMonthAgendaWidth(nextWidth);
      }
    }

    if (weekLayoutRef.current) {
      const nextHeight = clampWeekCalendarHeight(weekCalendarHeight, weekLayoutRef.current.getBoundingClientRect().height);
      if (nextHeight !== weekCalendarHeight) {
        setWeekCalendarHeight(nextHeight);
      }
    }
  }, [monthAgendaWidth, weekCalendarHeight]);

  useEffect(() => {
    clampSplitLayout();
    window.addEventListener("resize", clampSplitLayout);
    return () => window.removeEventListener("resize", clampSplitLayout);
  }, [clampSplitLayout]);

  useEffect(() => {
    if (!isResizingMonth || !monthResizeStateRef.current) {
      return;
    }

    const handlePointerMove = (event: PointerEvent) => {
      if (!monthResizeStateRef.current) {
        return;
      }

      const nextWidth = clampMonthAgendaWidth(
        monthResizeStateRef.current.startWidth - (event.clientX - monthResizeStateRef.current.startX),
        monthResizeStateRef.current.containerWidth,
      );
      setMonthAgendaWidth(nextWidth);
    };

    const handlePointerUp = () => {
      setIsResizingMonth(false);
      monthResizeStateRef.current = null;
    };

    document.body.style.cursor = "col-resize";
    document.body.style.userSelect = "none";
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);

    return () => {
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };
  }, [isResizingMonth]);

  useEffect(() => {
    if (!isResizingWeek || !weekResizeStateRef.current) {
      return;
    }

    const handlePointerMove = (event: PointerEvent) => {
      if (!weekResizeStateRef.current) {
        return;
      }

      const nextHeight = clampWeekCalendarHeight(
        weekResizeStateRef.current.startHeight + (event.clientY - weekResizeStateRef.current.startY),
        weekResizeStateRef.current.containerHeight,
      );
      setWeekCalendarHeight(nextHeight);
    };

    const handlePointerUp = () => {
      setIsResizingWeek(false);
      weekResizeStateRef.current = null;
    };

    document.body.style.cursor = "row-resize";
    document.body.style.userSelect = "none";
    window.addEventListener("pointermove", handlePointerMove);
    window.addEventListener("pointerup", handlePointerUp);

    return () => {
      document.body.style.cursor = "";
      document.body.style.userSelect = "";
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerup", handlePointerUp);
    };
  }, [isResizingWeek]);

  const navigatePeriod = useCallback(
    (direction: -1 | 1) => {
      if (viewMode === "month") {
        const nextMonthDate = addCalendarMonths(anchorDate, direction);
        const nextSelectedDate = getMonthSelectionDate(selectedDateString, nextMonthDate);

        setAnchorDate(nextMonthDate);
        setSelectedDateString(getLocalDateString(nextSelectedDate));
        return;
      }

      const nextAnchorDate = addDays(anchorDate, direction * 7);
      const selectedDate = parseLocalDateString(selectedDateString) ?? anchorDate;

      setAnchorDate(nextAnchorDate);
      setSelectedDateString(getLocalDateString(addDays(selectedDate, direction * 7)));
    },
    [anchorDate, selectedDateString, viewMode],
  );

  const handleSelectDate = useCallback((date: Date, dateString: string) => {
    setAnchorDate(date);
    setSelectedDateString(dateString);
  }, []);

  const jumpToToday = useCallback(() => {
    setAnchorDate(todayDate);
    setSelectedDateString(todayString);
  }, [todayDate, todayString]);

  const handleMonthResizeStart = useCallback(
    (event: ReactPointerEvent<HTMLButtonElement>) => {
      if (!monthLayoutRef.current) {
        return;
      }

      monthResizeStateRef.current = {
        startX: event.clientX,
        startWidth: monthAgendaWidth,
        containerWidth: monthLayoutRef.current.getBoundingClientRect().width,
      };
      setIsResizingMonth(true);
      event.preventDefault();
    },
    [monthAgendaWidth],
  );

  const handleWeekResizeStart = useCallback(
    (event: ReactPointerEvent<HTMLButtonElement>) => {
      if (!weekLayoutRef.current) {
        return;
      }

      weekResizeStateRef.current = {
        startY: event.clientY,
        startHeight: weekCalendarHeight,
        containerHeight: weekLayoutRef.current.getBoundingClientRect().height,
      };
      setIsResizingWeek(true);
      event.preventDefault();
    },
    [weekCalendarHeight],
  );

  const monthLayoutStyle = useMemo(
    () => ({ "--calendar-agenda-width": `${monthAgendaWidth}px` }) as CSSProperties,
    [monthAgendaWidth],
  );
  const weekLayoutStyle = useMemo(
    () => ({ "--calendar-week-height": `${weekCalendarHeight}px` }) as CSSProperties,
    [weekCalendarHeight],
  );

  const agendaContent = (
    <>
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <CalendarDays className="h-4 w-4 text-muted-foreground" />
            <h2 className="text-lg font-semibold text-foreground">{formatSelectedDayLabel(selectedDateString)}</h2>
          </div>
          <p className="mt-1 text-sm text-muted-foreground">
            {selectedOccurrences.length === 0 ? "No tasks scheduled for this date." : `${selectedOccurrences.length} task${selectedOccurrences.length === 1 ? "" : "s"}`}
          </p>
        </div>
        <Button variant="outline" onClick={() => onCreateTask(selectedDateString)}>
          <Plus className="h-4 w-4" />
          Create task
        </Button>
      </div>

      {selectedOccurrences.length === 0 ? (
        <div className="mt-5 rounded-[1.4rem] border border-dashed border-border/70 px-4 py-8 text-center text-sm text-muted-foreground">
          The day is clear. Create a task here to prefill {selectedDateString}.
        </div>
      ) : (
        <div className="mt-5 space-y-5">
          <CalendarAgendaSection title="Active" occurrences={activeOccurrences} listById={listById} onOpenTask={onOpenTask} />
          {showCompleted ? (
            <CalendarAgendaSection
              title="Completed"
              occurrences={completedOccurrences}
              listById={listById}
              onOpenTask={onOpenTask}
            />
          ) : null}
        </div>
      )}
    </>
  );

  const monthCalendarContent = (
    <div className="min-w-[760px]">
      <div className="grid grid-cols-7 border-b border-border/70 bg-background/30">
        {WEEKDAY_LABELS.map((label) => (
          <div
            key={label}
            className="px-4 py-3 text-center text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground"
          >
            {label}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7">
        {visibleDays.map((day) => {
          const dayOccurrences = occurrencesByDate.get(day.dateString) ?? [];
          const visibleOccurrences = dayOccurrences.slice(0, MONTH_DAY_OCCURRENCE_LIMIT);
          const hiddenCount = Math.max(0, dayOccurrences.length - visibleOccurrences.length);
          const isToday = day.dateString === todayString;
          const isSelected = day.dateString === selectedDateString;

          return (
            <div
              key={day.dateString}
              role="gridcell"
              aria-selected={isSelected}
              className={cn(
                "min-h-[154px] border-b border-r border-border/70 p-3 transition-colors",
                isSelected ? "bg-primary/8" : "bg-transparent hover:bg-background/40",
              )}
              onClick={() => handleSelectDate(day.date, day.dateString)}
            >
              <div className="mb-3 flex items-center justify-between gap-2">
                <span
                  className={cn(
                    "inline-flex h-8 min-w-8 items-center justify-center rounded-full px-2 text-sm font-semibold",
                    isSelected
                      ? "bg-primary text-primary-foreground"
                      : isToday
                        ? "bg-primary/15 text-primary"
                        : day.isCurrentMonth
                          ? "text-foreground"
                          : "text-muted-foreground/70",
                  )}
                >
                  {day.date.getDate()}
                </span>
                {isToday ? <span className="text-[11px] font-medium uppercase tracking-[0.16em] text-primary">Today</span> : null}
              </div>

              <div className="space-y-2">
                {visibleOccurrences.map((occurrence) => (
                  <CalendarTaskChip
                    key={`${occurrence.task.id}:${occurrence.dateString}`}
                    occurrence={occurrence}
                    listById={listById}
                    onOpenTask={onOpenTask}
                    variant="month"
                  />
                ))}

                {hiddenCount > 0 ? (
                  <button
                    type="button"
                    className="inline-flex items-center rounded-full px-2 py-1 text-xs font-medium text-muted-foreground transition-colors hover:bg-background/60 hover:text-foreground"
                    onClick={(event) => {
                      event.stopPropagation();
                      handleSelectDate(day.date, day.dateString);
                    }}
                  >
                    +{hiddenCount} more
                  </button>
                ) : null}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  const weekCalendarContent = (
    <div className="min-w-[760px]">
      <div className="grid grid-cols-7 border-b border-border/70 bg-background/30">
        {WEEKDAY_LABELS.map((label) => (
          <div
            key={label}
            className="px-4 py-3 text-center text-xs font-semibold uppercase tracking-[0.22em] text-muted-foreground"
          >
            {label}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7">
        {visibleDays.map((day) => {
          const isToday = day.dateString === todayString;
          const isSelected = day.dateString === selectedDateString;
          const dayOccurrences = occurrencesByDate.get(day.dateString) ?? [];

          return (
            <div
              key={day.dateString}
              className={cn(
                "min-h-[280px] border-r border-border/70 p-3 last:border-r-0",
                isSelected ? "bg-primary/8" : "bg-transparent",
              )}
            >
              <button
                type="button"
                className="mb-4 flex w-full items-center justify-between gap-3 rounded-[1rem] bg-background/40 px-3 py-2 text-left transition-colors hover:bg-background/60"
                onClick={() => handleSelectDate(day.date, day.dateString)}
              >
                <div>
                  <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {WEEKDAY_LABELS[(day.date.getDay() + 6) % 7]}
                  </div>
                  <div className={cn("mt-1 text-lg font-semibold", day.isCurrentMonth ? "text-foreground" : "text-muted-foreground")}>
                    {day.date.getDate()}
                  </div>
                </div>
                {isToday ? (
                  <span className="rounded-full bg-primary/15 px-2 py-1 text-xs font-medium text-primary">Today</span>
                ) : isSelected ? (
                  <span className="rounded-full bg-primary px-2 py-1 text-xs font-medium text-primary-foreground">Selected</span>
                ) : null}
              </button>

              <div className="space-y-2">
                {dayOccurrences.length === 0 ? (
                  <div className="rounded-[1rem] border border-dashed border-border/70 px-3 py-4 text-center text-sm text-muted-foreground">
                    No tasks
                  </div>
                ) : (
                  dayOccurrences.map((occurrence) => (
                    <CalendarTaskChip
                      key={`${occurrence.task.id}:${occurrence.dateString}`}
                      occurrence={occurrence}
                      listById={listById}
                      onOpenTask={onOpenTask}
                      variant="week"
                    />
                  ))
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );

  return (
    <div className="flex min-h-0 flex-col gap-4 lg:h-full">
      <section className="rounded-[2rem] border border-border/70 bg-card/80 p-4 shadow-panel backdrop-blur-xl lg:p-5">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">Calendar</p>
            <h1 className="mt-2 text-2xl font-semibold text-foreground">{periodTitle}</h1>
          </div>

          <div className="flex flex-wrap items-center gap-2 lg:justify-end">
            <Button variant="outline" size="icon" aria-label="Previous period" onClick={() => navigatePeriod(-1)}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="outline" size="icon" aria-label="Next period" onClick={() => navigatePeriod(1)}>
              <ChevronRight className="h-4 w-4" />
            </Button>
            <Button variant="outline" onClick={jumpToToday}>
              Today
            </Button>
            <Button size="icon" aria-label="Create task on selected date" onClick={() => onCreateTask(selectedDateString)}>
              <Plus className="h-4 w-4" />
            </Button>
            <div className="inline-flex items-center rounded-full border border-border/70 bg-background/60 p-1 shadow-sm">
              <button
                type="button"
                aria-pressed={viewMode === "month"}
                className={cn(
                  "inline-flex items-center gap-2 rounded-full px-3 py-2 text-sm font-medium transition-colors",
                  viewMode === "month" ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
                )}
                onClick={() => {
                  setViewMode("month");
                  setAnchorDate(parseLocalDateString(selectedDateString) ?? todayDate);
                }}
              >
                <LayoutGrid className="h-4 w-4" />
                <span>Month</span>
              </button>
              <button
                type="button"
                aria-pressed={viewMode === "week"}
                className={cn(
                  "inline-flex items-center gap-2 rounded-full px-3 py-2 text-sm font-medium transition-colors",
                  viewMode === "week" ? "bg-primary text-primary-foreground shadow-sm" : "text-muted-foreground hover:text-foreground",
                )}
                onClick={() => {
                  setViewMode("week");
                  setAnchorDate(parseLocalDateString(selectedDateString) ?? todayDate);
                }}
              >
                <Rows3 className="h-4 w-4" />
                <span>Week</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <section className="flex min-h-0 flex-1 flex-col gap-4 lg:overflow-hidden">
        {viewMode === "month" ? (
          <div
            ref={monthLayoutRef}
            style={monthLayoutStyle}
            className="flex min-h-0 flex-col gap-4 lg:flex-row lg:gap-0"
          >
            <div className="min-h-0 overflow-auto rounded-[2rem] border border-border/70 bg-card/80 shadow-panel backdrop-blur-xl lg:min-w-0 lg:flex-1">
              {monthCalendarContent}
            </div>

            <button
              type="button"
              role="separator"
              aria-orientation="vertical"
              aria-label="Resize month calendar panels"
              data-testid="calendar-month-split-handle"
              className="group hidden w-4 shrink-0 cursor-col-resize items-center justify-center self-stretch lg:flex"
              onPointerDown={handleMonthResizeStart}
            >
              <span className="h-24 w-1 rounded-full bg-border/80 transition-colors group-hover:bg-primary/45 group-active:bg-primary/65" />
            </button>

            <section className="rounded-[2rem] border border-border/70 bg-card/80 p-5 shadow-panel backdrop-blur-xl lg:min-h-0 lg:w-[var(--calendar-agenda-width)] lg:flex-none lg:overflow-auto">
              {agendaContent}
            </section>
          </div>
        ) : (
          <div
            ref={weekLayoutRef}
            style={weekLayoutStyle}
            className="flex min-h-0 flex-col gap-4 lg:gap-0"
          >
            <div
              ref={weekCalendarScrollRef}
              className="min-h-0 overflow-auto rounded-[2rem] border border-border/70 bg-card/80 shadow-panel backdrop-blur-xl lg:h-[var(--calendar-week-height)] lg:flex-none"
            >
              {weekCalendarContent}
            </div>

            <button
              type="button"
              role="separator"
              aria-orientation="horizontal"
              aria-label="Resize week calendar panels"
              data-testid="calendar-week-split-handle"
              className="group hidden h-4 w-full shrink-0 cursor-row-resize items-center justify-center lg:flex"
              onPointerDown={handleWeekResizeStart}
            >
              <span className="h-1 w-24 rounded-full bg-border/80 transition-colors group-hover:bg-primary/45 group-active:bg-primary/65" />
            </button>

            <section className="rounded-[2rem] border border-border/70 bg-card/80 p-5 shadow-panel backdrop-blur-xl lg:min-h-0 lg:flex-1 lg:overflow-auto">
              {agendaContent}
            </section>
          </div>
        )}
      </section>
    </div>
  );
});

export function CalendarPage({
  tasks,
  tasksLoading,
  tasksError,
  onRetry,
  lists,
  showCompleted,
  newTaskPlacement,
  todayString,
  tomorrowString,
}: CalendarPageProps) {
  const queryClient = useQueryClient();
  const [taskDialogOpen, setTaskDialogOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<TaskItem | null>(null);
  const [createDraft, setCreateDraft] = useState<TaskDraft | null>(null);
  const [collapsedSubtasks, setCollapsedSubtasks] = useState<Record<number, boolean>>({});
  const dialogDraft = useMemo(
    () => createDraft ?? buildTaskDraft("all", todayString, tomorrowString),
    [createDraft, todayString, tomorrowString],
  );

  const openCreateTaskDialog = useCallback(
    (overrides?: TaskDraftOverrides) => {
      setEditingTask(null);
      setCreateDraft(buildTaskDraft("all", todayString, tomorrowString, undefined, overrides));
      setTaskDialogOpen(true);
    },
    [todayString, tomorrowString],
  );

  const openEditTaskDialog = useCallback((task: TaskItem) => {
    setCreateDraft(null);
    setEditingTask(task);
    setTaskDialogOpen(true);
  }, []);

  const closeTaskDialog = useCallback((open: boolean) => {
    setTaskDialogOpen(open);

    if (!open) {
      setEditingTask(null);
      setCreateDraft(null);
    }
  }, []);

  const handleTaskCreate = useCallback(
    async (payload: TaskCreatePayload) => {
      const createdTask = await createTask(payload);
      upsertTaskInCaches(queryClient, createdTask);

      const reorderScope = buildTopLevelTaskReorderScopeForTask({
        task: createdTask,
        mode: "all",
        todayString,
      });

      if (!reorderScope) {
        return;
      }

      const reorderedTaskIds = insertOrderedId(getTopLevelTaskIdsForReorderScope(tasks, reorderScope), createdTask.id, newTaskPlacement);
      if (reorderedTaskIds.length <= 1) {
        return;
      }

      try {
        const reorderedTasks = await reorderTopLevelTasks(reorderedTaskIds, reorderScope);
        upsertTasksInCaches(queryClient, reorderedTasks);
      } catch {
        await queryClient.invalidateQueries({ queryKey: ["tasks"] });
      }
    },
    [newTaskPlacement, queryClient, tasks, todayString],
  );

  const handleTaskUpdate = useCallback(
    async (task: TaskItem, payload: TaskUpdatePayload) => {
      const updatedTask = await updateTask(task.id, payload);
      upsertTaskInCaches(queryClient, updatedTask);

      if (editingTask?.id === updatedTask.id) {
        setEditingTask(updatedTask);
      }

      return updatedTask;
    },
    [editingTask?.id, queryClient],
  );

  const handleTaskToggle = useCallback(
    async (task: TaskItem) => {
      const updatedTask = await toggleTask(task.id);
      upsertTaskInCaches(queryClient, updatedTask);

      if (task.repeat !== "none" && !task.is_done && updatedTask.is_done) {
        await queryClient.invalidateQueries({ queryKey: ["tasks"] });
      }

      return updatedTask;
    },
    [queryClient],
  );

  const handleTaskDelete = useCallback(
    async (task: TaskItem) => {
      await deleteTask(task.id);
      removeTaskFromCaches(queryClient, task.id);
      setEditingTask(null);
    },
    [queryClient],
  );

  const handleCreateSubtask = useCallback(
    async (task: TaskItem, title: string) => {
      const createdSubtask = await createTask({
        title,
        description: null,
        description_blocks: [],
        due_date: null,
        reminder_time: null,
        repeat_until: null,
        is_done: false,
        is_pinned: false,
        priority: task.priority,
        repeat: "none",
        parent_id: task.id,
        list_id: task.list_id,
        subtasks: [],
      });

      upsertSubtaskInCaches(queryClient, task.id, createdSubtask);
      return createdSubtask;
    },
    [queryClient],
  );

  const handleUpdateSubtask = useCallback(
    async (subtask: TaskSubtask, payload: TaskUpdatePayload) => {
      const updatedSubtask = await updateTask(subtask.id, payload);

      if (subtask.parent_id !== null) {
        upsertSubtaskInCaches(queryClient, subtask.parent_id, updatedSubtask);
      }

      return updatedSubtask;
    },
    [queryClient],
  );

  const handleToggleSubtask = useCallback(
    async (subtask: TaskSubtask) => {
      const updatedSubtask = await toggleTask(subtask.id);

      if (subtask.parent_id !== null) {
        upsertSubtaskInCaches(queryClient, subtask.parent_id, updatedSubtask);

        if (subtask.repeat !== "none" && !subtask.is_done && updatedSubtask.is_done) {
          await queryClient.invalidateQueries({ queryKey: ["tasks"] });

          if (editingTask?.id === subtask.parent_id) {
            const refreshedParent = findTaskInTaskCaches(queryClient, subtask.parent_id);
            if (refreshedParent) {
              setEditingTask(refreshedParent);
            }
          }
        }
      }

      return updatedSubtask;
    },
    [editingTask?.id, queryClient],
  );

  const handleDeleteSubtask = useCallback(
    async (subtask: TaskSubtask) => {
      await deleteTask(subtask.id);

      if (subtask.parent_id !== null) {
        removeSubtaskFromCaches(queryClient, subtask.parent_id, subtask.id);
      }
    },
    [queryClient],
  );

  const handleReorderSubtasks = useCallback(
    async (task: TaskItem, subtaskIds: number[]) => {
      const updatedTask = await reorderSubtasks(task.id, subtaskIds);
      upsertTaskInCaches(queryClient, updatedTask);
      return updatedTask;
    },
    [queryClient],
  );

  const toggleTaskSubtasks = useCallback((taskId: number) => {
    setCollapsedSubtasks((current) => ({
      ...current,
      [taskId]: !current[taskId],
    }));
  }, []);

  const handleCreateTaskFromCalendar = useCallback(
    (dateString: string) => {
      openCreateTaskDialog({ due_date: dateString });
    },
    [openCreateTaskDialog],
  );

  if (tasksLoading) {
    return (
      <div className="flex h-full min-h-48 items-center justify-center rounded-[2rem] border border-border/70 bg-card/75 shadow-panel">
        <div className="flex items-center gap-3 text-sm text-muted-foreground">
          <Spinner />
          Loading calendar...
        </div>
      </div>
    );
  }

  if (tasksError) {
    return (
      <EmptyState
        title="Could not load the calendar"
        description="The calendar uses the existing task feed, and that request failed."
        action={<Button onClick={onRetry}>Retry</Button>}
      />
    );
  }

  return (
    <>
      <CalendarPageView
        tasks={tasks}
        lists={lists}
        showCompleted={showCompleted}
        todayString={todayString}
        onOpenTask={openEditTaskDialog}
        onCreateTask={handleCreateTaskFromCalendar}
      />

      <TaskDialog
        open={taskDialogOpen}
        task={editingTask}
        defaultDraft={dialogDraft}
        lists={lists}
        subtasksCollapsed={editingTask ? collapsedSubtasks[editingTask.id] ?? false : false}
        onOpenChange={closeTaskDialog}
        onCreateTask={handleTaskCreate}
        onUpdateTask={handleTaskUpdate}
        onToggleTask={handleTaskToggle}
        onDelete={handleTaskDelete}
        onCreateSubtask={handleCreateSubtask}
        onUpdateSubtask={handleUpdateSubtask}
        onToggleSubtask={handleToggleSubtask}
        onDeleteSubtask={handleDeleteSubtask}
        onReorderSubtasks={handleReorderSubtasks}
        onToggleSubtasks={toggleTaskSubtasks}
      />
    </>
  );
}
