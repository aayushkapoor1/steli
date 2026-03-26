"""Repository interfaces + thin implementations over the filesystem-backed Store."""

from __future__ import annotations

from abc import ABC, abstractmethod

from app.store import Store


class UserRepository(ABC):
    @abstractmethod
    def create_user(self, username: str, password: str, first_name: str, last_name: str) -> dict | None: ...

    @abstractmethod
    def verify_user(self, username: str, password: str) -> dict | None: ...

    @abstractmethod
    def get_user_by_username(self, username: str) -> dict | None: ...

    @abstractmethod
    def search_users(self, query: str) -> list[dict]: ...


class SessionRepository(ABC):
    @abstractmethod
    def create_token(self, user_id: int) -> str: ...

    @abstractmethod
    def get_user_by_token(self, token: str) -> dict | None: ...

    @abstractmethod
    def delete_token(self, token: str) -> None: ...


class SocialRepository(ABC):
    @abstractmethod
    def follow(self, follower_id: int, following_id: int) -> bool: ...

    @abstractmethod
    def unfollow(self, follower_id: int, following_id: int) -> None: ...

    @abstractmethod
    def is_following(self, follower_id: int, following_id: int) -> bool: ...

    @abstractmethod
    def get_followers(self, user_id: int) -> list[dict]: ...

    @abstractmethod
    def get_following(self, user_id: int) -> list[dict]: ...

    @abstractmethod
    def followers_count(self, user_id: int) -> int: ...

    @abstractmethod
    def following_count(self, user_id: int) -> int: ...


class SpotRepository(ABC):
    @abstractmethod
    def get_or_create_spot(self, name: str, category: str = "") -> dict: ...

    @abstractmethod
    def list_spots(self) -> list[dict]: ...

    @abstractmethod
    def search_spots(self, query: str) -> list[dict]: ...


class RankingRepository(ABC):
    @abstractmethod
    def set_rankings(self, user_id: int, ranked_items: list[dict]) -> list[dict]: ...

    @abstractmethod
    def get_user_rankings(self, user_id: int) -> list[dict]: ...

    @abstractmethod
    def ranked_count(self, user_id: int) -> int: ...

    @abstractmethod
    def get_feed(self, user_id: int, limit: int = 20) -> list[dict]: ...

    @abstractmethod
    def get_recent_rankings(self, limit: int = 20) -> list[dict]: ...

    @abstractmethod
    def get_matchup(self, user_id: int) -> dict | None: ...

    @abstractmethod
    def record_pairwise_result(self, user_id: int, winner_spot_name: str, loser_spot_name: str) -> dict: ...


class UserRepositoryImpl(UserRepository):
    def __init__(self, store: Store):
        self._store = store

    def create_user(self, username: str, password: str, first_name: str, last_name: str) -> dict | None:
        return self._store.create_user(username, password, first_name, last_name)

    def verify_user(self, username: str, password: str) -> dict | None:
        return self._store.verify_user(username, password)

    def get_user_by_username(self, username: str) -> dict | None:
        return self._store.get_user_by_username(username)

    def search_users(self, query: str) -> list[dict]:
        if not query:
            return list(self._store.users.values())
        return self._store.search_users(query)


class SessionRepositoryImpl(SessionRepository):
    def __init__(self, store: Store):
        self._store = store

    def create_token(self, user_id: int) -> str:
        return self._store.create_token(user_id)

    def get_user_by_token(self, token: str) -> dict | None:
        return self._store.get_user_by_token(token)

    def delete_token(self, token: str) -> None:
        return self._store.delete_token(token)


class SocialRepositoryImpl(SocialRepository):
    def __init__(self, store: Store):
        self._store = store

    def follow(self, follower_id: int, following_id: int) -> bool:
        return self._store.follow(follower_id, following_id)

    def unfollow(self, follower_id: int, following_id: int) -> None:
        return self._store.unfollow(follower_id, following_id)

    def is_following(self, follower_id: int, following_id: int) -> bool:
        return self._store.is_following(follower_id, following_id)

    def get_followers(self, user_id: int) -> list[dict]:
        return self._store.get_followers(user_id)

    def get_following(self, user_id: int) -> list[dict]:
        return self._store.get_following(user_id)

    def followers_count(self, user_id: int) -> int:
        return self._store.followers_count(user_id)

    def following_count(self, user_id: int) -> int:
        return self._store.following_count(user_id)


class SpotRepositoryImpl(SpotRepository):
    def __init__(self, store: Store):
        self._store = store

    def get_or_create_spot(self, name: str, category: str = "") -> dict:
        return self._store.get_or_create_spot(name, category)

    def list_spots(self) -> list[dict]:
        return self._store.list_spots()

    def search_spots(self, query: str) -> list[dict]:
        return self._store.search_spots(query)


class RankingRepositoryImpl(RankingRepository):
    def __init__(self, store: Store):
        self._store = store

    def set_rankings(self, user_id: int, ranked_items: list[dict]) -> list[dict]:
        return self._store.set_rankings(user_id, ranked_items)

    def get_user_rankings(self, user_id: int) -> list[dict]:
        return self._store.get_user_rankings(user_id)

    def ranked_count(self, user_id: int) -> int:
        return self._store.ranked_count(user_id)

    def get_feed(self, user_id: int, limit: int = 20) -> list[dict]:
        return self._store.get_feed(user_id, limit=limit)

    def get_recent_rankings(self, limit: int = 20) -> list[dict]:
        return self._store.get_recent_rankings(limit=limit)

    def get_matchup(self, user_id: int) -> dict | None:
        return self._store.get_matchup(user_id)

    def record_pairwise_result(self, user_id: int, winner_spot_name: str, loser_spot_name: str) -> dict:
        return self._store.record_pairwise_result(user_id, winner_spot_name, loser_spot_name)
