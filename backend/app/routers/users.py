"""User profile, search, and follow routes."""

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

from app.facade import facade
from app.auth import get_current_user, get_optional_user

router = APIRouter()


def _public_user(user: dict, viewer=None):
    uid = user["id"]
    viewer_id = viewer["id"] if viewer else None
    status = facade.follow_status(viewer_id, uid) if viewer_id else "none"
    return {
        "id": uid,
        "username": user["username"],
        "first_name": user["first_name"],
        "last_name": user["last_name"],
        "profile_photo_url": user.get("profile_photo_url", ""),
        "followers_count": facade.followers_count(uid),
        "following_count": facade.following_count(uid),
        "ranked_count": facade.ranked_count(uid),
        "is_following": status == "following",
        "is_public": user.get("is_public", False),
        "follow_status": status,
        "pending_requests_count": facade.pending_requests_count(uid) if viewer_id and viewer_id == uid else 0,
    }


@router.get("/me")
def get_me(user=Depends(get_current_user)):
    return _public_user(user, viewer=user)


class UpdatePhotoRequest(BaseModel):
    photo_url: str


@router.put("/me/photo")
def update_profile_photo(req: UpdatePhotoRequest, user=Depends(get_current_user)):
    updated = facade.update_profile_photo(user["id"], req.photo_url.strip())
    if updated is None:
        raise HTTPException(status_code=404, detail="User not found")
    return _public_user(updated, viewer=updated)


class UpdatePrivacyRequest(BaseModel):
    is_public: bool


@router.put("/me/privacy")
def update_privacy(req: UpdatePrivacyRequest, user=Depends(get_current_user)):
    updated = facade.update_privacy(user["id"], req.is_public)
    if updated is None:
        raise HTTPException(status_code=404, detail="User not found")
    return _public_user(updated, viewer=updated)


@router.get("/search")
def search_users(q: str = "", user=Depends(get_optional_user)):
    results = facade.search_users(q)
    return [_public_user(u, viewer=user) for u in results]


@router.get("/{username}")
def get_user(username: str, user=Depends(get_optional_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    return _public_user(target, viewer=user)


@router.get("/{username}/followers")
def get_followers(username: str, user=Depends(get_optional_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    return [_public_user(u, viewer=user) for u in facade.get_followers(target["id"])]


@router.get("/{username}/following")
def get_following(username: str, user=Depends(get_optional_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    return [_public_user(u, viewer=user) for u in facade.get_following(target["id"])]


@router.post("/{username}/follow")
def follow_user(username: str, user=Depends(get_current_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    result = facade.follow(user["id"], target["id"])
    if result == "self":
        raise HTTPException(status_code=400, detail="Cannot follow yourself")
    return {"status": result}


@router.delete("/{username}/follow")
def unfollow_user(username: str, user=Depends(get_current_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    facade.unfollow(user["id"], target["id"])
    return {"status": "ok"}


@router.get("/me/follow-requests")
def get_follow_requests(user=Depends(get_current_user)):
    requests = facade.get_pending_follow_requests(user["id"])
    return [_public_user(u, viewer=user) for u in requests]


@router.post("/me/follow-requests/{username}/approve")
def approve_follow_request(username: str, user=Depends(get_current_user)):
    requester = facade.get_user_by_username(username)
    if requester is None:
        raise HTTPException(status_code=404, detail="User not found")
    if not facade.approve_follow_request(user["id"], requester["id"]):
        raise HTTPException(status_code=404, detail="No pending request from this user")
    return {"status": "ok"}


@router.delete("/me/follow-requests/{username}/deny")
def deny_follow_request(username: str, user=Depends(get_current_user)):
    requester = facade.get_user_by_username(username)
    if requester is None:
        raise HTTPException(status_code=404, detail="User not found")
    if not facade.deny_follow_request(user["id"], requester["id"]):
        raise HTTPException(status_code=404, detail="No pending request from this user")
    return {"status": "ok"}