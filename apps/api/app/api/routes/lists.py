from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.db.session import get_db
from app.schemas.lists import ListCreate, ListRead, ListReorderPayload, ListUpdate
from app.services import lists as list_service

router = APIRouter()


@router.get("/lists", response_model=list[ListRead])
def get_lists(session: Session = Depends(get_db)) -> list[ListRead]:
    return list_service.list_lists(session)


@router.post("/lists", response_model=ListRead, status_code=status.HTTP_201_CREATED)
def create_list(payload: ListCreate, session: Session = Depends(get_db)) -> ListRead:
    return list_service.create_list(session, payload)


@router.patch("/lists/{list_id}", response_model=ListRead)
def update_list(list_id: int, payload: ListUpdate, session: Session = Depends(get_db)) -> ListRead:
    task_list = list_service.get_list_or_none(session, list_id)
    if task_list is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found.")
    return list_service.update_list(task_list, payload, session)


@router.delete("/lists/{list_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_list(list_id: int, session: Session = Depends(get_db)) -> Response:
    task_list = list_service.get_list_or_none(session, list_id)
    if task_list is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="List not found.")
    list_service.delete_list(task_list, session)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/lists/reorder", response_model=list[ListRead])
def reorder_lists(payload: ListReorderPayload, session: Session = Depends(get_db)) -> list[ListRead]:
    return list_service.reorder_list_positions(payload, session)
