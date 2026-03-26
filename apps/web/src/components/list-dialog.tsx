import { type FormEvent, useEffect, useState } from "react";

import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { ListItem, ListPayload } from "@/lib/types";
import { ApiError } from "@/lib/api";

type ListDialogProps = {
  open: boolean;
  list: ListItem | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: ListPayload) => Promise<void>;
  onDelete?: (list: ListItem) => Promise<void>;
};

export function ListDialog({ open, list, onOpenChange, onSubmit, onDelete }: ListDialogProps) {
  const [name, setName] = useState("");
  const [color, setColor] = useState("#2563EB");
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setName(list?.name ?? "");
    setColor(list?.color ?? "#2563EB");
    setError(null);
  }, [open, list]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmedName = name.trim();
    if (!trimmedName) {
      setError("List name is required.");
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await onSubmit({ name: trimmedName, color });
      onOpenChange(false);
    } catch (caughtError) {
      setError(caughtError instanceof ApiError ? caughtError.message : "Failed to save the list.");
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!list || !onDelete) {
      return;
    }
    if (!window.confirm(`Delete "${list.name}"? Tasks in it will remain unassigned.`)) {
      return;
    }

    setIsSubmitting(true);
    setError(null);

    try {
      await onDelete(list);
      onOpenChange(false);
    } catch (caughtError) {
      setError(caughtError instanceof ApiError ? caughtError.message : "Failed to delete the list.");
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      title={list ? "Edit list" : "Create a list"}
      description="Lists are thematic groups such as Work, Study, or Personal."
      onOpenChange={onOpenChange}
    >
      <form className="hover-scrollbar min-h-0 flex-1 overflow-y-auto pr-1" onSubmit={handleSubmit}>
        <div className="space-y-5">
          <div className="space-y-2">
            <label className="text-sm font-medium text-foreground" htmlFor="list-name">
              Name
            </label>
            <Input
              id="list-name"
              value={name}
              placeholder="Work"
              onChange={(event) => setName(event.target.value)}
              maxLength={120}
            />
          </div>

          <div className="space-y-2">
            <label className="text-sm font-medium text-foreground" htmlFor="list-color">
              Color
            </label>
            <div className="flex items-center gap-3">
              <Input
                id="list-color"
                type="color"
                value={color}
                onChange={(event) => setColor(event.target.value)}
                className="h-12 w-16 cursor-pointer rounded-2xl px-2 py-2"
              />
              <span className="text-sm text-muted-foreground">{color.toUpperCase()}</span>
            </div>
          </div>

          {error ? <p className="text-sm text-red-600">{error}</p> : null}

          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:items-center sm:justify-between">
            {list && onDelete ? (
              <Button type="button" variant="danger" onClick={handleDelete} disabled={isSubmitting}>
                Delete list
              </Button>
            ) : (
              <span />
            )}
            <div className="flex gap-3">
              <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "Saving..." : list ? "Save changes" : "Create list"}
              </Button>
            </div>
          </div>
        </div>
      </form>
    </Dialog>
  );
}
