import { useEffect, useRef } from "react";

import { parseLocalDateString } from "@/lib/date";
import { isDueDateOverdue } from "@/lib/task-groups";
import type { DailyNotificationPreferences, TaskItem, TaskSubtask } from "@/lib/types";

const DAILY_NOTIFICATION_STORAGE_KEY = "task-manager.notifications.daily";
const LAST_DAILY_NOTIFICATION_KEY = "task-manager.notifications.daily.last";
const LAST_TASK_REMINDER_KEY = "task-manager.notifications.reminder.last";
const DEFAULT_DAILY_NOTIFICATION_TIME = "09:00";

type ReminderTask = Pick<TaskSubtask, "id" | "title" | "due_date" | "reminder_time" | "is_done">;

export function loadDailyNotificationPreferences(): DailyNotificationPreferences {
  if (typeof window === "undefined") {
    return { enabled: false, time: DEFAULT_DAILY_NOTIFICATION_TIME };
  }

  const rawValue = window.localStorage.getItem(DAILY_NOTIFICATION_STORAGE_KEY);
  if (!rawValue) {
    return { enabled: false, time: DEFAULT_DAILY_NOTIFICATION_TIME };
  }

  try {
    const parsed = JSON.parse(rawValue) as Partial<DailyNotificationPreferences>;
    return {
      enabled: parsed.enabled === true,
      time: typeof parsed.time === "string" && parsed.time ? parsed.time : DEFAULT_DAILY_NOTIFICATION_TIME,
    };
  } catch {
    return { enabled: false, time: DEFAULT_DAILY_NOTIFICATION_TIME };
  }
}

export function persistDailyNotificationPreferences(preferences: DailyNotificationPreferences) {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(DAILY_NOTIFICATION_STORAGE_KEY, JSON.stringify(preferences));
}

export function getNotificationPermissionState(): NotificationPermission | "unsupported" {
  if (typeof window === "undefined" || typeof Notification === "undefined") {
    return "unsupported";
  }

  return Notification.permission;
}

export async function requestBrowserNotificationPermission(): Promise<NotificationPermission | "unsupported"> {
  if (typeof window === "undefined" || typeof Notification === "undefined") {
    return "unsupported";
  }

  return Notification.requestPermission();
}

export async function registerNotificationWorker() {
  if (typeof window === "undefined" || !("serviceWorker" in navigator) || !window.isSecureContext) {
    return null;
  }

  const existingRegistration = await navigator.serviceWorker.getRegistration();
  if (existingRegistration) {
    return existingRegistration;
  }

  return navigator.serviceWorker.register("/notifications-sw.js");
}

export async function showBrowserNotification(title: string, body: string, tag: string) {
  if (typeof window === "undefined" || typeof Notification === "undefined" || Notification.permission !== "granted") {
    return;
  }

  const registration = await registerNotificationWorker();
  if (registration && "showNotification" in registration) {
    await registration.showNotification(title, {
      body,
      tag,
      badge: "/task-manager-logo.svg",
      icon: "/task-manager-logo.svg",
    });
    return;
  }

  new Notification(title, { body, tag });
}

export function buildDailyNotificationLines(tasks: TaskItem[], todayString: string): string[] {
  const visibleTasks = flattenTasks(tasks)
    .filter((task) => !task.is_done && (task.due_date === todayString || isDueDateOverdue(task.due_date, todayString)))
    .sort((left, right) => {
      const leftRank = isDueDateOverdue(left.due_date, todayString) ? 0 : left.reminder_time ? 2 : 1;
      const rightRank = isDueDateOverdue(right.due_date, todayString) ? 0 : right.reminder_time ? 2 : 1;
      if (leftRank !== rightRank) {
        return leftRank - rightRank;
      }

      const leftTime = left.reminder_time ?? "99:99";
      const rightTime = right.reminder_time ?? "99:99";
      if (leftTime !== rightTime) {
        return leftTime.localeCompare(rightTime);
      }

      return left.title.localeCompare(right.title);
    })
    .map((task) => {
      if (isDueDateOverdue(task.due_date, todayString)) {
        return `Overdue "${task.title}"`;
      }

      if (task.reminder_time) {
        return `${task.reminder_time} "${task.title}"`;
      }

      return `Today "${task.title}"`;
    });

  return visibleTasks.length <= 6 ? visibleTasks : [...visibleTasks.slice(0, 6), `+${visibleTasks.length - 6} more`];
}

