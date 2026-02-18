"""Authentication routes: register, login, logout."""

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel

from app.store import store
from app.auth import get_current_user

router = APIRouter()


class RegisterRequest(BaseModel):
    username: str
    password: str
    first_name: str
    last_name: str


class LoginRequest(BaseModel):
    username: str
    password: str


def _user_response(user: dict, token: str):
    return {
        "token": token,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "first_name": user["first_name"],
            "last_name": user["last_name"],
            "followers_count": store.followers_count(user["id"]),
            "following_count": store.following_count(user["id"]),
        },
    }


@router.post("/register")
def register(req: RegisterRequest):
    username = req.username.strip()
    if not username:
        raise HTTPException(status_code=400, detail="Username is required")
    if len(req.password) <= 8:
        raise HTTPException(
            status_code=400,
            detail="Password must be longer than 8 characters",
        )
    user = store.create_user(username, req.password, req.first_name.strip(), req.last_name.strip())
    if user is None:
        raise HTTPException(status_code=400, detail="Username already taken")
    token = store.create_token(user["id"])
    return _user_response(user, token)


@router.post("/login")
def login(req: LoginRequest):
    user = store.verify_user(req.username, req.password)
    if user is None:
        raise HTTPException(status_code=401, detail="Invalid username or password")
    token = store.create_token(user["id"])
    return _user_response(user, token)


@router.post("/logout")
def logout(request: Request, user=Depends(get_current_user)):
    auth = request.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        store.delete_token(auth[7:])
    return {"status": "ok"}