"""Authentication dependencies for FastAPI routes."""

from fastapi import Depends, HTTPException, Header
from typing import Optional


def get_current_user():
    """Get the current authenticated user. For now, returns a mock user."""
    # TODO: Implement real authentication
    return {"id": 1, "username": "alex_zhang"}


def get_optional_user():
    """Get the current user if authenticated, otherwise None."""
    # TODO: Implement real authentication
    return None
