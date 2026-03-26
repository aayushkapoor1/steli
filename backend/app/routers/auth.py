"""Authentication routes: register, login, logout."""

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel

from app.facade import facade
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
            "followers_count": facade.followers_count(user["id"]),
            "following_count": facade.following_count(user["id"]),
        },
    }


@router.post("/register")
def register(req: RegisterRequest):
    username = req.username.strip()
    if not username:
        raise HTTPException(
            status_code=400,
            detail={"code": "USERNAME_REQUIRED", "message": "Username is required"},
        )
    if len(req.password) <= 8:
        raise HTTPException(
            status_code=400,
            detail={"code": "PASSWORD_TOO_SHORT", "message": "Password must be longer than 8 characters"},
        )
    user = facade.create_user(username, req.password, req.first_name.strip(), req.last_name.strip())
    if user is None:
        raise HTTPException(
            status_code=400,
            detail={"code": "USERNAME_TAKEN", "message": "Username is already taken"},
        )
    token = facade.create_token(user["id"])
    return _user_response(user, token)


@router.post("/login")
def login(req: LoginRequest):
    username = req.username.strip()
    if not username:
        raise HTTPException(
            status_code=400,
            detail={"code": "USERNAME_REQUIRED", "message": "Username is required"},
        )
    user = facade.verify_user(username, req.password)
    if user is None:
        raise HTTPException(
            status_code=401,
            detail={"code": "INVALID_CREDENTIALS", "message": "Invalid username or password"},
        )
    token = facade.create_token(user["id"])
    return _user_response(user, token)


@router.post("/logout")
def logout(request: Request, user=Depends(get_current_user)):
    auth = request.headers.get("Authorization", "")
    if auth.startswith("Bearer "):
        facade.delete_token(auth[7:])
    return {"status": "ok"}