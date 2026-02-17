"""Ranking routes: create/update rankings, get feed, pairwise matchups."""

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.store import store
from app.auth import get_current_user, get_optional_user

router = APIRouter()


class RankedItem(BaseModel):
    spot_name: str
    score: float = 5.0
    notes: str = ""
    photo_url: str = ""


class SetRankingsRequest(BaseModel):
    """Full ordered list of ranked spots (index 0 = rank 1)."""
    rankings: list[RankedItem]


@router.put("")
def set_rankings(req: SetRankingsRequest, user=Depends(get_current_user)):
    """Replace the user's entire ranked list."""
    items = [item.model_dump() for item in req.rankings]
    results = store.set_rankings(user["id"], items)
    return results


@router.get("/matchup")
def get_matchup(user=Depends(get_current_user)):
    """Get two random spots for pairwise comparison (Rank screen)."""
    result = store.get_matchup(user["id"])
    if result is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="Not enough spots for a matchup")
    return result


@router.get("/user/{username}")
def get_user_rankings(username: str, user=Depends(get_optional_user)):
    target = store.get_user_by_username(username)
    if target is None:
        from fastapi import HTTPException
        raise HTTPException(status_code=404, detail="User not found")
    return store.get_user_rankings(target["id"])
