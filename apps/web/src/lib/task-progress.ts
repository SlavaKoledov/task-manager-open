type SubtaskProgressItem = {
  is_done: boolean;
};

export type SubtaskProgressSummary = {
  total: number;
  done: number;
  percent: number | null;
};

export function getSubtaskProgressSummary(subtasks: readonly SubtaskProgressItem[]): SubtaskProgressSummary {
  const total = subtasks.length;

  if (total === 0) {
    return {
      total: 0,
      done: 0,
      percent: null,
    };
  }

  const done = subtasks.reduce((count, subtask) => count + (subtask.is_done ? 1 : 0), 0);

  return {
    total,
    done,
    percent: Math.round((done / total) * 100),
  };
}
