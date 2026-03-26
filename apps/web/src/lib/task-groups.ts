import { addDays, getTomorrowDateString, parseLocalDateString } from "@/lib/date";
import type { AllTaskGroupId, TaskItem, TaskPriority, TaskSectionId, TaskTopLevelReorderScope, ViewMode } from "@/lib/types";
export type SidebarTaskCounts = {
  all: number;
  today: number;
  tomorrow: number;
  inbox: number;
  listTaskCounts: Record<number, number>;
};

export type TaskSection = {
  id: TaskSectionId;
  title: string;
  tasks: TaskItem[];
};

export type TaskCollection = {
  sections: TaskSection[];
  doneTasks: TaskItem[];
  activeCount: number;
  doneCount: number;
  totalCount: number;
};

export type AllTaskGroup = TaskCollection & {
  id: AllTaskGroupId;
  title: string;
  emptyDescription: string;
  tasks: TaskItem[];
};

export function buildTaskSectionCollapseKey(groupKey: string, sectionId: TaskSectionId): string {
  return `${groupKey}:${sectionId}`;
}

export const ALL_TASK_GROUPS: { id: AllTaskGroupId; title: string; emptyDescription: string }[] = [
  {
    id: "overdue",
    title: "Overdue",
    emptyDescription: "Nothing is overdue.",
  },
  {
    id: "today",
    title: "Today",
    emptyDescription: "Nothing scheduled for today.",
  },
  {
    id: "tomorrow",
    title: "Tomorrow",
    emptyDescription: "Nothing queued for tomorrow.",
  },
  {
    id: "next_7_days",
    title: "Next 7 Days",
    emptyDescription: "Nothing due in the next 7 days.",
  },
  {
    id: "later",
    title: "Later",
    emptyDescription: "Nothing scheduled later on.",
  },
  {
    id: "no_date",
    title: "No Date",
    emptyDescription: "Everything here has a date.",
  },
];

const PRIORITY_SECTION_META: { id: TaskPriority; title: string }[] = [
  { id: "urgent_important", title: "High" },
  { id: "not_urgent_important", title: "Medium" },
  { id: "urgent_unimportant", title: "Low" },
  { id: "not_urgent_unimportant", title: "None" },
];

export function isDueDateOverdue(dueDate: string | null, todayString: string): boolean {
  if (!dueDate) {
    return false;
  }

  const todayDate = parseLocalDateString(todayString);
  const dueDateValue = parseLocalDateString(dueDate);

  if (!todayDate || !dueDateValue) {
    return false;
  }

  return dueDateValue < todayDate;
}

export function isTaskOverdue(task: Pick<TaskItem, "due_date" | "is_done">, todayString: string): boolean {
  return !task.is_done && isDueDateOverdue(task.due_date, todayString);
}

export function classifyTaskDueDateGroup(dueDate: string | null, todayString: string): AllTaskGroupId {
  if (!dueDate) {
    return "no_date";
  }

  if (isDueDateOverdue(dueDate, todayString)) {
    return "overdue";
  }

  const todayDate = parseLocalDateString(todayString);
  const dueDateValue = parseLocalDateString(dueDate);
  if (!todayDate || !dueDateValue) {
    return "later";
  }

  if (dueDateValue.getTime() === todayDate.getTime()) {
    return "today";
  }

  const tomorrowString = getTomorrowDateString(todayDate);
  if (dueDate === tomorrowString) {
    return "tomorrow";
  }

  const nextSevenDaysEnd = addDays(todayDate, 7);
  if (dueDateValue > addDays(todayDate, 1) && dueDateValue <= nextSevenDaysEnd) {
    return "next_7_days";
  }

  return "later";
}

export function getTaskSectionId(task: Pick<TaskItem, "is_pinned" | "priority">): TaskSectionId {
  return task.is_pinned ? "pinned" : task.priority;
}

export function groupTasksByPriority(tasks: TaskItem[]): TaskSection[] {
  const sections: TaskSection[] = [];
  const pinnedTasks = tasks.filter((task) => task.is_pinned);
  const regularTasks = tasks.filter((task) => !task.is_pinned);

  if (pinnedTasks.length > 0) {
    sections.push({
      id: "pinned",
      title: "Pinned",
      tasks: pinnedTasks,
    });
  }

  for (const prioritySection of PRIORITY_SECTION_META) {
    const sectionTasks = regularTasks.filter((task) => task.priority === prioritySection.id);
    if (sectionTasks.length === 0) {
      continue;
    }

    sections.push({
      id: prioritySection.id,
      title: prioritySection.title,
      tasks: sectionTasks,
    });
  }

  return sections;
}

export function taskMatchesTopLevelReorderScope(task: TaskItem, scope: TaskTopLevelReorderScope): boolean {
  if (task.parent_id !== null || task.is_done) {
    return false;
  }

  if (getTaskSectionId(task) !== scope.section_id) {
    return false;
  }

  if (scope.view === "list") {
    return task.list_id === scope.list_id;
  }

  if (scope.view === "inbox") {
    return task.due_date === null;
  }

  if (scope.view === "today" || scope.view === "tomorrow") {
    return task.due_date === scope.target_date;
  }

  if (scope.view === "all") {
    return classifyTaskDueDateGroup(task.due_date, scope.reference_date) === scope.group_id;
  }

  return false;
}

export function getTopLevelTaskIdsForReorderScope(tasks: TaskItem[], scope: TaskTopLevelReorderScope): number[] {
  return tasks.filter((task) => taskMatchesTopLevelReorderScope(task, scope)).map((task) => task.id);
}

