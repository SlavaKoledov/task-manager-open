import { useEffect } from "react";
import type { QueryClient } from "@tanstack/react-query";

import { createLiveEventSource, parseLiveEvent } from "@/lib/api";

export function useLiveSync(queryClient: QueryClient) {
  useEffect(() => {
    let disposed = false;
    let reconnectTimeoutId: number | null = null;
    let eventSource: EventSource | null = null;
    let pendingTaskRefresh = false;
    let pendingListRefresh = false;
    let flushTimeoutId: number | null = null;

    const flushInvalidations = () => {
      flushTimeoutId = null;

      if (pendingTaskRefresh) {
        pendingTaskRefresh = false;
        void queryClient.invalidateQueries({ queryKey: ["tasks"] });
      }

      if (pendingListRefresh) {
        pendingListRefresh = false;
        void queryClient.invalidateQueries({ queryKey: ["lists"] });
      }
    };

    const scheduleFlush = () => {
      if (flushTimeoutId !== null) {
        return;
      }

      flushTimeoutId = window.setTimeout(flushInvalidations, 120);
    };

    const connect = () => {
      if (disposed) {
        return;
      }

      eventSource = createLiveEventSource();

      eventSource.addEventListener("change", (event) => {
        const parsedEvent = parseLiveEvent(event as MessageEvent<string>);
        if (!parsedEvent) {
          return;
        }

        if (parsedEvent.entity_type === "task") {
          pendingTaskRefresh = true;
        }

        if (parsedEvent.entity_type === "list") {
          pendingListRefresh = true;
        }

        scheduleFlush();
      });

      eventSource.onerror = () => {
        if (disposed) {
          return;
        }

        eventSource?.close();
        eventSource = null;

        if (reconnectTimeoutId === null) {
          reconnectTimeoutId = window.setTimeout(() => {
            reconnectTimeoutId = null;
            connect();
          }, 2_000);
        }
      };
    };

    connect();

    return () => {
      disposed = true;
      if (reconnectTimeoutId !== null) {
        window.clearTimeout(reconnectTimeoutId);
      }
      if (flushTimeoutId !== null) {
        window.clearTimeout(flushTimeoutId);
      }
      eventSource?.close();
    };
  }, [queryClient]);
}
