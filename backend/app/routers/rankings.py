"""Ranking routes: create/update rankings, get feed, pairwise matchups."""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.facade import facade
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


class CompareSpotsRequest(BaseModel):
    winner_spot_name: str
    loser_spot_name: str


@router.post("/compare")
def compare_spots(req: CompareSpotsRequest, user=Depends(get_current_user)):
    """Persist a pairwise comparison outcome as a lightweight feed event."""
    winner_name = req.winner_spot_name.strip()
    loser_name = req.loser_spot_name.strip()
    if not winner_name or not loser_name:
        raise HTTPException(status_code=400, detail="Both spot names are required")
    if winner_name.lower() == loser_name.lower():
        raise HTTPException(status_code=400, detail="Cannot compare a spot to itself")
    result = facade.record_pairwise_result(user["id"], winner_name, loser_name)
    return {"status": "ok", **result}


@router.put("")
def set_rankings(req: SetRankingsRequest, user=Depends(get_current_user)):
    """Replace the user's entire ranked list."""
    items = [item.model_dump() for item in req.rankings]
    results = facade.set_rankings(user["id"], items)
    return results


@router.get("/matchup")
def get_matchup(user=Depends(get_current_user)):
    """Get two random spots for pairwise comparison (Rank screen)."""
    result = facade.get_matchup(user["id"])
    if result is None:
        raise HTTPException(status_code=404, detail="Not enough spots for a matchup")
    return result


@router.get("/user/{username}")
def get_user_rankings(username: str, user=Depends(get_optional_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    viewer_id = user["id"] if user else None
    if not facade.is_profile_visible(target["id"], viewer_id):
        raise HTTPException(status_code=403, detail="This profile is private")
    return facade.get_user_rankings(target["id"])


@router.get("/feed")
def get_feed(limit: int = 20, user=Depends(get_current_user)):
    """Feed from followed users, ordered by recency."""
    return facade.get_feed(user["id"], limit=limit)


@router.get("/recent")
def get_recent_rankings(limit: int = 20, user=Depends(get_optional_user)):
    """Global recent feed: only shows items from public profiles or profiles the viewer follows."""
    viewer_id = user["id"] if user else None
    items = facade.get_recent_rankings(limit=limit * 2, viewer_id=viewer_id)
    visible = [
        item for item in items
        if facade.is_profile_visible(item["user"]["id"], viewer_id)
    ]
    return visible[:limit]


@router.post("/feed/{event_id}/like")
def toggle_like(event_id: int, user=Depends(get_current_user)):
    """Toggle like on a feed event."""
    liked = facade.toggle_like(event_id, user["id"])
    return {"liked": liked}


@router.delete("/feed/{event_id}/like")
def unlike(event_id: int, user=Depends(get_current_user)):
    """Remove like from a feed event."""
    facade.unlike(event_id, user["id"])
    return {"liked": False}


class AddCommentRequest(BaseModel):
    text: str


@router.get("/feed/{event_id}/comments")
def get_comments(event_id: int, user=Depends(get_current_user)):
    """Get all comments for a feed event."""
    return facade.get_comments(event_id)


@router.post("/feed/{event_id}/comments")
def add_comment(event_id: int, req: AddCommentRequest, user=Depends(get_current_user)):
    """Add a comment to a feed event."""
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="Comment text is required")
    return facade.add_comment(event_id, user["id"], text)
