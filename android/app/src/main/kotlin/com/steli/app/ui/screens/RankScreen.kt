package com.steli.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.steli.app.data.*
import kotlinx.coroutines.launch

// Local model for a spot being ranked (before submission)
private data class LocalRankedSpot(
    val spotName: String,
    val score: Double = 7.0,
    val notes: String = "",
    val photoUrl: String = "",
    val rating: String = "okay", // "bad" | "okay" | "good"
)

// Initial rating: 3 equal ranges on 0–10
private const val SCORE_BAD_MAX = 10.0 / 3
private const val SCORE_OKAY_MAX = (20.0 / 3) - .1
private const val SCORE_BAD_MID = (0.0 + SCORE_BAD_MAX) / 2
private const val SCORE_OKAY_MID = (SCORE_BAD_MAX + SCORE_OKAY_MAX) / 2
private const val SCORE_GOOD_MID = (SCORE_OKAY_MAX + 10.0) / 2

private val SCORE_COLOR_BAD = Color(0xFFC62828)
private val SCORE_COLOR_OKAY = Color(0xFFF9A825)
private val SCORE_COLOR_GOOD = Color(0xFF2E7D32)

private fun scoreToBadgeColor(score: Double): Color = when {
    score >= SCORE_OKAY_MAX -> SCORE_COLOR_GOOD
    score >= SCORE_BAD_MAX -> SCORE_COLOR_OKAY
    else -> SCORE_COLOR_BAD
}

