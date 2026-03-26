from __future__ import annotations

import asyncio
import json
import queue
from collections.abc import AsyncIterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.schemas.events import LiveEventRead
from app.services.live_events import live_event_broker

router = APIRouter()


def _format_event(event: LiveEventRead) -> bytes:
    payload = event.model_dump(mode="json")
    return f"event: change\ndata: {json.dumps(payload)}\n\n".encode("utf-8")


async def _event_stream() -> AsyncIterator[bytes]:
    subscriber_id, subscriber_queue = live_event_broker.subscribe()

    try:
        yield b": connected\n\n"

        while True:
            try:
                event = await asyncio.to_thread(subscriber_queue.get, True, 20)
            except queue.Empty:
                yield b": keep-alive\n\n"
                continue

            yield _format_event(event)
    finally:
        live_event_broker.unsubscribe(subscriber_id)


@router.get("/events")
async def stream_events() -> StreamingResponse:
    return StreamingResponse(
        _event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
