import { Bell, ShieldCheck } from "lucide-react";

import { Dialog } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { DailyNotificationPreferences } from "@/lib/types";

type NotificationSettingsDialogProps = {
  open: boolean;
  preferences: DailyNotificationPreferences;
  permissionState: NotificationPermission | "unsupported";
  onOpenChange: (open: boolean) => void;
  onChangePreferences: (preferences: DailyNotificationPreferences) => void;
  onRequestPermission: () => Promise<void>;
};

export function NotificationSettingsDialog({
  open,
  preferences,
  permissionState,
  onOpenChange,
  onChangePreferences,
  onRequestPermission,
}: NotificationSettingsDialogProps) {
  const notificationsSupported = permissionState !== "unsupported";
  const permissionGranted = permissionState === "granted";
  const permissionBlocked = permissionState === "denied";

  return (
    <Dialog
      open={open}
      title="Notifications"
      description="Daily summaries can run while this app is open. Installed browser/PWA support may improve delivery, but closed-browser delivery is not guaranteed without server push."
      onOpenChange={onOpenChange}
      contentClassName="max-w-lg"
    >
      <div className="space-y-5">
        <section className="rounded-[1.35rem] border border-border/70 bg-muted/15 p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                <Bell className="h-4 w-4 text-primary" />
                <span>Daily notification</span>
              </div>
              <p className="mt-2 text-sm text-muted-foreground">
                Shows overdue and today tasks using your current local time.
              </p>
            </div>

            <button
              type="button"
              role="switch"
              aria-checked={preferences.enabled}
              disabled={!notificationsSupported || permissionBlocked}
              className={`inline-flex h-8 w-14 items-center rounded-full border px-1 transition-colors ${
                preferences.enabled ? "border-primary bg-primary/15" : "border-border/80 bg-card/80"
              } ${(!notificationsSupported || permissionBlocked) ? "cursor-not-allowed opacity-50" : ""}`}
              onClick={async () => {
                if (!notificationsSupported || permissionBlocked) {
                  return;
                }

                if (!preferences.enabled && !permissionGranted) {
                  await onRequestPermission();
                  return;
                }

                onChangePreferences({
                  ...preferences,
                  enabled: !preferences.enabled,
                });
              }}
            >
              <span
                className={`h-6 w-6 rounded-full transition-transform ${
                  preferences.enabled ? "translate-x-6 bg-primary" : "translate-x-0 bg-muted-foreground/45"
                }`}
              />
            </button>
          </div>

          <div className="mt-4 space-y-2">
            <label className="text-xs font-semibold uppercase tracking-[0.18em] text-muted-foreground" htmlFor="daily-time">
              Daily summary time
            </label>
            <Input
              id="daily-time"
              type="time"
              value={preferences.time}
              disabled={!preferences.enabled}
              onChange={(event) =>
                onChangePreferences({
                  ...preferences,
                  time: event.target.value || preferences.time,
                })
              }
            />
          </div>
        </section>

        <section className="rounded-[1.35rem] border border-border/70 bg-card/70 p-4">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <ShieldCheck className="h-4 w-4 text-primary" />
            <span>Browser permission</span>
          </div>

          <p className="mt-2 text-sm text-muted-foreground">
            {!notificationsSupported
              ? "This browser does not support notifications here."
              : permissionGranted
                ? "Notifications are allowed."
                : permissionBlocked
                  ? "Notifications are blocked in this browser."
                  : "Allow notifications to deliver daily summaries and task reminders while this app stays available."}
          </p>

          {notificationsSupported && !permissionGranted ? (
            <Button className="mt-4" variant="outline" onClick={() => void onRequestPermission()}>
              Allow notifications
            </Button>
          ) : null}
        </section>
      </div>
    </Dialog>
  );
}
