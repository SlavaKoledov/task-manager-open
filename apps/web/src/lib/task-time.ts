import type { TaskItem } from "@/lib/types";

const TASK_TIME_PATTERN = /^(\d{2}):(\d{2})$/;
const TASK_TIME_INPUT_PATTERN = /^(\d{1,2}):(\d{2})$/;
const TASK_TIME_COMPACT_INPUT_PATTERN = /^(\d{3,4})$/;
const TASK_TIME_12_HOUR_INPUT_PATTERN = /^(\d{1,2}):(\d{2})\s*([AaPp][Mm])$/;

export function normalizeTaskTime(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }

  const cleaned = value.trim();
  if (!cleaned) {
    return null;
  }

  const match = TASK_TIME_PATTERN.exec(cleaned);
  if (!match) {
    return null;
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (hours > 23 || minutes > 59) {
    return null;
  }

  return cleaned;
}

export function sanitizeTaskTimeInput(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) {
    return "";
  }

  if (trimmed.includes(":")) {
    const [rawHours, rawMinutes = ""] = trimmed.split(":", 2);
    const hours = rawHours.replace(/\D/g, "").slice(0, 2);
    const minutes = rawMinutes.replace(/\D/g, "").slice(0, 2);

    if (!hours) {
      return "";
    }

    return rawMinutes.length > 0 || trimmed.endsWith(":") ? `${hours}:${minutes}` : hours;
  }

  const digits = trimmed.replace(/\D/g, "").slice(0, 4);
  if (digits.length <= 2) {
    return digits;
  }

  if (digits.length === 3) {
    return `${digits.slice(0, 1)}:${digits.slice(1)}`;
  }

  return `${digits.slice(0, 2)}:${digits.slice(2)}`;
}

export function normalizeTaskTimeInput(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }

  const cleaned = value.trim();
  if (!cleaned) {
    return null;
  }

  const twelveHourMatch = TASK_TIME_12_HOUR_INPUT_PATTERN.exec(cleaned);
  if (twelveHourMatch) {
    const hours = Number(twelveHourMatch[1]);
    const minutes = Number(twelveHourMatch[2]);
    const meridiem = twelveHourMatch[3].toLowerCase();

    if (hours < 1 || hours > 12 || minutes > 59) {
      return null;
    }

    const normalizedHours = meridiem === "pm" ? (hours % 12) + 12 : hours % 12;
    return `${String(normalizedHours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
  }

  const colonMatch = TASK_TIME_INPUT_PATTERN.exec(cleaned);
  if (colonMatch) {
    const hours = Number(colonMatch[1]);
    const minutes = Number(colonMatch[2]);
    if (hours > 23 || minutes > 59) {
      return null;
    }

    return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
  }

  const compactMatch = TASK_TIME_COMPACT_INPUT_PATTERN.exec(cleaned);
  if (compactMatch) {
    const digits = compactMatch[1];
    const hours = Number(digits.length === 3 ? digits.slice(0, 1) : digits.slice(0, 2));
    const minutes = Number(digits.slice(-2));
    if (hours > 23 || minutes > 59) {
      return null;
    }

    return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
  }

  return null;
}

export function parseTaskTimeToMinutes(value: string | null | undefined): number | null {
  const normalized = normalizeTaskTime(value);
  if (!normalized) {
    return null;
  }

  const [hours, minutes] = normalized.split(":").map(Number);
  return hours * 60 + minutes;
}

export function validateTaskTimeRange(
  dueDate: string | null | undefined,
  startTime: string | null | undefined,
  endTime: string | null | undefined,
): string | null {
  if (!dueDate) {
    return null;
  }

  const normalizedStart = normalizeTaskTime(startTime);
  const normalizedEnd = normalizeTaskTime(endTime);

  if (!normalizedStart && normalizedEnd) {
    return "Choose a start time before setting an end time.";
  }

  if (normalizedStart && normalizedEnd) {
    const startMinutes = parseTaskTimeToMinutes(normalizedStart);
    const endMinutes = parseTaskTimeToMinutes(normalizedEnd);
    if (startMinutes !== null && endMinutes !== null && endMinutes <= startMinutes) {
      return "End time must be later than the start time.";
    }
  }

  return null;
}

export function formatTaskTimeRange(
  startTime: string | null | undefined,
  endTime: string | null | undefined,
): string | null {
  const normalizedStart = normalizeTaskTime(startTime);
  if (!normalizedStart) {
    return null;
  }

  const normalizedEnd = normalizeTaskTime(endTime);
  return normalizedEnd ? `${normalizedStart}–${normalizedEnd}` : normalizedStart;
}

export function compareTaskStartTimes(
  leftStartTime: string | null | undefined,
  rightStartTime: string | null | undefined,
  leftEndTime: string | null | undefined = null,
  rightEndTime: string | null | undefined = null,
): number {
  const leftStartMinutes = parseTaskTimeToMinutes(leftStartTime);
  const rightStartMinutes = parseTaskTimeToMinutes(rightStartTime);

  if (leftStartMinutes !== null && rightStartMinutes === null) {
    return -1;
  }

  if (leftStartMinutes === null && rightStartMinutes !== null) {
    return 1;
  }

  if (leftStartMinutes !== null && rightStartMinutes !== null && leftStartMinutes !== rightStartMinutes) {
    return leftStartMinutes - rightStartMinutes;
  }

  const leftEndMinutes = parseTaskTimeToMinutes(leftEndTime);
  const rightEndMinutes = parseTaskTimeToMinutes(rightEndTime);
  if (leftEndMinutes !== null && rightEndMinutes !== null && leftEndMinutes !== rightEndMinutes) {
    return leftEndMinutes - rightEndMinutes;
  }

  return 0;
}

type TimeOrderedTask = Pick<TaskItem, "start_time" | "end_time" | "position" | "created_at" | "id">;
type ScheduledTask = TimeOrderedTask & Pick<TaskItem, "due_date">;

export function compareTaskItemsByTime<T extends ScheduledTask>(left: T, right: T): number {
  if (left.due_date !== null && right.due_date === null) {
    return -1;
  }

  if (left.due_date === null && right.due_date !== null) {
    return 1;
  }

  if (left.due_date !== null && right.due_date !== null) {
    const dueDateDelta = left.due_date.localeCompare(right.due_date);
    if (dueDateDelta !== 0) {
      return dueDateDelta;
    }
  }

  const timeDelta = compareTaskStartTimes(left.start_time, right.start_time, left.end_time, right.end_time);
  if (timeDelta !== 0) {
    return timeDelta;
  }

  if (left.position !== right.position) {
    return left.position - right.position;
  }

  const createdAtDelta = left.created_at.localeCompare(right.created_at);
  if (createdAtDelta !== 0) {
    return createdAtDelta;
  }

  return left.id - right.id;
}
