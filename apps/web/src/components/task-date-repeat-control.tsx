import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Bell, CalendarDays, Check, ChevronLeft, ChevronRight, Repeat2, X } from "lucide-react";

import { PopoverPanel } from "@/components/popover-panel";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Select } from "@/components/ui/select";
import {
  addDays,
  formatDueDateLabel,
  getLocalDateString,
  getTomorrowDateString,
  parseLocalDateString,
} from "@/lib/date";
import { TASK_REPEAT_OPTIONS } from "@/lib/task-options";
import {
  addCalendarMonths,
  buildRecurringPreviewDates,
  buildVisibleRecurringPreviewDateSet,
  getCalendarDays,
  getCalendarMonthStart,
} from "@/lib/task-calendar";
import {
  ensureCustomRepeatConfig,
  getTaskRepeatSummary,
  ISO_WEEKDAYS,
  resolveSafeDate,
  switchCustomRepeatUnit,
  WEEKDAY_LETTERS,
  WEEKDAY_NAMES,
} from "@/lib/task-repeat";
import { validateTaskTimeRange } from "@/lib/task-time";
import type { TaskCustomRepeatConfig, TaskCustomRepeatUnit, TaskRepeat } from "@/lib/types";
import { cn } from "@/lib/utils";

type TaskDateRepeatControlProps = {
  value: string;
  startTime: string;
  endTime: string;
  reminderTime: string;
  repeat: TaskRepeat;
  repeatConfig: TaskCustomRepeatConfig | null;
  repeatUntil: string;
  onDateChange: (nextDate: string) => void;
  onStartTimeChange: (nextTime: string) => void;
  onEndTimeChange: (nextTime: string) => void;
  onReminderTimeChange: (nextTime: string) => void;
  onRepeatChange: (nextRepeat: TaskRepeat) => void;
  onRepeatConfigChange: (nextRepeatConfig: TaskCustomRepeatConfig | null) => void;
  onRepeatUntilChange: (nextDate: string) => void;
  className?: string;
};

const WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const monthFormatter = new Intl.DateTimeFormat("en-US", {
  month: "long",
  year: "numeric",
});
const yearlyMonthFormatter = new Intl.DateTimeFormat("en-US", {
  month: "long",
});
const selectedDateFormatter = new Intl.DateTimeFormat("en-US", {
  weekday: "long",
  month: "long",
  day: "numeric",
});

function getInitialVisibleMonth(value: string, todayString: string): Date {
  return getCalendarMonthStart(parseLocalDateString(value) ?? parseLocalDateString(todayString) ?? new Date());
}

function renderCalendarGrid({
  calendarDays,
  selectedDateString,
  todayString,
  previewDates,
  onSelect,
  getDayDisabled,
  dayClassName = "h-10",
}: {
  calendarDays: ReturnType<typeof getCalendarDays>;
  selectedDateString: string;
  todayString: string;
  previewDates: Set<string>;
  onSelect: (dateString: string) => void;
  getDayDisabled?: (dateString: string) => boolean;
  dayClassName?: string;
}) {
  return (
    <>
      <div className="grid grid-cols-7 gap-1 text-center text-[11px] font-semibold uppercase tracking-[0.14em] text-muted-foreground">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="py-1">
            {label}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-7 gap-1">
        {calendarDays.map((day) => {
          const isSelected = selectedDateString === day.dateString;
          const isToday = todayString === day.dateString;
          const isPreviewDate = previewDates.has(day.dateString);
          const disabled = getDayDisabled?.(day.dateString) ?? false;

          return (
            <button
              key={day.dateString}
              type="button"
              aria-pressed={isSelected}
              disabled={disabled}
              className={cn(
                "relative flex items-center justify-center rounded-[0.95rem] text-sm font-medium transition-colors",
                dayClassName,
                day.isCurrentMonth
                  ? "text-foreground hover:bg-muted/65"
                  : "text-muted-foreground/55 hover:bg-muted/35",
                isPreviewDate && !isSelected && "bg-emerald-500/10 text-foreground ring-1 ring-emerald-500/35",
                isToday && !isSelected && "ring-1 ring-primary/40",
                isSelected && "bg-primary text-primary-foreground shadow-sm hover:bg-primary",
                disabled && "cursor-not-allowed opacity-35 hover:bg-transparent",
              )}
              onClick={() => onSelect(day.dateString)}
            >
              <span>{day.date.getDate()}</span>

              {isPreviewDate ? (
                <span className="pointer-events-none absolute inset-x-0 bottom-1 flex items-center justify-center gap-0.5">
                  <span
                    className={cn(
                      "h-1.5 w-4 rounded-full",
                      isSelected ? "bg-primary-foreground/75" : "bg-emerald-500/90",
                    )}
                  />
                </span>
              ) : null}
            </button>
          );
        })}
      </div>
    </>
  );
}

