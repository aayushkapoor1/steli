"""In-memory data store. Replace with a real database later."""

import random
import secrets
from datetime import datetime, timedelta, timezone

import bcrypt


class Store:
    def __init__(self):
        self.users: dict[int, dict] = {}
        self.usernames: dict[str, int] = {}  # lowercase username -> user id
        self.tokens: dict[str, int] = {}  # token -> user id
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
        return [u for u in self.users.values() if q in u["username"].lower()]

    # ── Tokens ─────────────────────────────────────────────────────

    def create_token(self, user_id: int) -> str:
        token = secrets.token_hex(32)
        self.tokens[token] = user_id
        return token

    def get_user_by_token(self, token: str):
        uid = self.tokens.get(token)
        return self.users.get(uid) if uid else None

    def delete_token(self, token: str):
        self.tokens.pop(token, None)

    # ── Follows ────────────────────────────────────────────────────

    def follow(self, follower_id: int, following_id: int) -> bool:
        if follower_id == following_id:
            return False
        self.follows.add((follower_id, following_id))
        return True

    def unfollow(self, follower_id: int, following_id: int):
        self.follows.discard((follower_id, following_id))

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

        ####### Not in project yet ######
        # Feed event only when they explicitly added at least one new spot (not reorder/score-only changes)
        if ranked_items:
            new_spot_names = {item["spot_name"].lower().strip() for item in ranked_items}
            added_names = new_spot_names - old_spot_names
            if added_names:
                # Find the first newly added spot in their list for the feed card
                for item in ranked_items:
                    if item["spot_name"].lower().strip() in added_names:
                        spot = self.get_or_create_spot(item["spot_name"], item.get("category", ""))
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
        events = [e for e in self.feed_events if e["user_id"] in following_ids]
        events.sort(key=lambda x: x["created_at"], reverse=True)
        return self._feed_events_to_items(events[:limit])

    def get_recent_rankings(self, limit: int = 20):
        """Recent feed: sorted by recency (newest first). One entry per new ranking action."""
        events = sorted(self.feed_events, key=lambda x: x["created_at"], reverse=True)[:limit]
        return self._feed_events_to_items(events)
    ####### End of not in project yet ######

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
