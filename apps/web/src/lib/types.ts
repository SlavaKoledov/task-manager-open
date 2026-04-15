export type ListItem = {
  id: number;
  name: string;
  color: string;
  position: number;
  created_at: string;
  updated_at: string;
};

export type TaskPriority =
  | "urgent_important"
  | "not_urgent_important"
  | "urgent_unimportant"
  | "not_urgent_unimportant";

export type TaskRepeat = "none" | "daily" | "weekly" | "monthly" | "yearly" | "custom";
export type TaskCustomRepeatUnit = "day" | "week" | "month" | "year";
export type TaskCustomRepeatConfig = {
  interval: number;
  unit: TaskCustomRepeatUnit;
  skip_weekends: boolean;
  weekdays: number[];
  month_day: number | null;
  month: number | null;
  day: number | null;
};
export type AllTaskGroupId = "overdue" | "today" | "tomorrow" | "next_7_days" | "later" | "no_date";
export type TaskSectionId = "pinned" | TaskPriority;
export type TaskTopLevelReorderScope =
  | {
      view: "list";
      list_id: number;
      section_id: TaskSectionId;
    }
  | {
      view: "inbox";
      section_id: TaskSectionId;
    }
  | {
      view: "today" | "tomorrow";
      target_date: string;
      section_id: TaskSectionId;
    }
  | {
      view: "all";
      group_id: AllTaskGroupId;
      reference_date: string;
      section_id: TaskSectionId;
    };
export type TaskMovePayload =
  | {
      destination_parent_id: number;
      ordered_ids: number[];
      destination_scope?: never;
    }
  | {
      destination_parent_id?: never;
      destination_scope: TaskTopLevelReorderScope;
      ordered_ids: number[];
    };

export type TaskMoveResult = {
  task: TaskSubtask;
  affected_tasks: TaskItem[];
  removed_top_level_task_ids: number[];
};
export type NewTaskPlacementPreference = "start" | "end";

export type DescriptionTextBlock = {
  kind: "text";
  text: string;
};

export type DescriptionCheckboxBlock = {
  kind: "checkbox";
  text: string;
  checked: boolean;
};

export type DescriptionBlock = DescriptionTextBlock | DescriptionCheckboxBlock;

export type TaskSubtask = {
  id: number;
  title: string;
  description: string | null;
  description_blocks: DescriptionBlock[];
  due_date: string | null;
  reminder_time: string | null;
  repeat_config?: TaskCustomRepeatConfig | null;
  repeat_until: string | null;
  is_done: boolean;
  is_pinned: boolean;
  priority: TaskPriority;
  repeat: TaskRepeat;
  parent_id: number | null;
  position: number;
  list_id: number | null;
  created_at: string;
  updated_at: string;
};

export type TaskItem = TaskSubtask & {
  subtasks: TaskSubtask[];
};

export type ListPayload = {
  name: string;
  color: string;
};

export type TaskEditableFields = {
  title: string;
  description: string | null;
  description_blocks: DescriptionBlock[];
  due_date: string | null;
  reminder_time: string | null;
  repeat_config?: TaskCustomRepeatConfig | null;
  repeat_until: string | null;
  is_done: boolean;
  is_pinned: boolean;
  priority: TaskPriority;
  repeat: TaskRepeat;
  list_id: number | null;
};

export type TaskCreateSubtaskPayload = {
  title: string;
  description: string | null;
  description_blocks: DescriptionBlock[];
  due_date: string | null;
  reminder_time: string | null;
  is_done: boolean;
};

export type TaskCreatePayload = TaskEditableFields & {
  parent_id: number | null;
  subtasks: TaskCreateSubtaskPayload[];
};

export type TaskUpdatePayload = Partial<TaskEditableFields>;

export type ViewMode = "all" | "today" | "tomorrow" | "inbox" | "list";

export type DraggedTaskItem = {
  task_id: number;
  parent_id: number | null;
  has_subtasks: boolean;
};

export type TaskDraft = {
  title: string;
  description_blocks: DescriptionBlock[];
  due_date: string;
  reminder_time: string;
  repeat_config: TaskCustomRepeatConfig | null;
  repeat_until: string;
  is_done: boolean;
  is_pinned: boolean;
  priority: TaskPriority;
  repeat: TaskRepeat;
  list_id: string;
};

export type Theme = "light" | "dark";

export type DailyNotificationPreferences = {
  enabled: boolean;
  time: string;
};

export type LiveEvent = {
  version: number;
  entity_type: "task" | "list";
  entity_ids: number[];
  changed_at: string;
};