type BuildTopLevelTaskReorderScopeParams = {
  task: TaskItem;
  mode: ViewMode;
  todayString: string;
  targetDateString?: string;
  listId?: number | null;
};

export function buildTopLevelTaskReorderScopeForTask({
  task,
  mode,
  todayString,
  targetDateString,
  listId,
}: BuildTopLevelTaskReorderScopeParams): TaskTopLevelReorderScope | null {
  if (task.parent_id !== null || task.is_done) {
    return null;
  }

  const section_id = getTaskSectionId(task);

  if (mode === "list") {
    if (!listId || task.list_id !== listId) {
      return null;
    }

    return {
      view: "list",
      list_id: listId,
      section_id,
    };
  }

  if (mode === "inbox") {
    return task.due_date === null
      ? {
          view: "inbox",
          section_id,
        }
      : null;
  }

  if (mode === "today" || mode === "tomorrow") {
    if (!targetDateString || task.due_date !== targetDateString) {
      return null;
    }

    return {
      view: mode,
      target_date: targetDateString,
      section_id,
    };
  }

  return {
    view: "all",
    group_id: classifyTaskDueDateGroup(task.due_date, todayString),
    reference_date: todayString,
    section_id,
  };
}

type BuildTopLevelTaskDropScopeParams = {
  mode: ViewMode;
  sectionId: TaskSectionId;
  todayString: string;
  tomorrowString: string;
  listId?: number | null;
  groupId?: AllTaskGroupId;
};

export function buildTopLevelTaskDropScope({
  mode,
  sectionId,
  todayString,
  tomorrowString,
  listId,
  groupId,
}: BuildTopLevelTaskDropScopeParams): TaskTopLevelReorderScope | null {
  if (mode === "list") {
    if (!listId) {
      return null;
    }

    return {
      view: "list",
      list_id: listId,
      section_id: sectionId,
    };
  }

  if (mode === "inbox") {
    return {
      view: "inbox",
      section_id: sectionId,
    };
  }

  if (mode === "today") {
    return {
      view: "today",
      target_date: todayString,
      section_id: sectionId,
    };
  }

  if (mode === "tomorrow") {
    return {
      view: "tomorrow",
      target_date: tomorrowString,
      section_id: sectionId,
    };
  }

  if (!groupId) {
    return null;
  }

  return {
    view: "all",
    group_id: groupId,
    reference_date: todayString,
    section_id: sectionId,
  };
}

export function groupTasksWithDone(tasks: TaskItem[]): TaskCollection {
  const activeTasks = tasks.filter((task) => !task.is_done);
  const doneTasks = tasks.filter((task) => task.is_done);

  return {
    sections: groupTasksByPriority(activeTasks),
    doneTasks,
    activeCount: activeTasks.length,
    doneCount: doneTasks.length,
    totalCount: tasks.length,
  };
}

export function buildSidebarTaskCounts(
  tasks: TaskItem[],
  listIds: number[],
  todayString: string,
  tomorrowString: string,
): SidebarTaskCounts {
  const listTaskCounts = Object.fromEntries(listIds.map((listId) => [listId, 0])) as Record<number, number>;
  let all = 0;
  let today = 0;
  let tomorrow = 0;
  let inbox = 0;

  for (const task of tasks) {
    if (task.is_done) {
      continue;
    }

    all += 1;

    if (task.due_date !== null && task.due_date <= todayString) {
      today += 1;
    }

    if (task.due_date === tomorrowString) {
      tomorrow += 1;
    }

    if (task.due_date === null) {
      inbox += 1;
    }

    if (task.list_id !== null) {
      listTaskCounts[task.list_id] = (listTaskCounts[task.list_id] ?? 0) + 1;
    }
  }

  return {
    all,
    today,
    tomorrow,
    inbox,
    listTaskCounts,
  };
}

export function filterVisibleTasks(tasks: TaskItem[], showCompleted: boolean): TaskItem[] {
  return showCompleted ? tasks : tasks.filter((task) => !task.is_done);
}

export function groupTasksForAllView(tasks: TaskItem[], todayString: string): AllTaskGroup[] {
  const groups = ALL_TASK_GROUPS.reduce(
    (accumulator, group) => {
      accumulator[group.id] = [];
      return accumulator;
    },
    {
      overdue: [] as TaskItem[],
      today: [] as TaskItem[],
      tomorrow: [] as TaskItem[],
      next_7_days: [] as TaskItem[],
      later: [] as TaskItem[],
      no_date: [] as TaskItem[],
    },
  );

  for (const task of tasks) {
    groups[classifyTaskDueDateGroup(task.due_date, todayString)].push(task);
  }

  return ALL_TASK_GROUPS.map((group) => ({
    ...group,
    tasks: groups[group.id],
    ...groupTasksWithDone(groups[group.id]),
  }));
}

export function getVisibleTaskCollection(tasks: TaskItem[], showCompleted: boolean): TaskCollection {
  return groupTasksWithDone(filterVisibleTasks(tasks, showCompleted));
}

export function getVisibleAllTaskGroups(tasks: TaskItem[], todayString: string, showCompleted: boolean): AllTaskGroup[] {
  const groups = groupTasksForAllView(filterVisibleTasks(tasks, showCompleted), todayString);

  if (showCompleted) {
    return groups;
  }

  return groups.filter((group) => group.activeCount > 0);
}

export function getVisibleTodayTaskGroups(tasks: TaskItem[], todayString: string, showCompleted: boolean): AllTaskGroup[] {
  return getVisibleAllTaskGroups(tasks, todayString, showCompleted).filter(
    (group) => group.id === "overdue" || group.id === "today",
  );
}
