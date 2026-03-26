import type { TaskPriority, TaskRepeat } from "@/lib/types";

type TaskPriorityOption = {
  value: TaskPriority;
  label: string;
  shortLabel: string;
  sectionLabel: string;
  chipClassName: string;
  buttonClassName: string;
  accentClassName: string;
  accentColor: string;
};

type TaskRepeatOption = {
  value: TaskRepeat;
  label: string;
};

export const TASK_PRIORITY_OPTIONS: TaskPriorityOption[] = [
  {
    value: "urgent_important",
    label: "Urgent & important",
    shortLabel: "High",
    sectionLabel: "High",
    chipClassName: "border-rose-200/80 bg-rose-500/10 text-rose-700 dark:border-rose-400/20 dark:bg-rose-500/15 dark:text-rose-200",
    buttonClassName: "border-rose-200/80 bg-rose-500/10 text-rose-700 hover:bg-rose-500/15 dark:border-rose-400/20 dark:bg-rose-500/15 dark:text-rose-100",
    accentClassName: "bg-rose-500",
    accentColor: "#f43f5e",
  },
  {
    value: "not_urgent_important",
    label: "Important, not urgent",
    shortLabel: "Medium",
    sectionLabel: "Medium",
    chipClassName: "border-amber-200/80 bg-amber-500/10 text-amber-700 dark:border-amber-400/20 dark:bg-amber-500/15 dark:text-amber-200",
    buttonClassName: "border-amber-200/80 bg-amber-500/10 text-amber-700 hover:bg-amber-500/15 dark:border-amber-400/20 dark:bg-amber-500/15 dark:text-amber-100",
    accentClassName: "bg-amber-400",
    accentColor: "#f59e0b",
  },
  {
    value: "urgent_unimportant",
    label: "Urgent, not important",
    shortLabel: "Low",
    sectionLabel: "Low",
    chipClassName: "border-sky-200/80 bg-sky-500/10 text-sky-700 dark:border-sky-400/20 dark:bg-sky-500/15 dark:text-sky-200",
    buttonClassName: "border-sky-200/80 bg-sky-500/10 text-sky-700 hover:bg-sky-500/15 dark:border-sky-400/20 dark:bg-sky-500/15 dark:text-sky-100",
    accentClassName: "bg-sky-500",
    accentColor: "#0ea5e9",
  },
  {
    value: "not_urgent_unimportant",
    label: "Low pressure",
    shortLabel: "None",
    sectionLabel: "None",
    chipClassName: "border-zinc-300/80 bg-zinc-500/10 text-zinc-700 dark:border-zinc-500/40 dark:bg-zinc-500/15 dark:text-zinc-200",
    buttonClassName: "border-zinc-300/80 bg-zinc-500/10 text-zinc-700 hover:bg-zinc-500/15 dark:border-zinc-500/40 dark:bg-zinc-500/15 dark:text-zinc-100",
    accentClassName: "bg-zinc-400",
    accentColor: "#71717a",
  },
];

export const TASK_REPEAT_OPTIONS: TaskRepeatOption[] = [
  { value: "none", label: "No repeat" },
  { value: "daily", label: "Daily" },
  { value: "weekly", label: "Weekly" },
  { value: "monthly", label: "Monthly" },
  { value: "yearly", label: "Yearly" },
];

export function getTaskPriorityOption(priority: TaskPriority): TaskPriorityOption {
  return TASK_PRIORITY_OPTIONS.find((option) => option.value === priority) ?? TASK_PRIORITY_OPTIONS[3];
}

export function getTaskRepeatOption(repeat: TaskRepeat): TaskRepeatOption {
  return TASK_REPEAT_OPTIONS.find((option) => option.value === repeat) ?? TASK_REPEAT_OPTIONS[0];
}
