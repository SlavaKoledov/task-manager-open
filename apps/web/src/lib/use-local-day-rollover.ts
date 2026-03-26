import { useEffect, useMemo, useState } from "react";

import { getLocalDateString, getLocalTimezone, getMillisecondsUntilNextLocalMidnight, getTomorrowDateString } from "@/lib/date";

type LocalDayState = {
  todayString: string;
  tomorrowString: string;
  timeZone: string;
};

function getSnapshot(date = new Date()): LocalDayState {
  const todayString = getLocalDateString(date);
  return {
    todayString,
    tomorrowString: getTomorrowDateString(date),
    timeZone: getLocalTimezone(),
  };
}

export function useLocalDayRollover(onRollover: () => void): LocalDayState {
  const [snapshot, setSnapshot] = useState<LocalDayState>(() => getSnapshot());

  useEffect(() => {
    let timeoutId = window.setTimeout(() => undefined, 0);

    const refreshSnapshot = () => {
      setSnapshot((current) => {
        const nextSnapshot = getSnapshot();

        if (
          nextSnapshot.todayString === current.todayString &&
          nextSnapshot.tomorrowString === current.tomorrowString &&
          nextSnapshot.timeZone === current.timeZone
        ) {
          return current;
        }

        onRollover();
        return nextSnapshot;
      });
    };

    const scheduleMidnightCheck = () => {
      window.clearTimeout(timeoutId);
      timeoutId = window.setTimeout(() => {
        refreshSnapshot();
        scheduleMidnightCheck();
      }, getMillisecondsUntilNextLocalMidnight(new Date()) + 50);
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        refreshSnapshot();
        scheduleMidnightCheck();
      }
    };

    const handleFocus = () => {
      refreshSnapshot();
      scheduleMidnightCheck();
    };

    scheduleMidnightCheck();
    window.addEventListener("focus", handleFocus);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      window.clearTimeout(timeoutId);
      window.removeEventListener("focus", handleFocus);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [onRollover]);

  return useMemo(() => snapshot, [snapshot]);
}
