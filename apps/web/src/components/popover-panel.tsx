import { type CSSProperties, type ReactNode, type RefObject, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

import { cn } from "@/lib/utils";

type PopoverPanelProps = {
  children: ReactNode;
  className?: string;
  anchorRef?: RefObject<HTMLElement>;
  panelRef?: RefObject<HTMLDivElement>;
  align?: "start" | "end";
  sideOffset?: number;
};

type PopoverPosition = CSSProperties & {
  visibility: "hidden" | "visible";
};

const VIEWPORT_PADDING = 12;

export function PopoverPanel({
  children,
  className,
  anchorRef,
  panelRef,
  align = "start",
  sideOffset = 8,
}: PopoverPanelProps) {
  const internalPanelRef = useRef<HTMLDivElement | null>(null);
  const resolvedPanelRef = panelRef ?? internalPanelRef;
  const [position, setPosition] = useState<PopoverPosition>({
    top: 0,
    left: 0,
    maxHeight: "min(24rem, calc(100vh - 1.5rem))",
    visibility: "hidden",
  });

  useLayoutEffect(() => {
    if (!anchorRef?.current || !resolvedPanelRef.current) {
      return;
    }

    let frameId = 0;

    const updatePosition = () => {
      cancelAnimationFrame(frameId);

      frameId = window.requestAnimationFrame(() => {
        const anchor = anchorRef.current;
        const panel = resolvedPanelRef.current;

        if (!anchor || !panel) {
          return;
        }

        const anchorRect = anchor.getBoundingClientRect();
        const panelRect = panel.getBoundingClientRect();
        const availableRight = window.innerWidth - VIEWPORT_PADDING;
        const panelWidth = panelRect.width;
        let left =
          align === "end"
            ? anchorRect.right - panelWidth
            : anchorRect.left;

        left = Math.min(Math.max(VIEWPORT_PADDING, left), availableRight - panelWidth);

        let top = anchorRect.bottom + sideOffset;
        let maxHeight = window.innerHeight - top - VIEWPORT_PADDING;

        if (top + panelRect.height > window.innerHeight - VIEWPORT_PADDING) {
          const flippedTop = anchorRect.top - panelRect.height - sideOffset;

          if (flippedTop >= VIEWPORT_PADDING) {
            top = flippedTop;
            maxHeight = anchorRect.top - sideOffset - VIEWPORT_PADDING;
          }
        }

        setPosition({
          top,
          left,
          maxHeight: Math.max(160, maxHeight),
          visibility: "visible",
        });
      });
    };

    updatePosition();
    window.addEventListener("resize", updatePosition);
    window.addEventListener("scroll", updatePosition, true);

    return () => {
      cancelAnimationFrame(frameId);
      window.removeEventListener("resize", updatePosition);
      window.removeEventListener("scroll", updatePosition, true);
    };
  }, [align, anchorRef, resolvedPanelRef, sideOffset]);

  const panel = (
    <div
      ref={resolvedPanelRef}
      style={anchorRef ? position : undefined}
      className={cn(
        anchorRef
          ? "fixed z-[70] overflow-y-auto"
          : "absolute top-full z-30 mt-2",
        "min-w-[12rem] rounded-[1.25rem] border border-border/80 bg-card/95 p-2 shadow-panel backdrop-blur-xl",
        className,
      )}
    >
      {children}
    </div>
  );

  if (anchorRef && typeof document !== "undefined") {
    return createPortal(panel, document.body);
  }

  return panel;
}
