import { useEffect, useMemo, useRef } from "react";

import { Dialog } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { descriptionBlocksToText } from "@/lib/task-description";
import type { DescriptionBlock } from "@/lib/types";

type TaskDescriptionDialogProps = {
  open: boolean;
  blocks: DescriptionBlock[];
  onChange: (nextText: string) => void;
  onOpenChange: (open: boolean) => void;
};

export function TaskDescriptionDialog({ open, blocks, onChange, onOpenChange }: TaskDescriptionDialogProps) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const value = useMemo(() => descriptionBlocksToText(blocks) ?? "", [blocks]);

  useEffect(() => {
    if (!open) {
      return;
    }

    window.requestAnimationFrame(() => textareaRef.current?.focus());
  }, [open]);

  return (
    <Dialog
      open={open}
      title="Description"
      description="Read and edit the full task description in one place."
      onOpenChange={onOpenChange}
      contentClassName="h-[min(94dvh,56rem)] w-full max-w-[min(72rem,calc(100vw-2rem))] rounded-[1.8rem] p-0"
      headerClassName="border-b border-border/70 px-6 py-4"
      titleClassName="text-[1.35rem]"
      descriptionClassName="text-sm"
    >
      <div className="flex min-h-0 flex-1 flex-col px-6 pb-6">
        <Textarea
          ref={textareaRef}
          value={value}
          rows={18}
          placeholder="Add notes"
          className="min-h-0 flex-1 resize-none border-0 bg-transparent px-0 py-0 text-sm leading-7 shadow-none focus:border-transparent focus:ring-0"
          onChange={(event) => onChange(event.target.value)}
        />
      </div>
    </Dialog>
  );
}
