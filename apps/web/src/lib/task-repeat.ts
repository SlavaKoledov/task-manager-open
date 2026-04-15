import { addDays, getLocalDateString, parseLocalDateString } from "@/lib/date";
import type { TaskCustomRepeatConfig, TaskCustomRepeatUnit, TaskRepeat } from "@/lib/types";

export const ISO_WEEKDAYS = [1, 2, 3, 4, 5, 6, 7] as const;
export const WEEKDAY_LETTERS = ["M", "T", "W", "T", "F", "S", "S"] as const;
export const WEEKDAY_NAMES = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
] as const;

const YEARLY_SUMMARY_FORMATTER = new Intl.DateTimeFormat("en-US", {
  month: "short",
  day: "numeric",
});

export function buildDefaultCustomRepeatConfig(
  unit: TaskCustomRepeatUnit,
  anchorDateString: string,
  interval = 1,
): TaskCustomRepeatConfig {
  const anchorDate = parseLocalDateString(anchorDateString) ?? parseLocalDateString(getLocalDateString()) ?? new Date();

  return {
    interval: Math.max(1, interval),
    unit,
    skip_weekends: false,
    weekdays: unit === "week" ? [getIsoWeekday(anchorDate)] : [],
    month_day: unit === "month" ? anchorDate.getDate() : null,
    month: unit === "year" ? anchorDate.getMonth() + 1 : null,
    day: unit === "year" ? anchorDate.getDate() : null,
  };
}

export function ensureCustomRepeatConfig(
  repeatConfig: TaskCustomRepeatConfig | null | undefined,
  anchorDateString: string,
): TaskCustomRepeatConfig {
  if (!repeatConfig) {
    return buildDefaultCustomRepeatConfig("day", anchorDateString);
  }

  const normalized = normalizeCustomRepeatConfig(repeatConfig);
  return normalized ?? buildDefaultCustomRepeatConfig("day", anchorDateString);
}

export function switchCustomRepeatUnit(
  repeatConfig: TaskCustomRepeatConfig | null | undefined,
  unit: TaskCustomRepeatUnit,
  anchorDateString: string,
): TaskCustomRepeatConfig {
  const current = ensureCustomRepeatConfig(repeatConfig, anchorDateString);
  const next = buildDefaultCustomRepeatConfig(unit, anchorDateString, current.interval);

  return {
    ...next,
    interval: current.interval,
    skip_weekends: unit === "day" || unit === "month" ? current.skip_weekends : false,
  };
}

export function normalizeCustomRepeatConfig(
  repeatConfig: TaskCustomRepeatConfig | null | undefined,
): TaskCustomRepeatConfig | null {
  if (!repeatConfig) {
    return null;
  }

  return {
    interval: Math.max(1, Number.isFinite(repeatConfig.interval) ? repeatConfig.interval : 1),
    unit: repeatConfig.unit,
    skip_weekends: Boolean(repeatConfig.skip_weekends),
    weekdays: Array.from(new Set(repeatConfig.weekdays ?? []))
      .filter((weekday) => weekday >= 1 && weekday <= 7)
      .sort((left, right) => left - right),
    month_day: repeatConfig.month_day && repeatConfig.month_day >= 1 && repeatConfig.month_day <= 31 ? repeatConfig.month_day : null,
    month: repeatConfig.month && repeatConfig.month >= 1 && repeatConfig.month <= 12 ? repeatConfig.month : null,
    day: repeatConfig.day && repeatConfig.day >= 1 && repeatConfig.day <= 31 ? repeatConfig.day : null,
  };
}

export function validateCustomRepeatConfig(repeatConfig: TaskCustomRepeatConfig | null | undefined): string | null {
  const normalized = normalizeCustomRepeatConfig(repeatConfig);
  if (!normalized) {
    return "Choose a custom repeat pattern.";
  }

  if (normalized.interval < 1) {
    return "Custom repeat interval must be at least 1.";
  }

  if (normalized.unit === "week" && normalized.weekdays.length === 0) {
    return "Choose at least one weekday for a custom weekly repeat.";
  }

  if (normalized.unit === "month" && normalized.month_day == null) {
    return "Choose a day of the month for a custom monthly repeat.";
  }

  if (normalized.unit === "year" && (normalized.month == null || normalized.day == null)) {
    return "Choose a month and date for a custom yearly repeat.";
  }

  if (
    normalized.unit === "year" &&
    normalized.month != null &&
    normalized.day != null &&
    normalized.day > getLastDayOfMonth(normalized.month === 2 ? 2024 : 2025, normalized.month - 1)
  ) {
    return "Choose a valid month and date for a custom yearly repeat.";
  }

  return null;
}

