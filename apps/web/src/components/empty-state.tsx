import type { ReactNode } from "react";

type EmptyStateProps = {
  title: string;
  description: string;
  action?: ReactNode;
};

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="rounded-[2rem] border border-dashed border-border/70 bg-card/75 p-10 text-center shadow-panel backdrop-blur-xl">
      <h2 className="display-title text-3xl font-semibold text-foreground">{title}</h2>
      <p className="mx-auto mt-3 max-w-lg text-sm leading-6 text-muted-foreground">{description}</p>
      {action ? <div className="mt-6 flex justify-center">{action}</div> : null}
    </div>
  );
}
