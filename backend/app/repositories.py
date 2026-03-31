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

    @abstractmethod
    def update_profile_photo(self, user_id: int, photo_url: str) -> dict | None: ...

    @abstractmethod
    def update_privacy(self, user_id: int, is_public: bool) -> dict | None: ...

    @abstractmethod
    def is_profile_visible(self, target_id: int, viewer_id: int | None) -> bool: ...


class SessionRepository(ABC):
    @abstractmethod
    def create_token(self, user_id: int) -> str: ...

    @abstractmethod
    def get_user_by_token(self, token: str) -> dict | None: ...

    @abstractmethod
    def delete_token(self, token: str) -> None: ...


class SocialRepository(ABC):
    @abstractmethod
    def follow(self, follower_id: int, following_id: int) -> str: ...

    @abstractmethod
    def unfollow(self, follower_id: int, following_id: int) -> None: ...

    @abstractmethod
    def is_following(self, follower_id: int, following_id: int) -> bool: ...

    @abstractmethod
    def follow_status(self, viewer_id: int, target_id: int) -> str: ...

    @abstractmethod
    def get_pending_follow_requests(self, user_id: int) -> list[dict]: ...

    @abstractmethod
    def pending_requests_count(self, user_id: int) -> int: ...

    @abstractmethod
    def approve_follow_request(self, target_id: int, requester_id: int) -> bool: ...

    @abstractmethod
    def deny_follow_request(self, target_id: int, requester_id: int) -> bool: ...

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
    def get_recent_rankings(self, limit: int = 20, viewer_id: int | None = None) -> list[dict]: ...

    @abstractmethod
    def get_matchup(self, user_id: int) -> dict | None: ...

    @abstractmethod
    def record_pairwise_result(self, user_id: int, winner_spot_name: str, loser_spot_name: str) -> dict: ...

    @abstractmethod
    def toggle_like(self, feed_event_id: int, user_id: int) -> bool: ...

    @abstractmethod
    def unlike(self, feed_event_id: int, user_id: int) -> None: ...

    @abstractmethod
    def add_comment(self, feed_event_id: int, user_id: int, text: str) -> dict: ...

    @abstractmethod
    def get_comments(self, feed_event_id: int) -> list[dict]: ...


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

    def update_profile_photo(self, user_id: int, photo_url: str) -> dict | None:
        return self._store.update_profile_photo(user_id, photo_url)

    def update_privacy(self, user_id: int, is_public: bool) -> dict | None:
        return self._store.update_privacy(user_id, is_public)

    def is_profile_visible(self, target_id: int, viewer_id: int | None) -> bool:
        return self._store.is_profile_visible(target_id, viewer_id)


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

    def follow(self, follower_id: int, following_id: int) -> str:
        return self._store.follow(follower_id, following_id)

    def unfollow(self, follower_id: int, following_id: int) -> None:
        return self._store.unfollow(follower_id, following_id)

    def is_following(self, follower_id: int, following_id: int) -> bool:
        return self._store.is_following(follower_id, following_id)

    def follow_status(self, viewer_id: int, target_id: int) -> str:
        return self._store.follow_status(viewer_id, target_id)

    def get_pending_follow_requests(self, user_id: int) -> list[dict]:
        return self._store.get_pending_follow_requests(user_id)

    def pending_requests_count(self, user_id: int) -> int:
        return self._store.pending_requests_count(user_id)

    def approve_follow_request(self, target_id: int, requester_id: int) -> bool:
        return self._store.approve_follow_request(target_id, requester_id)

    def deny_follow_request(self, target_id: int, requester_id: int) -> bool:
        return self._store.deny_follow_request(target_id, requester_id)

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

    def get_recent_rankings(self, limit: int = 20, viewer_id: int | None = None) -> list[dict]:
        return self._store.get_recent_rankings(limit=limit, viewer_id=viewer_id)

    def get_matchup(self, user_id: int) -> dict | None:
        return self._store.get_matchup(user_id)

    def record_pairwise_result(self, user_id: int, winner_spot_name: str, loser_spot_name: str) -> dict:
        return self._store.record_pairwise_result(user_id, winner_spot_name, loser_spot_name)

    def toggle_like(self, feed_event_id: int, user_id: int) -> bool:
        return self._store.toggle_like(feed_event_id, user_id)

    def unlike(self, feed_event_id: int, user_id: int) -> None:
        return self._store.unlike(feed_event_id, user_id)

    def add_comment(self, feed_event_id: int, user_id: int, text: str) -> dict:
        comment = self._store.add_comment(feed_event_id, user_id, text)
        return self._store._comment_to_response(comment)

    def get_comments(self, feed_event_id: int) -> list[dict]:
        comments = self._store.get_comments(feed_event_id)
        return [self._store._comment_to_response(c) for c in comments]
