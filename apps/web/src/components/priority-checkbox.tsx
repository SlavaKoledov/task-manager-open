import type { KeyboardEvent } from "react";
import { Check } from "lucide-react";

import { getTaskPriorityOption } from "@/lib/task-options";
import type { TaskPriority } from "@/lib/types";
import { cn } from "@/lib/utils";

type PriorityCheckboxProps = {
  checked: boolean;
  priority: TaskPriority;
  onChange: (checked: boolean) => void;
  className?: string;
  title?: string;
};

export function PriorityCheckbox({ checked, priority, onChange, className, title }: PriorityCheckboxProps) {
  const option = getTaskPriorityOption(priority);

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key !== "Enter" && event.key !== " ") {
      return;
    }

    event.preventDefault();
    onChange(!checked);
  }

  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      aria-label={title ?? "Toggle task"}
      className={cn(
        "inline-flex h-6 w-6 items-center justify-center rounded-md border bg-card/80 transition-transform duration-100 hover:scale-[1.03] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        checked && "text-white",
        className,
      )}
      style={{
        borderColor: option.accentColor,
        backgroundColor: checked ? option.accentColor : "transparent",
      }}
      onClick={(event) => {
        event.stopPropagation();
        onChange(!checked);
      }}
      onKeyDown={handleKeyDown}
    >
      <Check className={cn("h-4 w-4", checked ? "opacity-100" : "opacity-0")} />
    </button>
  );
}
