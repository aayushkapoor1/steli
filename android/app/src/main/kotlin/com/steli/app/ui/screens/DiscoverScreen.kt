package com.steli.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.steli.app.data.StudySpot
import com.steli.app.data.UserPublic
import com.steli.app.data.steliApi
import com.steli.app.ui.theme.SteliTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onAddSpot: (String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var refreshNonce by remember { mutableStateOf(0) }
    var spotsLoading by remember { mutableStateOf(true) }
    var spotsError by remember { mutableStateOf<String?>(null) }
    var spots by remember { mutableStateOf<List<StudySpot>>(emptyList()) }
    var userSuggestions by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var usersLoading by remember { mutableStateOf(false) }
    var addingSpotId by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(query, refreshNonce) {
        val q = query.trim()

        // Empty search: show global study spots (no user suggestions).
        if (q.isBlank()) {
            spotsLoading = true
            spotsError = null
            usersLoading = false
            userSuggestions = emptyList()
            try {
                spots = steliApi.getSpots()
            } catch (_: Exception) {
                spotsError = "Could not load study spots."
            } finally {
                spotsLoading = false
            }
            return@LaunchedEffect
        }

        // Debounce for a nicer autocomplete feel.
        spotsLoading = true
        spotsError = null
        usersLoading = true
        userSuggestions = emptyList()

        delay(250)

        try {
            coroutineScope {
                val spotsDeferred = async { steliApi.getSpots(q) }
                val usersDeferred = async { steliApi.searchUsers(q) }

                val newSpots = try {
                    spotsDeferred.await()
                } catch (_: Exception) {
                    null
                }
                val newUsers = try {
                    usersDeferred.await()
                } catch (_: Exception) {
                    emptyList()
                }

                if (newSpots == null) {
                    spotsError = "Could not load study spots."
                    spots = emptyList()
                } else {
                    spots = newSpots
                }
                userSuggestions = newUsers
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            usersLoading = false
            spotsLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("Discover", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Find study spots or users",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search for study spots or users...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
            )

            val trimmedQuery = query.trim()
            if (trimmedQuery.isNotBlank() && (usersLoading || userSuggestions.isNotEmpty())) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "SUGGESTED USERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                ) {
                    when {
                        usersLoading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Searching users...")
                            }
                        }

                        userSuggestions.isEmpty() -> {
                            Text(
                                "No users found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }

                        else -> {
                            val shownUsers = userSuggestions.take(5)
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(shownUsers, key = { it.id }) { user ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .clickable { onNavigateToUser(user.username) },
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(12.dp),
                                        tonalElevation = 1.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${user.firstName} ${user.lastName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = "@${user.username}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                            Text(
                                                text = "View",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "GLOBAL STUDY SPOTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            when {
                spotsLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                spotsError != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(spotsError!!, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { refreshNonce++ }) { Text("Retry") }
                        }
                    }
                }
                spots.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No study spots found.")
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(spots, key = { it.id }) { spot ->
                            DiscoverSpotRow(
                                spot = spot,
                                adding = addingSpotId == spot.id,
                                onAdd = {
                                    scope.launch {
                                        addingSpotId = spot.id
                                        onAddSpot(spot.name)
                                        addingSpotId = null
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverSpotRow(
    spot: StudySpot,
    adding: Boolean,
    onAdd: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spot.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (spot.category.isNotBlank()) {
                    Text(
                        spot.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onAdd,
                enabled = !adding,
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.inverseSurface),
            ) {
                if (adding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                } else {
                    Text("+ Add", color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    SteliTheme {
        DiscoverScreen(onAddSpot = {}, onNavigateToUser = {})
    }
}

