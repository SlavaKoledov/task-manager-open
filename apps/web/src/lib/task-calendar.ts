import { addDays, getLocalDateString, parseLocalDateString } from "@/lib/date";
import { resolveNextRecurringDate as resolveNextTaskRepeatDate } from "@/lib/task-repeat";
import { compareTaskStartTimes } from "@/lib/task-time";
import type { TaskItem, TaskRepeat } from "@/lib/types";

export type CalendarDay = {
  date: Date;
  dateString: string;
  isCurrentMonth: boolean;
};

export type CalendarRange = {
  start: Date;
  end: Date;
  startDateString: string;
  endDateString: string;
};

export type TaskOccurrence = {
  task: TaskItem;
  date: Date;
  dateString: string;
  isRecurring: boolean;
};

export const RECURRING_PREVIEW_LIMIT = 12;
const CALENDAR_MONTH_GRID_DAYS = 42;
const CALENDAR_WEEK_DAYS = 7;
const DAY_IN_MS = 24 * 60 * 60 * 1000;

export function getCalendarMonthStart(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

export function addCalendarMonths(date: Date, offsetMonths: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + offsetMonths, 1);
}

export function getCalendarWeekStart(date: Date): Date {
  const start = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const startOffset = (start.getDay() + 6) % 7;
  return addDays(start, -startOffset);
}

export function getCalendarMonthRange(monthDate: Date): CalendarRange {
  const monthStart = getCalendarMonthStart(monthDate);
  const start = getCalendarWeekStart(monthStart);
  const end = addDays(start, CALENDAR_MONTH_GRID_DAYS - 1);

  return {
    start,
    end,
    startDateString: getLocalDateString(start),
    endDateString: getLocalDateString(end),
  };
}

export function getCalendarWeekRange(date: Date): CalendarRange {
  const start = getCalendarWeekStart(date);
  const end = addDays(start, CALENDAR_WEEK_DAYS - 1);

  return {
    start,
    end,
    startDateString: getLocalDateString(start),
    endDateString: getLocalDateString(end),
  };
}

export function getCalendarDays(monthDate: Date): CalendarDay[] {
  const monthStart = getCalendarMonthStart(monthDate);
  const { start } = getCalendarMonthRange(monthDate);

  return Array.from({ length: CALENDAR_MONTH_GRID_DAYS }, (_, index) => {
    const date = addDays(start, index);

    return {
      date,
      dateString: getLocalDateString(date),
      isCurrentMonth: date.getMonth() === monthStart.getMonth(),
    };
  });
}

export function getCalendarWeekDays(date: Date): CalendarDay[] {
  const weekStart = getCalendarWeekStart(date);

  return Array.from({ length: CALENDAR_WEEK_DAYS }, (_, index) => {
    const day = addDays(weekStart, index);

    return {
      date: day,
      dateString: getLocalDateString(day),
      isCurrentMonth: day.getMonth() === date.getMonth(),
    };
  });
}

export function resolveNextRecurringPreviewDate(
  repeat: Exclude<TaskRepeat, "none">,
  currentDate: Date,
  repeatConfig?: TaskItem["repeat_config"],
): Date {
  return resolveNextTaskRepeatDate(repeat, currentDate, repeatConfig ?? null);
}

export function buildRecurringPreviewDates(
  dueDate: string,
  repeat: TaskRepeat,
  repeatConfig?: TaskItem["repeat_config"],
  repeatUntil?: string,
  limit = RECURRING_PREVIEW_LIMIT,
): string[] {
  if (!dueDate || repeat === "none" || limit <= 0) {
    return [];
  }

  const baseDate = parseLocalDateString(dueDate);
  const repeatUntilDate = repeatUntil ? parseLocalDateString(repeatUntil) : null;

  if (!baseDate) {
    return [];
  }

  const previewDates: string[] = [];
  let cursor = baseDate;

  for (let index = 0; index < limit; index += 1) {
    cursor = resolveNextRecurringPreviewDate(repeat, cursor, repeatConfig);

    if (repeatUntilDate && cursor > repeatUntilDate) {
      break;
    }

    previewDates.push(getLocalDateString(cursor));
  }

  return previewDates;
}

