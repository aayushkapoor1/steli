"""In-memory data store. Replace with a real database later."""

import random
from datetime import datetime, timedelta, timezone

import bcrypt


class Store:
    def __init__(self):
        self.users: dict[int, dict] = {} 
        self.usernames: dict[str, int] = {}         
        self.spots: dict[int, dict] = {}
        self.spot_names: dict[str, int] = {}  
        self.rankings: dict[int, dict] = {}
        self.user_rankings: dict[int, list[int]] = {} 
        self._next_user_id = 1
        self._next_spot_id = 1
        self._next_ranking_id = 1

    # ── Users ──────────────────────────────────────────────────────

    def create_user(self, username: str, password: str, first_name: str, last_name: str):
        if username.lower() in self.usernames:
            return None
        uid = self._next_user_id
        self._next_user_id += 1
        user = {
            "id": uid,
            "username": username,
            "password_hash": bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode(),
            "first_name": first_name,
            "last_name": last_name,
        }
        self.users[uid] = user
        self.usernames[username.lower()] = uid
        self.user_rankings[uid] = []
        return user

    def get_user_by_username(self, username: str):
        uid = self.usernames.get(username.lower())
        return self.users.get(uid) if uid else None

    # ── Spots ──────────────────────────────────────────────────────

    def get_or_create_spot(self, name: str, category: str = ""):
        key = name.lower().strip()
        if key in self.spot_names:
            spot = self.spots[self.spot_names[key]]
            # Update category if provided and spot doesn't have one yet
            if category and not spot.get("category"):
                spot["category"] = category
            return spot
        sid = self._next_spot_id
        self._next_spot_id += 1
        spot = {"id": sid, "name": name.strip(), "category": category}
        self.spots[sid] = spot
        self.spot_names[key] = sid
        return spot

    # ── Rankings ───────────────────────────────────────────────────

    # Ranking ranges: Bad / Okay / Good
    BAD_MAX = 10.0 / 3
    OKAY_MAX = 20.0 / 3

    @staticmethod
    def score_to_tier(score: float) -> str:
        """Convert a numeric score to a letter tier."""
        if score >= 9.0:
            return "S"
        elif score >= 8.0:
            return "A"
        elif score >= 7.0:
            return "B"
        elif score >= 6.0:
            return "C"
        elif score >= 5.0:
            return "D"
        return "F"

    @classmethod
    def score_to_rating(cls, score: float) -> str:
        """Ranking ranges: Bad / Okay / Good"""
        if score < cls.BAD_MAX:
            return "bad"
        if score < cls.OKAY_MAX:
            return "okay"
        return "good"

    def set_rankings(self, user_id: int, ranked_items: list[dict]):
        """Replace the full ranked list for a user.

        Each item: {"spot_name": str, "score": float, "notes": str, "photo_url": str, "category": str}
        Items are in order (index 0 = rank 1).
        """
        # Remember which spots they had before (normalized names for comparison)
        old_spot_names = set()
        for rid in self.user_rankings.get(user_id, []):
            r = self.rankings.get(rid)
            if r:
                s = self.spots.get(r["spot_id"])
                if s:
                    old_spot_names.add(s["name"].lower().strip())

        # Remove old rankings for this user
        for rid in self.user_rankings.get(user_id, []):
            self.rankings.pop(rid, None)
        self.user_rankings[user_id] = []

        now_iso = datetime.now(timezone.utc).isoformat()

        for i, item in enumerate(ranked_items):
            spot = self.get_or_create_spot(item["spot_name"], item.get("category", ""))
            rid = self._next_ranking_id
            self._next_ranking_id += 1
            score = item.get("score", 5.0)
            ranking = {
                "id": rid,
                "user_id": user_id,
                "spot_id": spot["id"],
                "rank": i + 1,
                "score": score,
                "tier": self.score_to_tier(score),
                "notes": item.get("notes", ""),
                "photo_url": item.get("photo_url", ""),
                "created_at": item.get("created_at", now_iso),
            }
            self.rankings[rid] = ranking
            self.user_rankings[user_id].append(rid)

        return self.get_user_rankings(user_id)

    def get_user_rankings(self, user_id: int):
        results = []
        for rid in self.user_rankings.get(user_id, []):
            r = self.rankings[rid]
            spot = self.spots[r["spot_id"]]
            out = {**r, "spot": spot}
            out["rating"] = self.score_to_rating(r["score"])
            results.append(out)
        return results

    def ranked_count(self, user_id: int) -> int:
        return len(self.user_rankings.get(user_id, []))

    # ── Pairwise Matchups (Rank screen) ────────────────────────────

    def get_matchup(self, user_id: int):
        """Return two random spots for the user to compare."""
        spot_list = list(self.spots.values())
        if len(spot_list) < 2:
            return None
        pair = random.sample(spot_list, 2)
        return {"spot_a": pair[0], "spot_b": pair[1]}


store = Store()


def _seed():
    """Populate the store with data matching the app mockups."""

    now = datetime.now(timezone.utc)

    # ── Study Spots (pre-create with categories) ───────────────────
    spots_data = [
        ("Dana Porter Library", "Library"),
        ("SLC Silent Study", "Student Center"),
        ("DC Library 2nd Floor", "Library"),
        ("QNC Study Lounge", "Academic Building"),
        ("E7 Coffee Corner", "Academic Building"),
        ("MC Comfy Lounge", "Academic Building"),
        ("PAC Study Room", "Recreation"),
        ("DP 3rd Floor Quiet Zone", "Library"),
    ]
    for name, category in spots_data:
        store.get_or_create_spot(name, category)

    # ── Users (all passwords are "password") ───────────────────────
    users_data = [
        ("alex_zhang", "Alex", "Zhang"),       # 1 - main profile user
    ]

    created = []
    for username, first, last in users_data:
        user = store.create_user(username, "password", first, last)
        created.append(user)

    # ── Rankings per user ──────────────────────────────────────────
    # alex_zhang's profile
    store.set_rankings(created[0]["id"], [
        {"spot_name": "Dana Porter Library", "score": 9.2,
         "notes": "Quiet, great for deep work. Outlets everywhere.",
         "created_at": (now - timedelta(days=2)).isoformat()},
        {"spot_name": "DC Library 2nd Floor", "score": 8.8,
         "notes": "Hidden gem. Good natural light.",
         "created_at": (now - timedelta(days=2)).isoformat()},
        {"spot_name": "QNC Study Lounge", "score": 8.5,
         "notes": "Modern vibe, can get busy during midterms.",
         "created_at": (now - timedelta(days=3)).isoformat()},
        {"spot_name": "SLC Silent Study", "score": 7.6,
         "notes": "Decent but lacks outlets near windows.",
         "created_at": (now - timedelta(days=3)).isoformat()},
        {"spot_name": "E7 Coffee Corner", "score": 7.2,
         "notes": "Good for group work, too noisy for solo.",
         "created_at": (now - timedelta(days=4)).isoformat()},
    ])

_seed()
