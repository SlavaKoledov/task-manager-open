import { forwardRef, type ButtonHTMLAttributes } from "react";

import { cn } from "@/lib/utils";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "default" | "outline" | "ghost" | "danger";
  size?: "default" | "sm" | "icon";
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    className,
    variant = "default",
    size = "default",
    type = "button",
    ...props
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-full border text-sm font-medium transition-colors duration-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60",
        variant === "default" &&
          "border-primary/80 bg-primary px-4 py-2 text-primary-foreground shadow-sm hover:brightness-110",
        variant === "outline" &&
          "border-border/80 bg-card/80 px-4 py-2 text-foreground shadow-sm backdrop-blur hover:bg-card hover:text-foreground",
        variant === "ghost" &&
          "border-transparent bg-transparent px-3 py-2 text-muted-foreground hover:bg-muted/70 hover:text-foreground",
        variant === "danger" &&
          "border-rose-200/70 bg-rose-500/10 px-4 py-2 text-rose-700 hover:bg-rose-500/15 dark:border-rose-400/20 dark:text-rose-200",
        size === "sm" && "px-3 py-1.5 text-xs",
        size === "icon" && "h-9 w-9 p-0",
        className,
      )}
      {...props}
    />
  );
});

Button.displayName = "Button";
