export const SHOW_COMPLETED_STORAGE_KEY = "task-manager.show-completed";
export const NEW_TASK_PLACEMENT_STORAGE_KEY = "task-manager.new-task-placement";

export function parseStoredShowCompleted(value: string | null): boolean {
  if (value === "false") {
    return false;
  }

  return true;
}

export function serializeShowCompleted(value: boolean): string {
  return String(value);
}

export function parseStoredNewTaskPlacement(value: string | null) {
  return value === "start" ? "start" : "end";
}

export function serializeNewTaskPlacement(value: "start" | "end"): string {
  return value;
}