export function buildVisibleRecurringPreviewDateSet(
  previewDates: string[],
  calendarDays: CalendarDay[],
): Set<string> {
  const calendarDateStrings = new Set(calendarDays.map((day) => day.dateString));

  return new Set(previewDates.filter((dateString) => calendarDateStrings.has(dateString)));
}

function differenceInLocalDays(left: Date, right: Date): number {
  return Math.floor((left.getTime() - right.getTime()) / DAY_IN_MS);
}

function getFirstRecurringCandidate(
  baseDate: Date,
  repeat: Exclude<TaskRepeat, "none">,
  rangeStart: Date,
  repeatConfig?: TaskItem["repeat_config"],
): Date {
  const firstRecurringDate = resolveNextRecurringPreviewDate(repeat, baseDate, repeatConfig);

  if (firstRecurringDate >= rangeStart) {
    return firstRecurringDate;
  }

  if (repeat === "daily") {
    return addDays(firstRecurringDate, Math.max(0, differenceInLocalDays(rangeStart, firstRecurringDate)));
  }

  if (repeat === "weekly") {
    const diffDays = Math.max(0, differenceInLocalDays(rangeStart, firstRecurringDate));
    return addDays(firstRecurringDate, Math.ceil(diffDays / 7) * 7);
  }

  let cursor = firstRecurringDate;

  while (cursor < rangeStart) {
    cursor = resolveNextRecurringPreviewDate(repeat, cursor, repeatConfig);
  }

  return cursor;
}

export function buildTaskOccurrencesInRange(tasks: TaskItem[], range: CalendarRange): TaskOccurrence[] {
  const occurrences: TaskOccurrence[] = [];

  for (const task of tasks) {
    if (!task.due_date) {
      continue;
    }

    const baseDate = parseLocalDateString(task.due_date);
    if (!baseDate) {
      continue;
    }

    if (baseDate >= range.start && baseDate <= range.end) {
      occurrences.push({
        task,
        date: baseDate,
        dateString: task.due_date,
        isRecurring: false,
      });
    }

    if (task.repeat === "none" || task.is_done) {
      continue;
    }

    const repeatUntilDate = task.repeat_until ? parseLocalDateString(task.repeat_until) : null;
    let cursor = getFirstRecurringCandidate(baseDate, task.repeat, range.start, task.repeat_config);

    while (cursor <= range.end) {
      if (repeatUntilDate && cursor > repeatUntilDate) {
        break;
      }

      occurrences.push({
        task,
        date: cursor,
        dateString: getLocalDateString(cursor),
        isRecurring: true,
      });

      cursor = resolveNextRecurringPreviewDate(task.repeat, cursor, task.repeat_config);
    }
  }

  return occurrences.sort((left, right) => {
    const dateDelta = left.dateString.localeCompare(right.dateString);
    if (dateDelta !== 0) {
      return dateDelta;
    }

    return compareTaskOccurrences(left, right);
  });
}

export function groupTaskOccurrencesByDate(occurrences: TaskOccurrence[]): Map<string, TaskOccurrence[]> {
  const occurrencesByDate = new Map<string, TaskOccurrence[]>();

  for (const occurrence of occurrences) {
    const currentOccurrences = occurrencesByDate.get(occurrence.dateString);
    if (currentOccurrences) {
      currentOccurrences.push(occurrence);
    } else {
      occurrencesByDate.set(occurrence.dateString, [occurrence]);
    }
  }

  return occurrencesByDate;
}

export function compareTaskOccurrences(left: TaskOccurrence, right: TaskOccurrence): number {
  const timeDelta = compareTaskStartTimes(
    left.task.start_time,
    right.task.start_time,
    left.task.end_time,
    right.task.end_time,
  );
  if (timeDelta !== 0) {
    return timeDelta;
  }

  if (left.task.position !== right.task.position) {
    return left.task.position - right.task.position;
  }

  const createdAtDelta = left.task.created_at.localeCompare(right.task.created_at);
  if (createdAtDelta !== 0) {
    return createdAtDelta;
  }

  return left.task.id - right.task.id;
}
