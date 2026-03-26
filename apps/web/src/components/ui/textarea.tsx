import { forwardRef, type TextareaHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className, ...props }, ref) {
    return (
      <textarea
        ref={ref}
        className={cn(
          "min-h-28 w-full rounded-3xl border border-input/80 bg-card/70 px-4 py-3 text-sm text-foreground shadow-sm outline-none transition-colors duration-100 placeholder:text-muted-foreground focus:border-primary focus:ring-2 focus:ring-primary/20",
          className,
        )}
        {...props}
      />
    );
  },
);
