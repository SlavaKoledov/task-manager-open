from fastapi import APIRouter

from app.api.routes import events, lists, tasks

api_router = APIRouter()
api_router.include_router(events.router, tags=["events"])
api_router.include_router(lists.router, tags=["lists"])
api_router.include_router(tasks.router, tags=["tasks"])
