import type { CSSProperties, ReactNode } from "react";

import { cn } from "@/lib/utils";

type BadgeProps = {
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
};

export function Badge({ children, className, style }: BadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full border border-border/70 bg-muted/55 px-2.5 py-1 text-xs font-medium text-muted-foreground",
        className,
      )}
      style={style}
    >
      {children}
    </span>
  );
}
