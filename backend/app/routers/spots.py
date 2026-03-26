"""Study spots API routes."""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

from app.facade import facade

router = APIRouter()


class StudySpotCreate(BaseModel):
    name: str
    category: str = ""


@router.get("")
def list_spots(q: str = ""):
    if q:
        return facade.search_spots(q)
    return facade.list_spots()


@router.post("")
def create_spot(spot: StudySpotCreate):
    return facade.get_or_create_spot(name=spot.name, category=spot.category)


@router.get("/{spot_id}")
def get_spot(spot_id: int):
    for s in facade.list_spots():
        if s["id"] == spot_id:
            return s
    raise HTTPException(status_code=404, detail="Spot not found")
