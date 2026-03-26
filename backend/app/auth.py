"""Auth dependency for protected endpoints."""

from fastapi import HTTPException, Request

from app.facade import facade


def get_current_user(request: Request):
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        raise HTTPException(
            status_code=401,
            detail={"code": "NOT_AUTHENTICATED", "message": "Not authenticated"},
        )
    token = auth[7:]
    user = facade.get_user_by_token(token)
    if user is None:
        raise HTTPException(
            status_code=401,
            detail={"code": "INVALID_TOKEN", "message": "Invalid or expired token"},
        )
    return user


def get_optional_user(request: Request):
    """Returns the current user or None (no error if unauthenticated)."""
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth[7:]
    return facade.get_user_by_token(token)
