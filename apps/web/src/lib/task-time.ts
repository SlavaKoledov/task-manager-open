const TASK_TIME_PATTERN = /^(\d{2}):(\d{2})$/;

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
