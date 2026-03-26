"""User profile, search, and follow routes."""

from fastapi import APIRouter, Depends, HTTPException

from app.facade import facade
from app.auth import get_current_user, get_optional_user

router = APIRouter()


def _public_user(user: dict, viewer=None):
    uid = user["id"]
    return {
        "id": uid,
        "username": user["username"],
        "first_name": user["first_name"],
        "last_name": user["last_name"],
        "followers_count": facade.followers_count(uid),
        "following_count": facade.following_count(uid),
        "ranked_count": facade.ranked_count(uid),
        "is_following": facade.is_following(viewer["id"], uid) if viewer else False,
    }


@router.get("/me")
def get_me(user=Depends(get_current_user)):
    return _public_user(user, viewer=user)


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
    if not facade.follow(user["id"], target["id"]):
        raise HTTPException(status_code=400, detail="Cannot follow yourself")
    return {"status": "ok"}


@router.delete("/{username}/follow")
def unfollow_user(username: str, user=Depends(get_current_user)):
    target = facade.get_user_by_username(username)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    facade.unfollow(user["id"], target["id"])
    return {"status": "ok"}