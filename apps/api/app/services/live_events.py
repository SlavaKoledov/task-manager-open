from __future__ import annotations

import itertools
import queue
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterator

from app.schemas.events import LiveEventRead


@dataclass(slots=True)
class _Subscriber:
    queue: queue.Queue[LiveEventRead]


class LiveEventBroker:
    def __init__(self) -> None:
        self._subscribers: dict[int, _Subscriber] = {}
        self._next_subscriber_id = itertools.count(1)
        self._next_version = itertools.count(1)
        self._lock = threading.Lock()

    def subscribe(self) -> tuple[int, queue.Queue[LiveEventRead]]:
        subscriber_id = next(self._next_subscriber_id)
        subscriber_queue: queue.Queue[LiveEventRead] = queue.Queue(maxsize=64)

        with self._lock:
            self._subscribers[subscriber_id] = _Subscriber(queue=subscriber_queue)

        return subscriber_id, subscriber_queue

    def unsubscribe(self, subscriber_id: int) -> None:
        with self._lock:
            self._subscribers.pop(subscriber_id, None)

    def publish(self, entity_type: str, entity_ids: list[int] | tuple[int, ...] | set[int]) -> LiveEventRead:
        event = LiveEventRead(
            version=next(self._next_version),
            entity_type=entity_type,  # type: ignore[arg-type]
            entity_ids=sorted({entity_id for entity_id in entity_ids if entity_id > 0}),
            changed_at=datetime.now(timezone.utc),
        )

        with self._lock:
            subscribers = list(self._subscribers.values())

        for subscriber in subscribers:
            if subscriber.queue.full():
                try:
                    subscriber.queue.get_nowait()
                except queue.Empty:
                    pass

            try:
                subscriber.queue.put_nowait(event)
            except queue.Full:
                # Drop the newest event for this slow subscriber rather than blocking mutations.
                continue

        return event


live_event_broker = LiveEventBroker()


def publish_task_event(*task_ids: int) -> LiveEventRead:
    return live_event_broker.publish("task", task_ids)


def publish_list_event(*list_ids: int) -> LiveEventRead:
    return live_event_broker.publish("list", list_ids)
