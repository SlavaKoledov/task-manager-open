export function getLocalDateString(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function addDays(date: Date, offsetDays: number): Date {
  const nextDate = new Date(date);
  nextDate.setDate(nextDate.getDate() + offsetDays);
  return nextDate;
}

export function getOffsetLocalDateString(offsetDays: number, date = new Date()): string {
  return getLocalDateString(addDays(date, offsetDays));
}

export function getTomorrowDateString(date = new Date()): string {
  return getOffsetLocalDateString(1, date);
}

export function parseLocalDateString(value: string): Date | null {
  const [year, month, day] = value.split("-").map(Number);
  if (!year || !month || !day) {
    return null;
  }
  return new Date(year, month - 1, day);
}

export function formatDueDateLabel(
  dueDate: string | null,
  todayString: string,
  tomorrowString = getTomorrowDateString(parseLocalDateString(todayString) ?? new Date()),
): string | null {
  if (!dueDate) {
    return null;
  }

  if (dueDate === todayString) {
    return "Today";
  }

  if (dueDate === tomorrowString) {
    return "Tomorrow";
  }

  const localDate = parseLocalDateString(dueDate);
  if (!localDate) {
    return dueDate;
  }

  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
  }).format(localDate);
}

export function getLocalTimezone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function getMillisecondsUntilNextLocalMidnight(date = new Date()): number {
  const nextMidnight = new Date(date);
  nextMidnight.setHours(24, 0, 0, 0);
  return Math.max(0, nextMidnight.getTime() - date.getTime());
}
