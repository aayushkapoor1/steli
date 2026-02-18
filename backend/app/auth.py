"""Auth dependency for protected endpoints."""

from fastapi import HTTPException, Request

from app.store import store


def get_current_user(request: Request):
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Not authenticated")
    token = auth[7:]
    user = store.get_user_by_token(token)
    if user is None:
        raise HTTPException(status_code=401, detail="Invalid token")
    return user


def get_optional_user(request: Request):
    """Returns the current user or None (no error if unauthenticated)."""
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth[7:]
    return store.get_user_by_token(token)
