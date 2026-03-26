import { type ReactNode, useEffect, useId } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

type DialogProps = {
  open: boolean;
  title: string;
  description?: string;
  children: ReactNode;
  onOpenChange: (open: boolean) => void;
  contentClassName?: string;
  headerClassName?: string;
  titleClassName?: string;
  descriptionClassName?: string;
};

export function Dialog({
  open,
  title,
  description,
  children,
  onOpenChange,
  contentClassName,
  headerClassName,
  titleClassName,
  descriptionClassName,
}: DialogProps) {
  const titleId = useId();
  const descriptionId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onOpenChange(false);
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [open, onOpenChange]);

  if (!open || typeof document === "undefined") {
    return null;
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-end justify-center overflow-hidden bg-slate-950/45 px-3 py-3 backdrop-blur-sm sm:items-center sm:px-6 sm:py-6"
      onClick={() => onOpenChange(false)}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        className={cn(
          "flex max-h-[calc(100dvh-1.5rem)] w-full max-w-xl flex-col overflow-hidden rounded-[2rem] border border-border/80 bg-card/95 p-6 shadow-panel backdrop-blur-xl sm:max-h-[calc(100dvh-3rem)]",
          contentClassName,
        )}
        onClick={(event) => event.stopPropagation()}
      >
        <div className={cn("mb-6 shrink-0 flex items-start justify-between gap-4", headerClassName)}>
          <div>
            <h2 id={titleId} className={cn("display-title text-2xl font-semibold text-foreground", titleClassName)}>
              {title}
            </h2>
            {description ? (
              <p id={descriptionId} className={cn("mt-2 text-sm text-muted-foreground", descriptionClassName)}>
                {description}
              </p>
            ) : null}
          </div>
          <Button variant="ghost" size="icon" aria-label="Close dialog" onClick={() => onOpenChange(false)}>
            <X className="h-4 w-4" />
          </Button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}
