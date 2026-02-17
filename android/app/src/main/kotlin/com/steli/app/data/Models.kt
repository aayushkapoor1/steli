package com.steli.app.data

import com.google.gson.annotations.SerializedName

// ── Users ─────────────────────────────────────────────────────────

data class UserPublic(
    val id: Int,
    val username: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    @SerializedName("ranked_count") val rankedCount: Int = 0,
)

// ── Spots ─────────────────────────────────────────────────────────

data class StudySpot(
    val id: Int,
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

data class RankedSpot(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val spot: StudySpot,
    val rank: Int,
    val score: Double = 5.0,
    val tier: String = "B",
    val rating: String = "okay", // "bad" | "okay" | "good" (for same-range comparison)
    val notes: String = "",
    @SerializedName("photo_url") val photoUrl: String = "",
    @SerializedName("created_at") val createdAt: String = "",
)

data class MatchupResponse(
    @SerializedName("spot_a") val spotA: StudySpot,
    @SerializedName("spot_b") val spotB: StudySpot,
)
