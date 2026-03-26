import { useEffect, type RefObject } from "react";

type OutsideRef<T extends HTMLElement> = RefObject<T> | ReadonlyArray<RefObject<HTMLElement>>;

export function useClickOutside<T extends HTMLElement>(
  refs: OutsideRef<T>,
  active: boolean,
  onOutsideClick: () => void,
) {
  useEffect(() => {
    if (!active) {
      return;
    }

    const refList = Array.isArray(refs) ? refs : [refs];

    function handlePointerDown(event: MouseEvent) {
      const target = event.target as Node;
      const clickedInside = refList.some((ref) => ref.current?.contains(target));

      if (!clickedInside) {
        onOutsideClick();
      }
    }

    window.addEventListener("mousedown", handlePointerDown);
    return () => window.removeEventListener("mousedown", handlePointerDown);
  }, [active, onOutsideClick, refs]);
}