function getInitialCustomYearlyMonth(
  repeatConfig: TaskCustomRepeatConfig | null,
  dueDate: string,
  todayString: string,
): Date {
  if (repeatConfig?.unit === "year" && repeatConfig.month) {
    return new Date(2024, repeatConfig.month - 1, 1);
  }

  const anchorDate = parseLocalDateString(dueDate) ?? parseLocalDateString(todayString) ?? new Date(2024, 0, 1);
  return new Date(2024, anchorDate.getMonth(), 1);
}

function renderMonthDaySelector(
  selectedDay: number | null,
  onSelect: (day: number) => void,
) {
  return (
    <div className="grid grid-cols-7 gap-2">
      {Array.from({ length: 31 }, (_, index) => index + 1).map((day) => {
        const selected = selectedDay === day;
        return (
          <button
            key={day}
            type="button"
            aria-pressed={selected}
            className={cn(
              "flex h-9 items-center justify-center rounded-[0.95rem] border text-sm font-medium transition-colors",
              selected
                ? "border-primary/45 bg-primary text-primary-foreground"
                : "border-border/80 bg-card/70 text-muted-foreground hover:text-foreground",
            )}
            onClick={() => onSelect(day)}
          >
            {day}
          </button>
        );
      })}
    </div>
  );
}

function getSelectedYearlyDateString(repeatConfig: TaskCustomRepeatConfig | null): string {
  if (repeatConfig?.unit !== "year" || repeatConfig.month == null || repeatConfig.day == null) {
    return "";
  }

  return getLocalDateString(resolveSafeDate(2024, repeatConfig.month, repeatConfig.day));
}

