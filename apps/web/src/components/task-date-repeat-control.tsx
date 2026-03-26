import { memo, useCallback, useMemo, useState } from "react";
import { Bell, CalendarDays, Check, ChevronLeft, ChevronRight, Repeat2, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  addDays,
  formatDueDateLabel,
  getLocalDateString,
  getTomorrowDateString,
  parseLocalDateString,
} from "@/lib/date";
import { getTaskRepeatOption, TASK_REPEAT_OPTIONS } from "@/lib/task-options";
import {
  addCalendarMonths,
  buildRecurringPreviewDates,
  buildVisibleRecurringPreviewDateSet,
  getCalendarDays,
  getCalendarMonthStart,
} from "@/lib/task-calendar";
import type { TaskRepeat } from "@/lib/types";
import { cn } from "@/lib/utils";

type TaskDateRepeatControlProps = {
  value: string;
  reminderTime: string;
  repeat: TaskRepeat;
  repeatUntil: string;
  onDateChange: (nextDate: string) => void;
  onReminderTimeChange: (nextTime: string) => void;
  onRepeatChange: (nextRepeat: TaskRepeat) => void;
  onRepeatUntilChange: (nextDate: string) => void;
  className?: string;
};

const WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const monthFormatter = new Intl.DateTimeFormat("en-US", {
  month: "long",
  year: "numeric",
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

function TaskDateRepeatControlInner({
  value,
  reminderTime,
  repeat,
  repeatUntil,
  onDateChange,
  onReminderTimeChange,
  onRepeatChange,
  onRepeatUntilChange,
  className,
}: TaskDateRepeatControlProps) {
  const [open, setOpen] = useState(false);
  const todayString = getLocalDateString();
  const tomorrowString = getTomorrowDateString();
  const [visibleMonth, setVisibleMonth] = useState(() => getInitialVisibleMonth(value, todayString));
  const [repeatUntilVisibleMonth, setRepeatUntilVisibleMonth] = useState(() =>
    getInitialVisibleMonth(repeatUntil || value, todayString),
  );

  const calendarDays = useMemo(() => getCalendarDays(visibleMonth), [visibleMonth]);
  const repeatUntilCalendarDays = useMemo(() => getCalendarDays(repeatUntilVisibleMonth), [repeatUntilVisibleMonth]);
  const recurringPreviewDates = useMemo(
    () => buildRecurringPreviewDates(value, repeat, repeatUntil),
    [repeat, repeatUntil, value],
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

  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (nextOpen) {
        setVisibleMonth(getInitialVisibleMonth(value, todayString));
        setRepeatUntilVisibleMonth(getInitialVisibleMonth(repeatUntil || value, todayString));
      }

      setOpen(nextOpen);
    },
    [repeatUntil, todayString, value],
  );

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
    onReminderTimeChange("");
    onRepeatChange("none");
    onRepeatUntilChange("");
    setVisibleMonth(getInitialVisibleMonth("", todayString));
    setRepeatUntilVisibleMonth(getInitialVisibleMonth("", todayString));
  }, [onDateChange, onReminderTimeChange, onRepeatChange, onRepeatUntilChange, todayString]);

  const dueDateObject = value ? parseLocalDateString(value) : null;

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
          {getTaskRepeatOption(repeat).label}
        </span>
      </button>

      {open ? (
        <Dialog
          open={open}
          title="Schedule"
          description="Choose the due date, reminder, and repeat settings in one place."
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
                      {repeat === "none" ? "One-time task" : getTaskRepeatOption(repeat).label}
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

                  <div className="grid gap-2">
                    {TASK_REPEAT_OPTIONS.map((option) => {
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
                          onClick={() => {
                            onRepeatChange(option.value);

                            if (option.value === "none") {
                              onRepeatUntilChange("");
                              return;
                            }

                            if (value && repeatUntil && repeatUntil < value) {
                              onRepeatUntilChange("");
                            }
                          }}
                        >
                          <span>{option.label}</span>
                          {repeat === option.value ? <Check className="h-4 w-4 text-primary" /> : null}
                        </button>
                      );
                    })}
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