// Screen states. Flow: rate first (Good/Bad/Okay), then compare only within same range
private sealed class RankState {
    data object Viewing : RankState()
    data object Adding : RankState()
    // User just added a spot; ask Good/Bad/Okay first
    data class RatingFirst(val newSpot: LocalRankedSpot) : RankState()
    // Comparing new spot only to spots in same range (sameRangeIndices into rankedSpots)
    data class Comparing(
        val newSpot: LocalRankedSpot,
        val sameRangeIndices: List<Int>,
        val low: Int,
        val high: Int,
    ) : RankState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen() {
    var rankedSpots by remember { mutableStateOf<List<LocalRankedSpot>>(emptyList()) }
    var state by remember { mutableStateOf<RankState>(RankState.Viewing) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingSpot by remember { mutableStateOf<LocalRankedSpot?>(null) }
    val scope = rememberCoroutineScope()

    /*
     * Place items in each range (good / okay / bad) evenly along that range.
     * E.g. 3 items in "good" get scores spread evenly from SCORE_OKAY_MAX to 10.0.
     */
    fun scoresFromRankOrder(spots: List<LocalRankedSpot>): List<LocalRankedSpot> {
        val goodSpots = spots.filter { it.rating == "good" }
        val okaySpots = spots.filter { it.rating == "okay" }
        val badSpots = spots.filter { it.rating == "bad" }

        fun scoreInRange(n: Int, index: Int, rangeLow: Double, rangeHigh: Double): Double {
            if (n <= 0) return (rangeLow + rangeHigh) / 2
            if (n == 1) return (rangeLow + rangeHigh) / 2
            return rangeHigh - index * (rangeHigh - rangeLow) / (n - 1)
        }

        val goodScores = goodSpots.indices.map { i ->
            scoreInRange(goodSpots.size, i, SCORE_OKAY_MAX, 10.0)
        }
        val okayRangeLow = SCORE_BAD_MAX + 0.01
        val okayRangeHigh = SCORE_OKAY_MAX - 0.01
        val okayScores = okaySpots.indices.map { i ->
            scoreInRange(okaySpots.size, i, okayRangeLow, okayRangeHigh)
        }
        val badRangeLow = 0.01
        val badRangeHigh = SCORE_BAD_MAX - 0.01
        val badScores = badSpots.indices.map { i ->
            scoreInRange(badSpots.size, i, badRangeLow, badRangeHigh)
        }

        var goodIdx = 0
        var okayIdx = 0
        var badIdx = 0
        return spots.map { spot ->
            val score = when (spot.rating) {
                "good" -> goodScores[goodIdx++]
                "okay" -> okayScores[okayIdx++]
                else -> badScores[badIdx++]
            }
            spot.copy(score = score)
        }
    }

    // Load existing rankings in server order; normalize scores for display so position 1 = highest.
    LaunchedEffect(Unit) {
        try {
            val user = AuthManager.currentUser.value ?: return@LaunchedEffect
            val rankings = steliApi.getUserRankings(user.username)
            val loaded = rankings.map {
                LocalRankedSpot(
                    spotName = it.spot.name,
                    score = it.score,
                    notes = it.notes,
                    photoUrl = it.photoUrl,
                    rating = it.rating,
                )
            }
            rankedSpots = scoresFromRankOrder(loaded).sortedByDescending { it.score }
        } catch (_: Exception) { }
        loading = false
    }

    // Save the full ranked list to the server. Scores are normalized so position 1 = highest score
    fun saveRankings(spots: List<LocalRankedSpot>) {
        scope.launch {
            saving = true
            try {
                val normalized = scoresFromRankOrder(spots).sortedByDescending { it.score }
                steliApi.setRankings(
                    SetRankingsRequest(
                        rankings = normalized.map {
                            RankedItem(spotName = it.spotName, score = it.score, notes = it.notes, photoUrl = it.photoUrl)
                        }
                    )
                )
                rankedSpots = normalized
                error = null
            } catch (e: Exception) {
                error = "Failed to save rankings due to error: " + e.message
            } finally {
                saving = false
            }
        }
    }

    /*
     * Score for a new item at [index] so it's unique and preserves order (between neighbors).
     * Avoids every "good" / "okay" / "bad" item getting the same midpoint and showing identical ranks.
     */
    fun scoreForInsertionAtIndex(index: Int): Double {
        return when {
            rankedSpots.isEmpty() -> SCORE_GOOD_MID
            index == 0 -> (10.0 + rankedSpots[0].score).coerceIn(0.0, 10.0) / 2.0
            index >= rankedSpots.size -> (rankedSpots[rankedSpots.size - 1].score - 0.5).coerceIn(0.0, 10.0)
            else -> (rankedSpots[index - 1].score + rankedSpots[index].score) / 2.0
        }
    }

    // Insert spot at full-list index. Assigns a unique score so list order and ranks stay correct
    fun insertAtIndex(spot: LocalRankedSpot, index: Int) {
        val score = scoreForInsertionAtIndex(index)
        val spotWithScore = spot.copy(score = score)
        val updated = rankedSpots.toMutableList().apply { add(index, spotWithScore) }
        saveRankings(updated)
        state = RankState.Viewing
    }

    // Index in full list where this rating bucket starts. Good=0, Okay=after good, Bad=after good+okay
    fun startIndexForRating(rating: String): Int {
        val goodCount = rankedSpots.count { it.rating == "good" }
        val okayCount = rankedSpots.count { it.rating == "okay" }
        return when (rating) {
            "good" -> 0
            "okay" -> goodCount
            else -> goodCount + okayCount
        }
    }

    // Indices into rankedSpots that have this rating (order preserved)
    fun sameRangeIndices(rating: String): List<Int> =
        rankedSpots.mapIndexed { i, s -> i to s }.filter { it.second.rating == rating }.map { it.first }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text(
                    "Steli",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "My Rankings",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        floatingActionButton = {
            if (state is RankState.Viewing && !loading) {
                FloatingActionButton(
                    onClick = { state = RankState.Adding },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add spot")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val currentState = state) {
                is RankState.Viewing -> {
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (rankedSpots.isEmpty()) {
                        EmptyRankingsView()
                    } else {
                        RankedListView(
                            spots = rankedSpots.sortedByDescending { it.score },
                            saving = saving,
                            onDelete = { spotToRemove ->
                                val idx = rankedSpots.indexOf(spotToRemove)
                                if (idx >= 0) {
                                    val updated = rankedSpots.toMutableList().apply { removeAt(idx) }
                                    saveRankings(updated)
                                }
                            },
                            onEdit = { spotToEdit ->
                                editingSpot = spotToEdit
                            },
                        )
                    }
                    if (error != null) {
                        Snackbar(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        ) { Text(error!!) }
                    }
                    editingSpot?.let { spot ->
                        EditRankDialog(
                            spot = spot,
                            onConfirm = { updated ->
                                // Remove the old instance of this spot
                                val withoutOld = rankedSpots.toMutableList().apply {
                                    remove(spot)
                                }
                                rankedSpots = withoutOld
                                editingSpot = null

                                // Treat the updated spot as if it were newly added,
                                // using the same rating-first + pairwise comparison flow.
                                val rating = updated.rating
                                val spotWithRating = when (rating) {
                                    "good" -> updated.copy(score = SCORE_GOOD_MID)
                                    "okay" -> updated.copy(score = SCORE_OKAY_MID)
                                    "bad" -> updated.copy(score = SCORE_BAD_MID)
                                    else -> updated.copy(score = SCORE_OKAY_MID)
                                }

                                val indices = sameRangeIndices(rating)
                                if (indices.isEmpty()) {
                                    // No existing items in this range: insert directly at the start index
                                    insertAtIndex(spotWithRating, startIndexForRating(rating))
                                } else {
                                    // Reuse the existing pairwise comparison flow
                                    state = RankState.Comparing(
                                        newSpot = spotWithRating,
                                        sameRangeIndices = indices,
                                        low = 0,
                                        high = indices.size,
                                    )
                                }
                            },
                            onDismiss = { editingSpot = null },
                        )
                    }
                }

                is RankState.Adding -> {
                    AddSpotForm(
                        existingNames = rankedSpots.map { it.spotName.lowercase() }.toSet(),
                        onSubmit = { newSpot ->
                            // Beli: always rate first (Good/Bad/Okay), then compare within same range
                            state = RankState.RatingFirst(newSpot)
                        },
                        onCancel = { state = RankState.Viewing },
                    )
                }

                is RankState.RatingFirst -> {
                    InitialRatingDialog(
                        spotName = currentState.newSpot.spotName,
                        onBad = {
                            val spotWithRating = currentState.newSpot.copy(
                                score = SCORE_BAD_MID,
                                rating = "bad",
                            )
                            val indices = sameRangeIndices("bad")
                            if (indices.isEmpty()) {
                                insertAtIndex(spotWithRating, startIndexForRating("bad"))
                            } else {
                                state = RankState.Comparing(
                                    newSpot = spotWithRating,
                                    sameRangeIndices = indices,
                                    low = 0,
                                    high = indices.size,
                                )
                            }
                        },
                        onOkay = {
                            val spotWithRating = currentState.newSpot.copy(
                                score = SCORE_OKAY_MID,
                                rating = "okay",
                            )
                            val indices = sameRangeIndices("okay")
                            if (indices.isEmpty()) {
                                insertAtIndex(spotWithRating, startIndexForRating("okay"))
                            } else {
                                state = RankState.Comparing(
                                    newSpot = spotWithRating,
                                    sameRangeIndices = indices,
                                    low = 0,
                                    high = indices.size,
                                )
                            }
                        },
                        onGood = {
                            val spotWithRating = currentState.newSpot.copy(
                                score = SCORE_GOOD_MID,
                                rating = "good",
                            )
                            val indices = sameRangeIndices("good")
                            if (indices.isEmpty()) {
                                insertAtIndex(spotWithRating, startIndexForRating("good"))
                            } else {
                                state = RankState.Comparing(
                                    newSpot = spotWithRating,
                                    sameRangeIndices = indices,
                                    low = 0,
                                    high = indices.size,
                                )
                            }
                        },
                        onCancel = { state = RankState.Viewing },
                    )
                }

                is RankState.Comparing -> {
                    val indices = currentState.sameRangeIndices
                    val mid = (currentState.low + currentState.high) / 2
                    if (currentState.low >= currentState.high) {
                        // Insert before indices[low], or at end of same-range if low == indices.size
                        val insertAt = if (currentState.low < indices.size) indices[currentState.low] else rankedSpots.size
                        insertAtIndex(currentState.newSpot, insertAt)
                    } else {
                        val fullIndex = indices[mid]
                        ComparisonView(
                            newSpotName = currentState.newSpot.spotName,
                            existingSpotName = rankedSpots[fullIndex].spotName,
                            existingRank = mid + 1,
                            totalComparisons = indices.size,
                            onPreferNew = {
                                state = currentState.copy(high = mid)
                            },
                            onPreferExisting = {
                                state = currentState.copy(low = mid + 1)
                            },
                            onCancel = { state = RankState.Viewing },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyRankingsView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Add a study spot review to see your rankings",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EditRankDialog(
    spot: LocalRankedSpot,
    onConfirm: (LocalRankedSpot) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by remember(spot) { mutableStateOf(spot.notes) }
    var selectedRating by remember(spot) { mutableStateOf(spot.rating) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit ranking",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = spot.spotName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "How was it overall?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RatingOptionChip(
                        label = "Good",
                        isSelected = selectedRating == "good",
                        color = SCORE_COLOR_GOOD,
                        onClick = { selectedRating = "good" },
                    )
                    RatingOptionChip(
                        label = "Okay",
                        isSelected = selectedRating == "okay",
                        color = SCORE_COLOR_OKAY,
                        onClick = { selectedRating = "okay" },
                    )
                    RatingOptionChip(
                        label = "Bad",
                        isSelected = selectedRating == "bad",
                        color = SCORE_COLOR_BAD,
                        onClick = { selectedRating = "bad" },
                    )
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = spot.copy(
                        notes = notes.trim(),
                        rating = selectedRating,
                    )
                    onConfirm(updated)
                },
            ) {
                Text("Save and Re-rank")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RatingOptionChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isSelected) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        border = if (isSelected) {
            BorderStroke(1.dp, color)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RankedListView(
    spots: List<LocalRankedSpot>,
    saving: Boolean,
    onDelete: (LocalRankedSpot) -> Unit,
    onEdit: (LocalRankedSpot) -> Unit,
) {
    if (saving) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
        )
    }

    // Display in rank order (server order), not by score.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(spots.size) { index ->
            val spot = spots[index]
            val accentColor = scoreToBadgeColor(spot.score)
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "%.1f".format(spot.score),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = spot.spotName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (spot.notes.isNotBlank()) {
                            Text(
                                text = spot.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { onEdit(spot) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onDelete(spot) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove",
                                tint = Color(0xFF8D6E63),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSpotForm(
    existingNames: Set<String>,
    onSubmit: (LocalRankedSpot) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    // Fetch all known spots for autocomplete suggestions
    var allSpots by remember { mutableStateOf<List<StudySpot>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { allSpots = steliApi.getSpots() } catch (_: Exception) { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            "Add a Study Spot",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            placeholder = { Text("Spot Name") },
            singleLine = true,
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )

        // Show matching existing spots
        val suggestions = allSpots.filter {
            name.isNotBlank() && it.name.lowercase().contains(name.lowercase()) &&
                it.name.lowercase() !in existingNames
        }.take(3)
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            suggestions.forEach { spot ->
                TextButton(
                    onClick = { name = spot.name },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use: ${spot.name}", modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            placeholder = { Text("Notes (optional)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = photoUrl,
            onValueChange = { photoUrl = it },
            placeholder = { Text("Photo URL (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )

        if (photoUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = photoUrl,
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) {
                        nameError = "Name is required"
                        return@Button
                    }
                    if (trimmedName.lowercase() in existingNames) {
                        nameError = "You've already ranked this spot"
                        return@Button
                    }
                    onSubmit(LocalRankedSpot(spotName = trimmedName, notes = notes.trim(), photoUrl = photoUrl.trim()))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
private fun InitialRatingDialog(
    spotName: String,
    onBad: () -> Unit,
    onOkay: () -> Unit,
    onGood: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "How was it?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                spotName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            // Subtle outlined choices: thin accent bar + text, no full fill
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(0.85f),
            ) {
                RatingChoiceChip(
                    label = "Good",
                    accentColor = SCORE_COLOR_GOOD,
                    onClick = onGood,
                )
                RatingChoiceChip(
                    label = "Okay",
                    accentColor = SCORE_COLOR_OKAY,
                    onClick = onOkay,
                )
                RatingChoiceChip(
                    label = "Bad",
                    accentColor = SCORE_COLOR_BAD,
                    onClick = onBad,
                )
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RatingChoiceChip(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SpotChoiceCard(spotName: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = spotName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ComparisonView(
    newSpotName: String,
    existingSpotName: String,
    existingRank: Int,
    totalComparisons: Int,
    onPreferNew: () -> Unit,
    onPreferExisting: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text(
            "Rank Study Spots",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Which spot do you prefer?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val filled = (totalComparisons - (totalComparisons - existingRank)).coerceIn(0, 5)
            repeat(5) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i < filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        SpotChoiceCard(spotName = newSpotName, onClick = onPreferNew)
        Spacer(Modifier.height(12.dp))
        Text(
            "VS",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(12.dp))
        SpotChoiceCard(spotName = existingSpotName, onClick = onPreferExisting)

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}
