package com.steli.app.data

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────────

data class RegisterRequest(
    val username: String,
    val password: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
)

data class LoginRequest(
    val username: String,
    val password: String,
)

data class AuthResponse(
    val token: String,
    val user: UserPublic,
)

// ── Users ─────────────────────────────────────────────────────────

data class UserPublic(
    val id: Int,
    val username: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("profile_photo_url") val profilePhotoUrl: String = "",
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("ranked_count") val rankedCount: Int = 0,
    @SerializedName("is_following") val isFollowing: Boolean = false,
    @SerializedName("is_public") val isPublic: Boolean = false,
    @SerializedName("follow_status") val followStatus: String = "none",
    @SerializedName("pending_requests_count") val pendingRequestsCount: Int = 0,
)

data class FollowResponse(
    val status: String,
)

data class UpdatePrivacyRequest(
    @SerializedName("is_public") val isPublic: Boolean,
)

data class UpdatePhotoRequest(
    @SerializedName("photo_url") val photoUrl: String,
)

// ── Spots ─────────────────────────────────────────────────────────

data class StudySpot(
    val id: Int,
    val name: String,
    val category: String = "",
)

data class CreateSpotRequest(
    val name: String,
    val category: String = "",
)

// ── Rankings ──────────────────────────────────────────────────────

data class RankedItem(
    @SerializedName("spot_name") val spotName: String,
    val score: Double = 5.0,
    val notes: String = "",
    @SerializedName("photo_url") val photoUrl: String = "",
)

data class SetRankingsRequest(
    val rankings: List<RankedItem>,
)

data class CompareSpotsRequest(
    @SerializedName("winner_spot_name") val winnerSpotName: String,
    @SerializedName("loser_spot_name") val loserSpotName: String,
)

data class CompareSpotsResponse(
    val status: String,
    val winner: StudySpot,
    val loser: StudySpot,
)

data class RankedSpot(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val spot: StudySpot,
    val rank: Int,
    val score: Double = 5.0,
    val tier: String = "B",
    /** Beli-style: "bad" | "okay" | "good" (for same-range comparison). */
    val rating: String = "okay",
    val notes: String = "",
    @SerializedName("photo_url") val photoUrl: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)

data class FeedItem(
    val id: Int,
    val user: UserPublic,
    val spot: StudySpot,
    val rank: Int,
    val score: Double = 5.0,
    val tier: String = "B",
    val notes: String = "",
    @SerializedName("photo_url") val photoUrl: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("is_liked") val isLiked: Boolean = false,
    @SerializedName("comments_count") val commentsCount: Int = 0,
    val comments: List<FeedComment> = emptyList(),
)

data class FeedComment(
    val id: Int,
    val user: UserPublic,
    val text: String,
    @SerializedName("created_at") val createdAt: String = "",
)

data class LikeResponse(
    val liked: Boolean,
)

data class AddCommentRequest(
    val text: String,
)

data class MatchupResponse(
    @SerializedName("spot_a") val spotA: StudySpot,
    @SerializedName("spot_b") val spotB: StudySpot,
)