function TaskDateRepeatControlInner({
  value,
  startTime,
  endTime,
  reminderTime,
  repeat,
  repeatConfig,
  repeatUntil,
  onDateChange,
  onStartTimeChange,
  onEndTimeChange,
  onReminderTimeChange,
  onRepeatChange,
  onRepeatConfigChange,
  onRepeatUntilChange,
  className,
}: TaskDateRepeatControlProps) {
  const [open, setOpen] = useState(false);
  const todayString = getLocalDateString();
  const tomorrowString = getTomorrowDateString();
  const customButtonRef = useRef<HTMLButtonElement | null>(null);
  const customPanelRef = useRef<HTMLDivElement | null>(null);
  const [visibleMonth, setVisibleMonth] = useState(() => getInitialVisibleMonth(value, todayString));
  const [repeatUntilVisibleMonth, setRepeatUntilVisibleMonth] = useState(() =>
    getInitialVisibleMonth(repeatUntil || value, todayString),
  );
  const [customPanelOpen, setCustomPanelOpen] = useState(false);
  const [customYearlyVisibleMonth, setCustomYearlyVisibleMonth] = useState(() =>
    getInitialCustomYearlyMonth(repeatConfig, value, todayString),
  );
  const [customIntervalInput, setCustomIntervalInput] = useState("1");
  const [isEditingCustomInterval, setIsEditingCustomInterval] = useState(false);

  const normalizedCustomRepeat = useMemo(
    () => (repeat === "custom" ? ensureCustomRepeatConfig(repeatConfig, value || todayString) : null),
    [repeat, repeatConfig, todayString, value],
  );
  const calendarDays = useMemo(() => getCalendarDays(visibleMonth), [visibleMonth]);
  const yearlyCalendarDays = useMemo(() => getCalendarDays(customYearlyVisibleMonth), [customYearlyVisibleMonth]);
  const repeatUntilCalendarDays = useMemo(() => getCalendarDays(repeatUntilVisibleMonth), [repeatUntilVisibleMonth]);
  const recurringPreviewDates = useMemo(
    () => buildRecurringPreviewDates(value, repeat, repeatConfig, repeatUntil),
    [repeat, repeatConfig, repeatUntil, value],
  );
  const visibleRecurringPreviewDates = useMemo(
    () => buildVisibleRecurringPreviewDateSet(recurringPreviewDates, calendarDays),
    [calendarDays, recurringPreviewDates],
  );
  const selectedDate = value ? parseLocalDateString(value) : null;
  const selectedRepeatUntilDate = repeatUntil ? parseLocalDateString(repeatUntil) : null;
  const selectedDateLabel =
    value && selectedDate ? selectedDateFormatter.format(selectedDate) : "No due date selected";
  const selectedRepeatUntilLabel =
    repeatUntil && selectedRepeatUntilDate ? selectedDateFormatter.format(selectedRepeatUntilDate) : "No end date";
  const repeatUntilEnabled = repeat !== "none" && Boolean(value);
  const repeatSummary = getTaskRepeatSummary(repeat, repeatConfig);
  const taskTimeError = validateTaskTimeRange(value, startTime, endTime);

  useEffect(() => {
    if (customPanelOpen && isEditingCustomInterval) {
      return;
    }

    setCustomIntervalInput(String(normalizedCustomRepeat?.interval ?? 1));
  }, [customPanelOpen, isEditingCustomInterval, normalizedCustomRepeat?.interval]);

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (nextOpen) {
        setVisibleMonth(getInitialVisibleMonth(value, todayString));
        setRepeatUntilVisibleMonth(getInitialVisibleMonth(repeatUntil || value, todayString));
        setCustomYearlyVisibleMonth(getInitialCustomYearlyMonth(repeatConfig, value, todayString));
      }

      if (!nextOpen) {
        setCustomPanelOpen(false);
        setIsEditingCustomInterval(false);
      }
      setOpen(nextOpen);
    },
    [repeatConfig, repeatUntil, todayString, value],
  );

  useEffect(() => {
    if (!customPanelOpen) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) {
        return;
      }

      if (customButtonRef.current?.contains(target) || customPanelRef.current?.contains(target)) {
        return;
      }

      setCustomPanelOpen(false);
    };

    window.addEventListener("mousedown", handlePointerDown);
    return () => window.removeEventListener("mousedown", handlePointerDown);
  }, [customPanelOpen]);

  const handleQuickDate = useCallback(
    (nextDate: string) => {
      onDateChange(nextDate);
      if (repeatUntil && repeatUntil < nextDate) {
        onRepeatUntilChange("");
      }
      setVisibleMonth(getInitialVisibleMonth(nextDate, todayString));
    },
    [onDateChange, onRepeatUntilChange, repeatUntil, todayString],
  );

  const handleClearDate = useCallback(() => {
    onDateChange("");
    onStartTimeChange("");
    onEndTimeChange("");
    onReminderTimeChange("");
    onRepeatChange("none");
    onRepeatUntilChange("");
    setCustomPanelOpen(false);
    setVisibleMonth(getInitialVisibleMonth("", todayString));
    setRepeatUntilVisibleMonth(getInitialVisibleMonth("", todayString));
  }, [onDateChange, onEndTimeChange, onReminderTimeChange, onRepeatChange, onRepeatUntilChange, onStartTimeChange, todayString]);

  const dueDateObject = value ? parseLocalDateString(value) : null;
  const customAnchorDate = value || todayString;

  const handleSimpleRepeatSelect = useCallback(
    (nextRepeat: Extract<TaskRepeat, "none" | "daily" | "weekly" | "monthly" | "yearly">) => {
      onRepeatChange(nextRepeat);
      if (nextRepeat === "none") {
        onRepeatUntilChange("");
      } else if (value && repeatUntil && repeatUntil < value) {
        onRepeatUntilChange("");
      }
      setCustomPanelOpen(false);
    },
    [onRepeatChange, onRepeatUntilChange, repeatUntil, value],
  );

  const handleCustomRepeatOpen = useCallback(() => {
    const nextConfig = ensureCustomRepeatConfig(repeatConfig, customAnchorDate);
    onRepeatChange("custom");
    onRepeatConfigChange(nextConfig);
    if (value && repeatUntil && repeatUntil < value) {
      onRepeatUntilChange("");
    }
    setCustomYearlyVisibleMonth(getInitialCustomYearlyMonth(nextConfig, value, todayString));
    setCustomIntervalInput(String(nextConfig.interval));
    setIsEditingCustomInterval(false);
    setCustomPanelOpen(true);
  }, [customAnchorDate, onRepeatChange, onRepeatConfigChange, onRepeatUntilChange, repeatConfig, repeatUntil, todayString, value]);

  const handleCustomIntervalChange = useCallback(
    (nextValue: string) => {
      if (!normalizedCustomRepeat) {
        return;
      }

      setCustomIntervalInput(nextValue);
      if (!nextValue.trim()) {
        onRepeatConfigChange({
          ...normalizedCustomRepeat,
          interval: 1,
        });
        return;
      }

      const parsed = Number.parseInt(nextValue, 10);
      if (Number.isNaN(parsed) || parsed < 1) {
        return;
      }

      onRepeatConfigChange({
        ...normalizedCustomRepeat,
        interval: parsed,
      });
    },
    [normalizedCustomRepeat, onRepeatConfigChange],
  );

  const handleCustomUnitChange = useCallback(
    (nextUnit: TaskCustomRepeatUnit) => {
      const nextConfig = switchCustomRepeatUnit(normalizedCustomRepeat, nextUnit, customAnchorDate);
      onRepeatConfigChange(nextConfig);
      if (nextUnit === "year") {
        setCustomYearlyVisibleMonth(getInitialCustomYearlyMonth(nextConfig, value, todayString));
      }
    },
    [customAnchorDate, normalizedCustomRepeat, onRepeatConfigChange, todayString, value],
  );

  const handleCustomSkipWeekendsChange = useCallback(
    (nextChecked: boolean) => {
      if (!normalizedCustomRepeat) {
        return;
      }

      onRepeatConfigChange({
        ...normalizedCustomRepeat,
        skip_weekends: nextChecked,
      });
    },
    [normalizedCustomRepeat, onRepeatConfigChange],
  );

  const handleCustomWeekdayToggle = useCallback(
    (weekday: number) => {
      if (!normalizedCustomRepeat || normalizedCustomRepeat.unit !== "week") {
        return;
      }

      const nextWeekdays = normalizedCustomRepeat.weekdays.includes(weekday)
        ? normalizedCustomRepeat.weekdays.filter((currentWeekday) => currentWeekday !== weekday)
        : [...normalizedCustomRepeat.weekdays, weekday].sort((left, right) => left - right);

      onRepeatConfigChange({
        ...normalizedCustomRepeat,
        weekdays: nextWeekdays,
      });
    },
    [normalizedCustomRepeat, onRepeatConfigChange],
  );

  const handleCustomMonthDaySelect = useCallback(
    (day: number) => {
      if (!normalizedCustomRepeat) {
        return;
      }

      onRepeatConfigChange({
        ...normalizedCustomRepeat,
        month_day: day,
      });
    },
    [normalizedCustomRepeat, onRepeatConfigChange],
  );

  const handleCustomYearlyDateSelect = useCallback(
    (dateString: string) => {
      const selectedDate = parseLocalDateString(dateString);
      if (!normalizedCustomRepeat || !selectedDate) {
        return;
      }

      onRepeatConfigChange({
        ...normalizedCustomRepeat,
        month: selectedDate.getMonth() + 1,
        day: selectedDate.getDate(),
      });
      setCustomYearlyVisibleMonth(new Date(2024, selectedDate.getMonth(), 1));
    },
    [normalizedCustomRepeat, onRepeatConfigChange],
  );

  return (
    <div className={cn("relative", className)}>
      <button
        type="button"
        className="inline-flex items-center gap-2 rounded-full border border-border/80 bg-card/80 px-3 py-2 text-sm text-foreground shadow-sm transition-colors duration-100 hover:bg-muted/60"
        onClick={() => handleOpenChange(true)}
      >
        <CalendarDays className="h-4 w-4 text-primary" />
        <span>{value ? formatDueDateLabel(value, todayString, tomorrowString) : "No date"}</span>
        <span className="rounded-full bg-muted/60 px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
          {repeatSummary}
        </span>
      </button>

      {open ? (
        <Dialog
          open={open}
          title="Schedule"
          description="Choose the due date, task time, reminder, and repeat settings in one place."
          onOpenChange={handleOpenChange}
          contentClassName="h-[min(94dvh,58rem)] w-full max-w-[min(76rem,calc(100vw-2rem))] rounded-[1.8rem] p-0"
          headerClassName="border-b border-border/70 px-6 py-4"
          titleClassName="text-[1.35rem]"
          descriptionClassName="text-sm"
        >
          <div className="hover-scrollbar min-h-0 flex-1 overflow-y-auto px-6 pb-6 pt-1">
            <div className="space-y-4">
              <div className="rounded-[1.1rem] border border-border/70 bg-muted/25 px-4 py-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                      Due date
                    </p>
                    <p className="mt-1 text-sm font-semibold text-foreground">{selectedDateLabel}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {repeat === "none" ? "One-time task" : repeatSummary}
                    </p>
                  </div>

                  {value ? (
                    <button
                      type="button"
                      className="inline-flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-background/80 hover:text-foreground"
                      aria-label="Clear date"
                      onClick={handleClearDate}
                    >
                      <X className="h-4 w-4" />
                    </button>
                  ) : null}
                </div>
              </div>

              <div className="grid gap-4 lg:grid-cols-[minmax(0,1.25fr)_minmax(0,0.95fr)]">
                <div className="space-y-3 rounded-[1.1rem] border border-border/70 bg-card/75 px-4 py-4">
                  <div className="flex items-center justify-between gap-3">
                    <button
                      type="button"
                      className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border/70 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                      aria-label="Previous month"
                      onClick={() => setVisibleMonth((current) => addCalendarMonths(current, -1))}
                    >
                      <ChevronLeft className="h-4 w-4" />
                    </button>

                    <p className="text-sm font-semibold text-foreground">{monthFormatter.format(visibleMonth)}</p>

                    <button
                      type="button"
                      className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border/70 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                      aria-label="Next month"
                      onClick={() => setVisibleMonth((current) => addCalendarMonths(current, 1))}
                    >
                      <ChevronRight className="h-4 w-4" />
                    </button>
                  </div>

                  {renderCalendarGrid({
                    calendarDays,
                    selectedDateString: value,
                    todayString,
                    previewDates: visibleRecurringPreviewDates,
                    onSelect: (nextDateString) => {
                      onDateChange(nextDateString);
                      if (repeatUntil && repeatUntil < nextDateString) {
                        onRepeatUntilChange("");
                      }
                      setVisibleMonth(getInitialVisibleMonth(nextDateString, todayString));
                    },
                  })}

                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" onClick={() => handleQuickDate(todayString)}>
                      Today
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => handleQuickDate(tomorrowString)}>
                      Tomorrow
                    </Button>
                    <Button variant="ghost" size="sm" onClick={handleClearDate}>
                      Clear date
                    </Button>
                  </div>

                  {recurringPreviewDates.length > 0 ? (
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <span className="inline-flex items-center gap-1.5">
                        <span className="h-2 w-2 rounded-full bg-emerald-500" />
                        Repeat preview
                      </span>
                      <span>Today is outlined.</span>
                    </div>
                  ) : (
                    <div className="text-xs text-muted-foreground">Today is outlined.</div>
                  )}
                </div>

                <div className="space-y-4">
                  <div className="space-y-3 rounded-[1.1rem] border border-border/70 bg-card/75 px-4 py-4">
                    <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                      <CalendarDays className="h-3.5 w-3.5" />
                      <span>Time</span>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                      <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground" htmlFor="task-start-time">
                          Start time
                        </label>
                        <Input
                          id="task-start-time"
                          type="time"
                          value={startTime}
                          disabled={!value}
                          onChange={(event) => {
                            const nextStartTime = event.target.value;
                            onStartTimeChange(nextStartTime);
                            if (!nextStartTime) {
                              onEndTimeChange("");
                            }
                          }}
                        />
                      </div>

                      <div className="space-y-2">
                        <label className="text-xs font-medium text-muted-foreground" htmlFor="task-end-time">
                          End time
                        </label>
                        <Input
                          id="task-end-time"
                          type="time"
                          value={endTime}
                          disabled={!value || !startTime}
                          onChange={(event) => onEndTimeChange(event.target.value)}
                        />
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={!startTime}
                        onClick={() => {
                          onStartTimeChange("");
                          onEndTimeChange("");
                        }}
                      >
                        Clear start
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        disabled={!endTime}
                        onClick={() => onEndTimeChange("")}
                      >
                        Clear end
                      </Button>
                    </div>

                    <p className={cn("text-xs", taskTimeError ? "text-rose-600 dark:text-rose-300" : "text-muted-foreground")}>
                      {!value
                        ? "Pick a due date before setting task time."
                        : taskTimeError ?? (startTime
                          ? "Add an optional end time or leave the task open-ended."
                          : "Add an optional start time for scheduled tasks.")}
                    </p>
                  </div>

                  <div className="space-y-2 rounded-[1.1rem] border border-border/70 bg-card/75 px-4 py-4">
                    <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                      <Bell className="h-3.5 w-3.5" />
                      <span>Reminder</span>
                    </div>

                    <div className="space-y-2">
                      <Input
                        type="time"
                        value={reminderTime}
                        disabled={!value}
                        onChange={(event) => onReminderTimeChange(event.target.value)}
                      />
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!value}
                          onClick={() => onReminderTimeChange("09:00")}
                        >
                          09:00
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={!value}
                          onClick={() => onReminderTimeChange("16:00")}
                        >
                          16:00
                        </Button>
                        <Button variant="ghost" size="sm" disabled={!value || !reminderTime} onClick={() => onReminderTimeChange("")}>
                          No reminder
                        </Button>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {!value
                          ? "Pick a due date before setting a reminder."
                          : "Reminder time is stored with the task and reused by supported clients."}
                      </p>
                    </div>
                  </div>

                <div className="space-y-2 rounded-[1.1rem] border border-border/70 bg-card/75 px-4 py-4">
                  <div className="flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                    <Repeat2 className="h-3.5 w-3.5" />
                    <span>Repeat</span>
                  </div>

                  <div className="space-y-2">
                    <div className="grid gap-2 sm:grid-cols-2">
                      {TASK_REPEAT_OPTIONS.filter((option) => option.value !== "custom").map((option) => {
                        const disabled = !value && option.value !== "none";

                        return (
                          <button
                            key={option.value}
                            type="button"
                            className={cn(
                              "flex items-center justify-between rounded-[1rem] border px-3 py-2 text-left text-sm transition-colors",
                              repeat === option.value
                                ? "border-primary/45 bg-primary/10 text-foreground"
                                : "border-border/80 bg-card/70 text-muted-foreground hover:text-foreground",
                              disabled && "cursor-not-allowed opacity-45",
                            )}
                            disabled={disabled}
                            onClick={() => handleSimpleRepeatSelect(option.value as Extract<TaskRepeat, "none" | "daily" | "weekly" | "monthly" | "yearly">)}
                          >
                            <span>{option.label}</span>
                            {repeat === option.value ? <Check className="h-4 w-4 text-primary" /> : null}
                          </button>
                        );
                      })}
                    </div>

                    <div className="relative">
                      <button
                        ref={customButtonRef}
                        type="button"
                        className={cn(
                          "flex w-full items-center justify-between rounded-[1rem] border px-3 py-2 text-left text-sm transition-colors",
                          repeat === "custom"
                            ? "border-primary/45 bg-primary/10 text-foreground"
                            : "border-border/80 bg-card/70 text-muted-foreground hover:text-foreground",
                          !value && "cursor-not-allowed opacity-45",
                        )}
                        disabled={!value}
                        aria-expanded={customPanelOpen}
                        aria-haspopup="dialog"
                        onClick={handleCustomRepeatOpen}
                      >
                        <span className="flex min-w-0 flex-col">
                          <span>Custom</span>
                          {repeat === "custom" ? (
                            <span className="truncate text-xs text-muted-foreground">{repeatSummary}</span>
                          ) : null}
                        </span>
                        {repeat === "custom" ? <Check className="h-4 w-4 text-primary" /> : null}
                      </button>

                      {customPanelOpen ? (
                        <PopoverPanel
                          anchorRef={customButtonRef}
                          panelRef={customPanelRef}
                          className="w-[min(26rem,calc(100vw-2rem))] space-y-4 p-4"
                        >
                          <div className="grid gap-3 sm:grid-cols-2">
                            <div className="rounded-[1rem] border border-border/80 bg-muted/20 px-3 py-3">
                              <label className="flex items-center gap-3 text-sm font-medium text-foreground">
                                <span>Every</span>
                                <Input
                                  type="number"
                                  min={1}
                                  value={customIntervalInput}
                                  className="h-10 rounded-xl px-3 py-2"
                                  onFocus={() => setIsEditingCustomInterval(true)}
                                  onBlur={() => {
                                    setIsEditingCustomInterval(false);
                                    if (!customIntervalInput.trim()) {
                                      setCustomIntervalInput(String(normalizedCustomRepeat?.interval ?? 1));
                                    }
                                  }}
                                  onChange={(event) => handleCustomIntervalChange(event.target.value)}
                                />
                              </label>
                            </div>

                            <div className="rounded-[1rem] border border-border/80 bg-muted/20 px-3 py-3">
                              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                                <span>Unit</span>
                                <Select
                                  value={normalizedCustomRepeat?.unit ?? "day"}
                                  onChange={(event) => handleCustomUnitChange(event.target.value as TaskCustomRepeatUnit)}
                                >
                                  <option value="day">day</option>
                                  <option value="week">week</option>
                                  <option value="month">month</option>
                                  <option value="year">year</option>
                                </Select>
                              </label>
                            </div>
                          </div>

                          {normalizedCustomRepeat?.unit === "day" ? (
                            <label className="flex items-center gap-3 rounded-[1rem] border border-border/80 bg-muted/20 px-3 py-3 text-sm text-foreground">
                              <input
                                type="checkbox"
                                checked={normalizedCustomRepeat.skip_weekends}
                                onChange={(event) => handleCustomSkipWeekendsChange(event.target.checked)}
                              />
                              <span>Skip weekends</span>
                            </label>
                          ) : null}

                          {normalizedCustomRepeat?.unit === "week" ? (
                            <div className="space-y-2 rounded-[1rem] border border-border/80 bg-muted/20 px-3 py-3">
                              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                                Repeat on
                              </p>
                              <div className="grid grid-cols-7 gap-2">
                                {ISO_WEEKDAYS.map((weekday) => {
                                  const selected = normalizedCustomRepeat.weekdays.includes(weekday);
                                  return (
                                    <button
                                      key={weekday}
                                      type="button"
                                      aria-label={WEEKDAY_NAMES[weekday - 1]}
                                      aria-pressed={selected}
                                      className={cn(
                                        "flex h-10 items-center justify-center rounded-[0.95rem] border text-sm font-semibold transition-colors",
                                        selected
                                          ? "border-primary/45 bg-primary text-primary-foreground"
                                          : "border-border/80 bg-card/70 text-muted-foreground hover:text-foreground",
                                      )}
                                      onClick={() => handleCustomWeekdayToggle(weekday)}
                                    >
                                      {WEEKDAY_LETTERS[weekday - 1]}
                                    </button>
                                  );
                                })}
                              </div>
                              <p className="text-xs text-muted-foreground">Choose one or more weekdays.</p>
                            </div>
                          ) : null}

                          {normalizedCustomRepeat?.unit === "month" ? (
                            <div className="space-y-3 rounded-[1rem] border border-border/80 bg-muted/20 px-3 py-3">
                              <div>
                                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                                  Day of month
                                </p>
                                <div className="mt-2">
                                  {renderMonthDaySelector(
                                    normalizedCustomRepeat.month_day,
                                    handleCustomMonthDaySelect,
                                  )}
                                </div>
                              </div>
                              <label className="flex items-center gap-3 text-sm text-foreground">
                                <input
                                  type="checkbox"
                                  checked={normalizedCustomRepeat.skip_weekends}
                                  onChange={(event) => handleCustomSkipWeekendsChange(event.target.checked)}
                                />
                                <span>Skip weekends</span>
                              </label>
                            </div>
                          ) : null}

                          {normalizedCustomRepeat?.unit === "year" ? (
                            <div className="space-y-3 rounded-[1rem] border border-border/80 bg-muted/20 px-3 py-3">
                              <div className="flex items-center justify-between gap-3">
                                <p className="text-sm font-semibold text-foreground">
                                  {yearlyMonthFormatter.format(customYearlyVisibleMonth)}
                                </p>
                                <div className="flex items-center gap-2">
                                  <button
                                    type="button"
                                    className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border/70 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                                    aria-label="Previous custom repeat month"
                                    onClick={() => setCustomYearlyVisibleMonth((current) => addCalendarMonths(current, -1))}
                                  >
                                    <ChevronLeft className="h-4 w-4" />
                                  </button>
                                  <button
                                    type="button"
                                    className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border/70 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                                    aria-label="Next custom repeat month"
                                    onClick={() => setCustomYearlyVisibleMonth((current) => addCalendarMonths(current, 1))}
                                  >
                                    <ChevronRight className="h-4 w-4" />
                                  </button>
                                </div>
                              </div>
                              {renderCalendarGrid({
                                calendarDays: yearlyCalendarDays,
                                selectedDateString: getSelectedYearlyDateString(normalizedCustomRepeat),
                                todayString: "",
                                previewDates: new Set<string>(),
                                dayClassName: "h-9",
                                onSelect: handleCustomYearlyDateSelect,
                              })}
                            </div>
                          ) : null}
                        </PopoverPanel>
                      ) : null}
                    </div>

                    <p className="text-xs text-muted-foreground">
                      {!value ? "Pick a due date before enabling repeat." : "Custom repeat keeps the same series semantics across clients."}
                    </p>
                  </div>
                </div>

                {repeatUntilEnabled ? (
                  <div className="space-y-3 rounded-[1.1rem] border border-border/70 bg-card/75 px-4 py-4">
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-muted-foreground">
                          Recurring until
                        </p>
                        <p className="mt-1 text-sm font-semibold text-foreground">{selectedRepeatUntilLabel}</p>
                        <p className="mt-1 text-xs text-muted-foreground">
                          Future tasks stop once the next due date would exceed this date.
                        </p>
                      </div>

                      {repeatUntil ? (
                        <button
                          type="button"
                          className="inline-flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-background/80 hover:text-foreground"
                          aria-label="Clear repeat end date"
                          onClick={() => onRepeatUntilChange("")}
                        >
                          <X className="h-4 w-4" />
                        </button>
                      ) : null}
                    </div>

                    <div className="flex items-center justify-between gap-3">
                      <button
                        type="button"
                        className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border/70 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                        aria-label="Previous repeat end month"
                        onClick={() => setRepeatUntilVisibleMonth((current) => addCalendarMonths(current, -1))}
                      >
                        <ChevronLeft className="h-4 w-4" />
                      </button>

                      <p className="text-sm font-semibold text-foreground">{monthFormatter.format(repeatUntilVisibleMonth)}</p>

                      <button
                        type="button"
                        className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-border/70 text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground"
                        aria-label="Next repeat end month"
                        onClick={() => setRepeatUntilVisibleMonth((current) => addCalendarMonths(current, 1))}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </button>
                    </div>

                    {renderCalendarGrid({
                      calendarDays: repeatUntilCalendarDays,
                      selectedDateString: repeatUntil,
                      todayString,
                      previewDates: new Set<string>(),
                      dayClassName: "h-8",
                      getDayDisabled: (dateString) => Boolean(value && dateString < value),
                      onSelect: (nextDateString) => {
                        onRepeatUntilChange(nextDateString);
                        setRepeatUntilVisibleMonth(getInitialVisibleMonth(nextDateString, todayString));
                      },
                    })}

                    <div className="flex flex-wrap gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!dueDateObject}
                        onClick={() => dueDateObject && onRepeatUntilChange(getLocalDateString(dueDateObject))}
                      >
                        Same day
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!dueDateObject}
                        onClick={() =>
                          dueDateObject && onRepeatUntilChange(getLocalDateString(addDays(dueDateObject, 30)))
                        }
                      >
                        +30 days
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={!dueDateObject}
                        onClick={() =>
                          dueDateObject && onRepeatUntilChange(getLocalDateString(addDays(dueDateObject, 90)))
                        }
                      >
                        +90 days
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => onRepeatUntilChange("")}>
                        No end date
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-[1.1rem] border border-dashed border-border/70 bg-muted/15 px-4 py-4 text-sm text-muted-foreground">
                    Pick a due date and enable repeat to set an end date for the recurring series.
                  </div>
                )}
                </div>
              </div>
            </div>
          </div>
        </Dialog>
      ) : null}
    </div>
  );
}

export const TaskDateRepeatControl = memo(TaskDateRepeatControlInner);
