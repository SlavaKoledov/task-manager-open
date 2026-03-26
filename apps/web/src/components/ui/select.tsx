import type { SelectHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

export function Select({ className, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={cn(
        "w-full rounded-2xl border border-input/80 bg-card/70 px-4 py-3 text-sm text-foreground shadow-sm outline-none transition-colors duration-100 focus:border-primary focus:ring-2 focus:ring-primary/20",
        className,
      )}
      {...props}
    />
  );
}
