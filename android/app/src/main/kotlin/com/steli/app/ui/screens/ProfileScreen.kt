package com.steli.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.steli.app.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String? = null,
    onNavigateToUser: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onLogout: () -> Unit,
) {
    val isOwnProfile = username == null
    val targetUsername = username ?: AuthManager.currentUser.value?.username ?: ""

    var user by remember { mutableStateOf<UserPublic?>(null) }
    var rankings by remember { mutableStateOf<List<RankedSpot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showFollowers by remember { mutableStateOf(false) }
    var showFollowing by remember { mutableStateOf(false) }
    var followersList by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<UserPublic>>(emptyList()) }

    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                user = if (isOwnProfile) steliApi.getMe() else steliApi.getUser(targetUsername)
                rankings = steliApi.getUserRankings(targetUsername)
            } catch (e: Exception) {
                error = "Could not load profile"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(targetUsername) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                },
                actions = {
                    if (isOwnProfile) {
                        IconButton(onClick = {
                            scope.launch {
                                try { steliApi.logout() } catch (_: Exception) { }
                                AuthManager.logout()
                                onLogout()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, "Logout")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { refresh() }) { Text("Retry") }
                    }
                }
            }
            user != null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        ProfileHeader(
                            user = user!!,
                            isOwnProfile = isOwnProfile,
                            onFollowToggle = {
                                scope.launch {
                                    try {
                                        if (user!!.isFollowing) steliApi.unfollowUser(targetUsername)
                                        else steliApi.followUser(targetUsername)
                                        refresh()
                                    } catch (_: Exception) { }
                                }
                            },
                            onFollowersClick = {
                                scope.launch {
                                    try {
                                        followersList = steliApi.getFollowers(targetUsername)
                                        showFollowers = true
                                    } catch (_: Exception) { }
                                }
                            },
                            onFollowingClick = {
                                scope.launch {
                                    try {
                                        followingList = steliApi.getFollowing(targetUsername)
                                        showFollowing = true
                                    } catch (_: Exception) { }
                                }
                            },
                        )
                    }

                    item {
                        Text(
                            "MY RANKINGS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (rankings.isEmpty()) {
                        item {
                            Text(
                                "No ranked spots yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(rankings, key = { it.id }) { ranked ->
                            ProfileRankedCard(ranked = ranked)
                        }
                    }
                }
            }
        }

        if (showFollowers) {
            UserListDialog(
                title = "Followers",
                users = followersList,
                onDismiss = { showFollowers = false },
                onUserClick = { u ->
                    showFollowers = false
                    onNavigateToUser(u.username)
                },
            )
        }

        if (showFollowing) {
            UserListDialog(
                title = "Following",
                users = followingList,
                onDismiss = { showFollowing = false },
                onUserClick = { u ->
                    showFollowing = false
                    onNavigateToUser(u.username)
                },
            )
        }
    }
}

@Composable
private fun ProfileHeader(
    user: UserPublic,
    isOwnProfile: Boolean,
    onFollowToggle: () -> Unit,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${user.firstName} ${user.lastName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatChip(
                    count = user.followersCount,
                    label = "Followers",
                    onClick = onFollowersClick,
                )
                StatChip(
                    count = user.followingCount,
                    label = "Following",
                    onClick = onFollowingClick,
                )
                StatChip(
                    count = user.rankedCount,
                    label = "Ranked",
                    onClick = { },
                )
            }
            if (!isOwnProfile) {
                Spacer(Modifier.height(12.dp))
                if (user.isFollowing) {
                    OutlinedButton(onClick = onFollowToggle) { Text("Unfollow") }
                } else {
                    Button(onClick = onFollowToggle) { Text("Follow") }
                }
            }
        }
    }
}

@Composable
private fun StatChip(count: Int, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileRankedCard(ranked: RankedSpot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (ranked.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = ranked.photoUrl,
                        contentDescription = ranked.spot.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = ranked.tier,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "%.1f".format(ranked.score),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = ranked.spot.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (ranked.notes.isNotBlank()) {
                    Text(
                        text = ranked.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserListItem(
    user: UserPublic,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text("${user.firstName} ${user.lastName}", fontWeight = FontWeight.SemiBold)
        },
        supportingContent = {
            Text("@${user.username}")
        },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailingContent = {
            Text(
                "${user.followersCount} followers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun UserListDialog(
    title: String,
    users: List<UserPublic>,
    onDismiss: () -> Unit,
    onUserClick: (UserPublic) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (users.isEmpty()) {
                Text("No $title yet.", textAlign = TextAlign.Center)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(users, key = { it.id }) { user ->
                        UserListItem(user = user, onClick = { onUserClick(user) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