export function findNextTaskReminder(tasks: TaskItem[], now = new Date()): ReminderTask | null {
  const upcomingReminders = flattenTasks(tasks)
    .filter((task) => !task.is_done && task.due_date && task.reminder_time)
    .map((task) => {
      const dueDateValue = task.due_date;
      const reminderTimeValue = task.reminder_time;
      if (!dueDateValue || !reminderTimeValue) {
        return null;
      }

      const dueDate = parseLocalDateString(dueDateValue);
      if (!dueDate) {
        return null;
      }

      const [hours, minutes] = reminderTimeValue.split(":").map((value) => Number(value));
      if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
        return null;
      }

      const scheduledAt = new Date(dueDate);
      scheduledAt.setHours(hours, minutes, 0, 0);
      return scheduledAt > now ? { task, scheduledAt } : null;
    })
    .filter((entry): entry is { task: ReminderTask; scheduledAt: Date } => entry !== null)
    .sort((left, right) => left.scheduledAt.getTime() - right.scheduledAt.getTime());

  return upcomingReminders[0]?.task ?? null;
}

export function useBrowserNotificationScheduler(
  tasks: TaskItem[],
  todayString: string,
  dailyPreferences: DailyNotificationPreferences,
  permissionState: NotificationPermission | "unsupported",
) {
  const latestStateRef = useRef({
    tasks,
    todayString,
    dailyPreferences,
    permissionState,
  });

  useEffect(() => {
    latestStateRef.current = {
      tasks,
      todayString,
      dailyPreferences,
      permissionState,
    };
  }, [dailyPreferences, permissionState, tasks, todayString]);

  useEffect(() => {
    void registerNotificationWorker().catch(() => undefined);
  }, []);

  useEffect(() => {
    if (permissionState !== "granted" || typeof window === "undefined") {
      return;
    }

    let disposed = false;
    const timers: number[] = [];

    const scheduleDailyNotification = () => {
      const {
        dailyPreferences: currentPreferences,
      } = latestStateRef.current;

      if (!currentPreferences.enabled) {
        return;
      }

      const now = new Date();
      const [hours, minutes] = currentPreferences.time.split(":").map((value) => Number(value));
      const nextDailyRun = new Date(now);
      nextDailyRun.setHours(hours, minutes, 0, 0);

      if (nextDailyRun <= now) {
        nextDailyRun.setDate(nextDailyRun.getDate() + 1);
      }

      timers.push(
        window.setTimeout(() => {
          if (disposed) {
            return;
          }

          const { tasks: currentTasks, todayString: currentTodayString } = latestStateRef.current;
          const alreadySentForDate = window.localStorage.getItem(LAST_DAILY_NOTIFICATION_KEY);

          if (alreadySentForDate !== currentTodayString) {
            const lines = buildDailyNotificationLines(currentTasks, currentTodayString);
            if (lines.length > 0) {
              void showBrowserNotification("Сегодняшние задачи:", lines.join("\n"), `daily-${currentTodayString}`);
              window.localStorage.setItem(LAST_DAILY_NOTIFICATION_KEY, currentTodayString);
            }
          }

          scheduleDailyNotification();
        }, Math.max(0, nextDailyRun.getTime() - now.getTime())),
      );
    };

    const scheduleNextReminder = () => {
      const nextReminder = findNextTaskReminder(latestStateRef.current.tasks, new Date());
      if (!nextReminder?.due_date || !nextReminder.reminder_time) {
        return;
      }

      const dueDate = parseLocalDateString(nextReminder.due_date);
      const [hours, minutes] = nextReminder.reminder_time.split(":").map((value) => Number(value));

      if (!dueDate || !Number.isFinite(hours) || !Number.isFinite(minutes)) {
        return;
      }

      const scheduledAt = new Date(dueDate);
      scheduledAt.setHours(hours, minutes, 0, 0);
      const reminderKey = `${nextReminder.id}:${nextReminder.due_date}:${nextReminder.reminder_time}`;

      timers.push(
        window.setTimeout(() => {
          if (disposed) {
            return;
          }

          const lastReminderKey = window.localStorage.getItem(LAST_TASK_REMINDER_KEY);
          if (lastReminderKey !== reminderKey) {
            void showBrowserNotification("Reminder", nextReminder.title, `task-${reminderKey}`);
            window.localStorage.setItem(LAST_TASK_REMINDER_KEY, reminderKey);
          }

          scheduleNextReminder();
        }, Math.max(0, scheduledAt.getTime() - Date.now())),
      );
    };

    scheduleDailyNotification();
    scheduleNextReminder();

    return () => {
      disposed = true;
      timers.forEach((timerId) => window.clearTimeout(timerId));
    };
  }, [dailyPreferences.enabled, dailyPreferences.time, permissionState, tasks, todayString]);
}

function flattenTasks(tasks: TaskItem[]): ReminderTask[] {
  return tasks.flatMap((task) => [
    {
      id: task.id,
      title: task.title,
      due_date: task.due_date,
      reminder_time: task.reminder_time,
      is_done: task.is_done,
    },
    ...task.subtasks.map((subtask) => ({
      id: subtask.id,
      title: subtask.title,
      due_date: subtask.due_date,
      reminder_time: subtask.reminder_time,
      is_done: subtask.is_done,
    })),
  ]);
}
