"""Application facade: orchestrates repositories (repository + facade patterns)."""

from __future__ import annotations

from app.repositories import (
    RankingRepository,
    RankingRepositoryImpl,
    SessionRepository,
    SessionRepositoryImpl,
    SocialRepository,
    SocialRepositoryImpl,
    SpotRepository,
    SpotRepositoryImpl,
    UserRepository,
    UserRepositoryImpl,
)
from app.store import store


class SteliFacade:
    def __init__(self):
        self._users: UserRepository = UserRepositoryImpl(store)
        self._sessions: SessionRepository = SessionRepositoryImpl(store)
        self._social: SocialRepository = SocialRepositoryImpl(store)
        self._spots: SpotRepository = SpotRepositoryImpl(store)
        self._rankings: RankingRepository = RankingRepositoryImpl(store)

    # Users
    def create_user(self, username: str, password: str, first_name: str, last_name: str):
        return self._users.create_user(username, password, first_name, last_name)

    def verify_user(self, username: str, password: str):
        return self._users.verify_user(username, password)

    def get_user_by_username(self, username: str):
        return self._users.get_user_by_username(username)

    def search_users(self, query: str):
        return self._users.search_users(query)

    # Sessions
    def create_token(self, user_id: int) -> str:
        return self._sessions.create_token(user_id)

    def get_user_by_token(self, token: str):
        return self._sessions.get_user_by_token(token)

    def delete_token(self, token: str):
        return self._sessions.delete_token(token)

    # Social
    def follow(self, follower_id: int, following_id: int) -> bool:
        return self._social.follow(follower_id, following_id)

    def unfollow(self, follower_id: int, following_id: int):
        return self._social.unfollow(follower_id, following_id)

    def is_following(self, follower_id: int, following_id: int) -> bool:
        return self._social.is_following(follower_id, following_id)

    def get_followers(self, user_id: int):
        return self._social.get_followers(user_id)

    def get_following(self, user_id: int):
        return self._social.get_following(user_id)

    def followers_count(self, user_id: int) -> int:
        return self._social.followers_count(user_id)

    def following_count(self, user_id: int) -> int:
        return self._social.following_count(user_id)

    # Spots
    def get_or_create_spot(self, name: str, category: str = ""):
        return self._spots.get_or_create_spot(name, category)

    def list_spots(self):
        return self._spots.list_spots()

    def search_spots(self, query: str):
        return self._spots.search_spots(query)

    # Rankings
    def set_rankings(self, user_id: int, ranked_items: list[dict]):
        return self._rankings.set_rankings(user_id, ranked_items)

    def get_user_rankings(self, user_id: int):
        return self._rankings.get_user_rankings(user_id)

    def ranked_count(self, user_id: int) -> int:
        return self._rankings.ranked_count(user_id)

    def get_feed(self, user_id: int, limit: int = 20):
        return self._rankings.get_feed(user_id, limit=limit)

    def get_recent_rankings(self, limit: int = 20):
        return self._rankings.get_recent_rankings(limit=limit)

    def get_matchup(self, user_id: int):
        return self._rankings.get_matchup(user_id)

    def record_pairwise_result(self, user_id: int, winner_spot_name: str, loser_spot_name: str):
        return self._rankings.record_pairwise_result(user_id, winner_spot_name, loser_spot_name)


facade = SteliFacade()
