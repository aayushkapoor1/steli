"""Filesystem-backed store with in-memory cache."""

import json
import os
import random
import secrets
import threading
from datetime import datetime, timedelta, timezone
from pathlib import Path

import bcrypt


class Store:
    def __init__(self):
        self._lock = threading.RLock()
        self._data_dir = Path(os.getenv("STELI_DATA_DIR", Path(__file__).resolve().parents[1] / "data"))
        self._data_file = self._data_dir / "store.json"
        self._schema_version = 1
        self._session_ttl_seconds = int(os.getenv("STELI_SESSION_TTL_SECONDS", str(60 * 60 * 24)))
        self.users: dict[int, dict] = {}
        self.usernames: dict[str, int] = {}  # lowercase username -> user id
        # token -> {"user_id": int, "expires_at": iso-string}
        self.tokens: dict[str, dict] = {}
        self.follows: set[tuple[int, int]] = set()  # (follower_id, following_id)
        self.spots: dict[int, dict] = {}
        self.spot_names: dict[str, int] = {}  # lowercase name -> spot id
        self.rankings: dict[int, dict] = {}
        self.user_rankings: dict[int, list[int]] = {}  # user_id -> [ranking_ids in rank order]
        self.feed_events: list[dict] = []  # one entry per ranking action (new or reranked)
        self._next_user_id = 1
        self._next_spot_id = 1
        self._next_ranking_id = 1
        self._next_feed_event_id = 1
        self._load()

    def _snapshot(self) -> dict:
        return {
            "schema_version": self._schema_version,
            "users": self.users,
            "usernames": self.usernames,
            "tokens": self.tokens,
            "follows": [list(pair) for pair in sorted(self.follows)],
            "spots": self.spots,
            "spot_names": self.spot_names,
            "rankings": self.rankings,
            "user_rankings": self.user_rankings,
            "feed_events": self.feed_events,
            "next_ids": {
                "user": self._next_user_id,
                "spot": self._next_spot_id,
                "ranking": self._next_ranking_id,
                "feed_event": self._next_feed_event_id,
            },
        }

    def _persist(self):
        self._data_dir.mkdir(parents=True, exist_ok=True)
        tmp_path = self._data_file.with_suffix(".tmp")
        with tmp_path.open("w", encoding="utf-8") as f:
            json.dump(self._snapshot(), f, ensure_ascii=True, separators=(",", ":"))
            f.flush()
            os.fsync(f.fileno())
        tmp_path.replace(self._data_file)

    @staticmethod
    def _to_int_keyed_dict(raw: dict) -> dict:
        return {int(k): v for k, v in raw.items()}

    def _load(self):
        if not self._data_file.exists():
            return
        with self._data_file.open("r", encoding="utf-8") as f:
            data = json.load(f)

        file_schema = int(data.get("schema_version", 0) or 0)
        if file_schema == 0:
            # v0 files were written before schema_version existed; treat as compatible with v1.
            data["schema_version"] = self._schema_version
        elif file_schema != self._schema_version:
            raise RuntimeError(
                f"Unsupported store schema_version={file_schema} (expected {self._schema_version}). "
                "Delete the data file or implement a migration."
            )

        self.users = self._to_int_keyed_dict(data.get("users", {}))
        self.usernames = dict(data.get("usernames", {}))
        raw_tokens = data.get("tokens", {})
        # Backwards compat: older versions stored token -> user_id (int).
        # Treat those tokens as re-issued at load-time.
        now = datetime.now(timezone.utc)
        tokens: dict[str, dict] = {}
        for token, val in raw_tokens.items():
            if isinstance(val, int):
                tokens[token] = {
                    "user_id": val,
                    "expires_at": (now + timedelta(seconds=self._session_ttl_seconds)).isoformat(),
                }
            elif isinstance(val, dict):
                tokens[token] = val
            else:
                # Unknown shape: drop it.
                continue
        self.tokens = tokens
        self.follows = {tuple(pair) for pair in data.get("follows", [])}
        self.spots = self._to_int_keyed_dict(data.get("spots", {}))
        self.spot_names = dict(data.get("spot_names", {}))
        self.rankings = self._to_int_keyed_dict(data.get("rankings", {}))
        self.user_rankings = {
            int(k): [int(v) for v in values]
            for k, values in data.get("user_rankings", {}).items()
        }
        self.feed_events = list(data.get("feed_events", []))

        next_ids = data.get("next_ids", {})
        self._next_user_id = int(next_ids.get("user", max(self.users.keys(), default=0) + 1))
        self._next_spot_id = int(next_ids.get("spot", max(self.spots.keys(), default=0) + 1))
        self._next_ranking_id = int(next_ids.get("ranking", max(self.rankings.keys(), default=0) + 1))
        self._next_feed_event_id = int(next_ids.get("feed_event", len(self.feed_events) + 1))

        # Drop expired sessions after loading.
        self._cleanup_expired_tokens()

    def _cleanup_expired_tokens(self):
        now = datetime.now(timezone.utc)
        expired = []
        for token, meta in self.tokens.items():
            expires_at = meta.get("expires_at")
            if not expires_at:
                expired.append(token)
                continue
            try:
                exp_dt = datetime.fromisoformat(expires_at)
                if exp_dt.tzinfo is None:
                    exp_dt = exp_dt.replace(tzinfo=timezone.utc)
                if exp_dt <= now:
                    expired.append(token)
            except Exception:
                expired.append(token)
        for token in expired:
            self.tokens.pop(token, None)

    # ── Users ──────────────────────────────────────────────────────

    def create_user(self, username: str, password: str, first_name: str, last_name: str):
        with self._lock:
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
            self._persist()
            return user

    def verify_user(self, username: str, password: str):
        uid = self.usernames.get(username.lower())
        if uid is None:
            return None
        user = self.users[uid]
        if bcrypt.checkpw(password.encode(), user["password_hash"].encode()):
            return user
        return None

    def get_user_by_username(self, username: str):
        uid = self.usernames.get(username.lower())
        return self.users.get(uid) if uid else None

    def search_users(self, query: str):
        q = query.lower()
        # Simple in-memory search for autocomplete.
        # Matches username and name parts (first/last) case-insensitively.
        return [
            u
            for u in self.users.values()
            if q in u["username"].lower()
            or q in u["first_name"].lower()
            or q in u["last_name"].lower()
            or q in f"{u['first_name']} {u['last_name']}".lower()
        ]

    # ── Tokens ─────────────────────────────────────────────────────

    def create_token(self, user_id: int) -> str:
        with self._lock:
            token = secrets.token_hex(32)
            expires_at = (datetime.now(timezone.utc) + timedelta(seconds=self._session_ttl_seconds)).isoformat()
            self.tokens[token] = {"user_id": user_id, "expires_at": expires_at}
            self._persist()
            return token

    def get_user_by_token(self, token: str):
        with self._lock:
            meta = self.tokens.get(token)
            if not meta:
                return None
            try:
                exp = meta.get("expires_at")
                if exp:
                    exp_dt = datetime.fromisoformat(exp)
                    if exp_dt.tzinfo is None:
                        exp_dt = exp_dt.replace(tzinfo=timezone.utc)
                    if exp_dt <= datetime.now(timezone.utc):
                        self.tokens.pop(token, None)
                        self._persist()
                        return None
            except Exception:
                self.tokens.pop(token, None)
                self._persist()
                return None
            uid = meta.get("user_id")
            return self.users.get(uid) if uid else None

    def delete_token(self, token: str):
        with self._lock:
            self.tokens.pop(token, None)
            self._persist()

    # ── Follows ────────────────────────────────────────────────────

    def follow(self, follower_id: int, following_id: int) -> bool:
        with self._lock:
            if follower_id == following_id:
                return False
            self.follows.add((follower_id, following_id))
            self._persist()
            return True

    def unfollow(self, follower_id: int, following_id: int):
        with self._lock:
            self.follows.discard((follower_id, following_id))
            self._persist()

    def is_following(self, follower_id: int, following_id: int) -> bool:
        return (follower_id, following_id) in self.follows

    def get_followers(self, user_id: int):
        return [self.users[fid] for fid, tid in self.follows if tid == user_id]

    def get_following(self, user_id: int):
        return [self.users[tid] for fid, tid in self.follows if fid == user_id]

    def followers_count(self, user_id: int) -> int:
        return sum(1 for _, tid in self.follows if tid == user_id)

    def following_count(self, user_id: int) -> int:
        return sum(1 for fid, _ in self.follows if fid == user_id)

    # ── Spots ──────────────────────────────────────────────────────

    def _get_or_create_spot_unlocked(self, name: str, category: str = ""):
        """Must be called with `self._lock` held."""
        key = name.lower().strip()
        if key in self.spot_names:
            spot = self.spots[self.spot_names[key]]
            # Update category if provided and spot doesn't have one yet
            if category and not spot.get("category"):
                spot["category"] = category
                self._persist()
            return spot
        sid = self._next_spot_id
        self._next_spot_id += 1
        spot = {"id": sid, "name": name.strip(), "category": category}
        self.spots[sid] = spot
        self.spot_names[key] = sid
        self._persist()
        return spot

    def get_or_create_spot(self, name: str, category: str = ""):
        with self._lock:
            return self._get_or_create_spot_unlocked(name, category)

    def list_spots(self):
        return list(self.spots.values())

    def search_spots(self, query: str):
        q = query.lower()
        return [s for s in self.spots.values() if q in s["name"].lower()]

    # ── Rankings ───────────────────────────────────────────────────

    # Beli-style ranges: Bad / Okay / Good (same boundaries as client).
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
        """Beli-style: bad / okay / good (for same-range comparison)."""
        if score < cls.BAD_MAX:
            return "bad"
        if score < cls.OKAY_MAX:
            return "okay"
        return "good"

    def set_rankings(self, user_id: int, ranked_items: list[dict]):
        """Replace the full ranked list for a user.

        Each item: {"spot_name": str, "score": float, "notes": str, "photo_url": str, "category": str}
        Items are in order (index 0 = rank 1).

        FEED EVENT STUFF NOT ADDED IN PROJECT YET
        Only adds a feed event when the user adds at least one *new* spot (not when they just reorder or scores change).
        """
        with self._lock:
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
                spot = self._get_or_create_spot_unlocked(item["spot_name"], item.get("category", ""))
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

            ####### Not in project yet ######
            # Feed event only when they explicitly added at least one new spot (not reorder/score-only changes)
            if ranked_items:
                new_spot_names = {item["spot_name"].lower().strip() for item in ranked_items}
                added_names = new_spot_names - old_spot_names
                if added_names:
                    # Find the first newly added spot in their list for the feed card
                    for item in ranked_items:
                        if item["spot_name"].lower().strip() in added_names:
                            spot = self._get_or_create_spot_unlocked(item["spot_name"], item.get("category", ""))
                            score = item.get("score", 5.0)
                            event_id = self._next_feed_event_id
                            self._next_feed_event_id += 1
                            self.feed_events.append({
                                "id": event_id,
                                "user_id": user_id,
                                "created_at": now_iso,
                                "kind": "new",
                                "spot": {"id": spot["id"], "name": spot["name"], "category": spot.get("category", "")},
                                "score": score,
                                "tier": self.score_to_tier(score),
                            })
                            break
                    if len(self.feed_events) > 500:
                        self.feed_events = self.feed_events[-500:]
            ####### End of not in project yet ######
            self._persist()
            return self.get_user_rankings(user_id)

    def get_user_rankings(self, user_id: int):
        results = []
        for rid in self.user_rankings.get(user_id, []):
            r = self.rankings[rid]
            spot = self.spots[r["spot_id"]]
            out = {**r, "spot": spot}
            out["rating"] = self.score_to_rating(r["score"])
            results.append(out)
        # Requirement: profile ranked list ordered by score (desc).
        results.sort(key=lambda x: x["score"], reverse=True)
        for i, item in enumerate(results, start=1):
            item["rank"] = i
        return results

    def ranked_count(self, user_id: int) -> int:
        return len(self.user_rankings.get(user_id, []))

    ####### Not in project yet ######
    def _feed_events_to_items(self, events: list[dict]):
        """Convert feed event dicts to the same shape as before (id, user, spot, score, tier, created_at)."""
        out = []
        for e in events:
            user = self.users[e["user_id"]]
            out.append({
                "id": e["id"],
                "user": {
                    "id": user["id"],
                    "username": user["username"],
                    "first_name": user["first_name"],
                    "last_name": user["last_name"],
                },
                "spot": e["spot"],
                "rank": 1,
                "score": e["score"],
                "tier": e["tier"],
                "notes": "",
                "photo_url": "",
                "created_at": e["created_at"],
                "kind": e["kind"],
            })
        return out

    def get_feed(self, user_id: int, limit: int = 20):
        """Feed from users you follow: sorted by recency (newest first)."""
        following_ids = {tid for fid, tid in self.follows if fid == user_id}
        events = [
            e
            for e in self.feed_events
            if e["user_id"] in following_ids and e.get("kind", "new") == "new"
        ]
        events.sort(key=lambda x: x["created_at"], reverse=True)
        return self._feed_events_to_items(events[:limit])

    def get_recent_rankings(self, limit: int = 20):
        """Recent feed: sorted by recency (newest first). One entry per new ranking action."""
        events = [e for e in self.feed_events if e.get("kind", "new") == "new"]
        events = sorted(events, key=lambda x: x["created_at"], reverse=True)[:limit]
        return self._feed_events_to_items(events)
    ####### End of not in project yet ######

    def record_pairwise_result(self, user_id: int, winner_spot_name: str, loser_spot_name: str):
        """Record a pairwise comparison outcome as a feed event (does not change rankings)."""
        with self._lock:
            winner = self._get_or_create_spot_unlocked(winner_spot_name, "")
            loser = self._get_or_create_spot_unlocked(loser_spot_name, "")
            now_iso = datetime.now(timezone.utc).isoformat()
            event_id = self._next_feed_event_id
            self._next_feed_event_id += 1
            self.feed_events.append(
                {
                    "id": event_id,
                    "user_id": user_id,
                    "created_at": now_iso,
                    "kind": "compare",
                    "spot": {"id": winner["id"], "name": winner["name"], "category": winner.get("category", "")},
                    "score": 0.0,
                    "tier": "—",
                    "meta": {"loser": {"id": loser["id"], "name": loser["name"]}},
                }
            )
            if len(self.feed_events) > 500:
                self.feed_events = self.feed_events[-500:]
            self._persist()
            return {"winner": winner, "loser": loser}

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

    if store.users:
        return
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
        ("sarah_chen", "Sarah", "Chen"),        # 2
        ("mike_j", "Mike", "Johnson"),          # 3
        ("alex_kim", "Alex", "Kim"),            # 4
        ("emma_w", "Emma", "Watson"),           # 5
        ("jordan_b", "Jordan", "Brown"),        # 6
        ("priya_s", "Priya", "Sharma"),         # 7
        ("chris_l", "Chris", "Lee"),            # 8
    ]

    created = []
    for username, first, last in users_data:
        user = store.create_user(username, "password", first, last)
        created.append(user)

    # ── Rankings per user ──────────────────────────────────────────
    # alex_zhang's profile (matches Profile screen mockup)
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

    # sarah_chen (appears in Home feed)
    store.set_rankings(created[1]["id"], [
        {"spot_name": "Dana Porter Library", "score": 8.5,
         "notes": "Love the atmosphere here.",
         "created_at": (now - timedelta(hours=2)).isoformat()},
        {"spot_name": "DP 3rd Floor Quiet Zone", "score": 8.0,
         "notes": "Perfect for exam prep.",
         "created_at": (now - timedelta(days=1)).isoformat()},
        {"spot_name": "MC Comfy Lounge", "score": 7.0,
         "notes": "Comfortable but can be distracting.",
         "created_at": (now - timedelta(days=2)).isoformat()},
    ])

    # mike_j (appears in Home feed)
    store.set_rankings(created[2]["id"], [
        {"spot_name": "SLC Silent Study", "score": 7.2,
         "notes": "Solid study spot overall.",
         "created_at": (now - timedelta(hours=5)).isoformat()},
        {"spot_name": "PAC Study Room", "score": 6.5,
         "notes": "Good when the library is full.",
         "created_at": (now - timedelta(days=2)).isoformat()},
    ])

    # alex_kim (appears in Home feed)
    store.set_rankings(created[3]["id"], [
        {"spot_name": "DC Library 2nd Floor", "score": 9.1,
         "notes": "My go-to spot. Always productive here.",
         "created_at": (now - timedelta(hours=8)).isoformat()},
        {"spot_name": "Dana Porter Library", "score": 8.7,
         "notes": "Classic choice, never disappoints.",
         "created_at": (now - timedelta(days=1)).isoformat()},
        {"spot_name": "QNC Study Lounge", "score": 7.5,
         "notes": "Nice but gets crowded.",
         "created_at": (now - timedelta(days=3)).isoformat()},
    ])

    # emma_w (appears in Home feed)
    store.set_rankings(created[4]["id"], [
        {"spot_name": "QNC Study Lounge", "score": 6.8,
         "notes": "Not bad, prefer quieter places though.",
         "created_at": (now - timedelta(days=1)).isoformat()},
        {"spot_name": "MC Comfy Lounge", "score": 7.8,
         "notes": "Great couches for long sessions.",
         "created_at": (now - timedelta(days=2)).isoformat()},
    ])

    # jordan_b (appears in Home feed)
    store.set_rankings(created[5]["id"], [
        {"spot_name": "E7 Coffee Corner", "score": 7.9,
         "notes": "Good coffee keeps me going.",
         "created_at": (now - timedelta(days=1)).isoformat()},
        {"spot_name": "SLC Silent Study", "score": 8.2,
         "notes": "Underrated spot.",
         "created_at": (now - timedelta(days=2)).isoformat()},
        {"spot_name": "Dana Porter Library", "score": 9.0,
         "notes": "The GOAT study spot.",
         "created_at": (now - timedelta(days=3)).isoformat()},
    ])

    # priya_s
    store.set_rankings(created[6]["id"], [
        {"spot_name": "DP 3rd Floor Quiet Zone", "score": 9.3,
         "notes": "Absolute silence. Perfect.",
         "created_at": (now - timedelta(days=1)).isoformat()},
        {"spot_name": "Dana Porter Library", "score": 8.9,
         "notes": "Close second to the quiet zone.",
         "created_at": (now - timedelta(days=2)).isoformat()},
    ])

    # chris_l
    store.set_rankings(created[7]["id"], [
        {"spot_name": "E7 Coffee Corner", "score": 8.1,
         "notes": "Best espresso on campus.",
         "created_at": (now - timedelta(hours=6)).isoformat()},
        {"spot_name": "PAC Study Room", "score": 7.4,
         "notes": "Quiet enough, good tables.",
         "created_at": (now - timedelta(days=1)).isoformat()},
        {"spot_name": "MC Comfy Lounge", "score": 6.9,
         "notes": "Okay for group projects.",
         "created_at": (now - timedelta(days=3)).isoformat()},
    ])

    # ── Follows (social graph) ─────────────────────────────────────
    # alex_zhang follows everyone (so the home feed shows all users)
    for i in range(1, len(created)):
        store.follow(created[0]["id"], created[i]["id"])

    # Build out the rest of the social graph
    follow_pairs = [
        # sarah follows alex, mike, emma
        (2, 1), (2, 3), (2, 5),
        # mike follows alex, sarah, jordan
        (3, 1), (3, 2), (3, 6),
        # alex_kim follows alex, sarah, priya
        (4, 1), (4, 2), (4, 7),
        # emma follows alex, jordan, chris
        (5, 1), (5, 6), (5, 8),
        # jordan follows alex, mike, emma, priya
        (6, 1), (6, 3), (6, 5), (6, 7),
        # priya follows alex, sarah, alex_kim
        (7, 1), (7, 2), (7, 4),
        # chris follows alex, mike, jordan
        (8, 1), (8, 3), (8, 6),
    ]
    for follower, following in follow_pairs:
        store.follow(follower, following)

    # Extra follows to get alex_zhang closer to 47 followers / 32 following
    # alex_zhang already follows 7 people; add more dummy follow relationships
    # to bump the numbers shown in the mockup (47 followers, 32 following, 23 ranked)
    # Since we only have 8 users, the exact counts won't match, but the structure is right.


_seed()