export function getTaskRepeatSummary(
  repeat: TaskRepeat,
  repeatConfig?: TaskCustomRepeatConfig | null,
): string {
  if (repeat === "none") {
    return "No repeat";
  }

  if (repeat === "daily") {
    return "Daily";
  }

  if (repeat === "weekly") {
    return "Weekly";
  }

  if (repeat === "monthly") {
    return "Monthly";
  }

  if (repeat === "yearly") {
    return "Yearly";
  }

  const normalized = normalizeCustomRepeatConfig(repeatConfig);
  if (!normalized) {
    return "Custom";
  }

  if (normalized.unit === "day") {
    return `Every ${formatInterval(normalized.interval, "day")}${normalized.skip_weekends ? ", skip weekends" : ""}`;
  }

  if (normalized.unit === "week") {
    return `Every ${formatInterval(normalized.interval, "week")} on ${normalized.weekdays.map(formatWeekdayLetter).join(", ")}`;
  }

  if (normalized.unit === "month") {
    return `Every ${formatInterval(normalized.interval, "month")} on ${normalized.month_day}${normalized.skip_weekends ? ", skip weekends" : ""}`;
  }

  const yearlyDate = resolveSafeDate(2025, normalized.month ?? 1, normalized.day ?? 1);
  return `Every ${formatInterval(normalized.interval, "year")} on ${YEARLY_SUMMARY_FORMATTER.format(yearlyDate)}`;
}

export function resolveNextRecurringDate(
  repeat: Exclude<TaskRepeat, "none">,
  currentDate: Date,
  repeatConfig?: TaskCustomRepeatConfig | null,
): Date {
  if (repeat === "daily") {
    return addDays(currentDate, 1);
  }

  if (repeat === "weekly") {
    return addDays(currentDate, 7);
  }

  if (repeat === "monthly") {
    return resolveNextMonthlyDate(currentDate, currentDate.getDate(), 1);
  }

  if (repeat === "yearly") {
    return resolveSafeDate(currentDate.getFullYear() + 1, currentDate.getMonth() + 1, currentDate.getDate());
  }

  const normalized = normalizeCustomRepeatConfig(repeatConfig) ?? buildDefaultCustomRepeatConfig("day", getLocalDateString(currentDate));

  if (normalized.unit === "day") {
    const nextDate = addDays(currentDate, normalized.interval);
    return normalized.skip_weekends ? shiftForwardPastWeekend(nextDate) : nextDate;
  }

  if (normalized.unit === "week") {
    const currentWeekday = getIsoWeekday(currentDate);
    const laterWeekdays = normalized.weekdays.filter((weekday) => weekday > currentWeekday);
    if (laterWeekdays.length > 0) {
      return addDays(currentDate, laterWeekdays[0] - currentWeekday);
    }

    const weekStart = addDays(currentDate, -(currentWeekday - 1));
    return addDays(weekStart, normalized.interval * 7 + (normalized.weekdays[0] - 1));
  }

  if (normalized.unit === "month") {
    const nextDate = resolveNextMonthlyDate(currentDate, normalized.month_day ?? currentDate.getDate(), normalized.interval);
    return normalized.skip_weekends ? shiftForwardPastWeekend(nextDate) : nextDate;
  }

  return resolveSafeDate(
    currentDate.getFullYear() + normalized.interval,
    normalized.month ?? currentDate.getMonth() + 1,
    normalized.day ?? currentDate.getDate(),
  );
}

export function formatWeekdayLetter(isoWeekday: number): string {
  return WEEKDAY_LETTERS[isoWeekday - 1] ?? "?";
}

export function getIsoWeekday(date: Date): number {
  return ((date.getDay() + 6) % 7) + 1;
}

export function resolveSafeDate(year: number, month: number, day: number): Date {
  return new Date(year, month - 1, Math.min(day, getLastDayOfMonth(year, month - 1)));
}

function formatInterval(interval: number, unit: "day" | "week" | "month" | "year"): string {
  return `${interval} ${unit}${interval === 1 ? "" : "s"}`;
}

function resolveNextMonthlyDate(currentDate: Date, dayOfMonth: number, intervalMonths: number): Date {
  const nextMonthIndex = currentDate.getMonth() + intervalMonths;
  const nextYear = currentDate.getFullYear() + Math.floor(nextMonthIndex / 12);
  const nextMonth = nextMonthIndex % 12;
  return resolveSafeDate(nextYear, nextMonth + 1, dayOfMonth);
}

function getLastDayOfMonth(year: number, monthIndex: number): number {
  return new Date(year, monthIndex + 1, 0).getDate();
}

function shiftForwardPastWeekend(date: Date): Date {
  if (date.getDay() === 6) {
    return addDays(date, 2);
  }
  if (date.getDay() === 0) {
    return addDays(date, 1);
  }
  return date;
}
