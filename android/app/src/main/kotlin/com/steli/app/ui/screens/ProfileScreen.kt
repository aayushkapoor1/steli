package com.steli.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.steli.app.ui.components.PhotoPreview
import com.steli.app.data.AuthManager
import com.steli.app.data.RankedSpot
import com.steli.app.data.UpdatePhotoRequest
import com.steli.app.data.UpdatePrivacyRequest
import com.steli.app.data.UserPublic
import com.steli.app.data.steliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
    var showFollowRequests by remember { mutableStateOf(false) }
    var followersList by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<UserPublic>>(emptyList()) }
    var followRequestsList by remember { mutableStateOf<List<UserPublic>>(emptyList()) }

    val scope = rememberCoroutineScope()

    val canViewRankings = isOwnProfile
            || (user?.isPublic == true)
            || (user?.isFollowing == true)

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            try {
                user = if (isOwnProfile) steliApi.getMe() else steliApi.getUser(targetUsername)
                val visible = isOwnProfile
                        || (user?.isPublic == true)
                        || (user?.isFollowing == true)
                rankings = if (visible) {
                    try { steliApi.getUserRankings(targetUsername) } catch (_: Exception) { emptyList() }
                } else {
                    emptyList()
                }
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
                        val requestCount = user?.pendingRequestsCount ?: 0
                        if (requestCount > 0) {
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        followRequestsList = steliApi.getFollowRequests()
                                        showFollowRequests = true
                                    } catch (_: Exception) { }
                                }
                            }) {
                                BadgedBox(badge = {
                                    Badge { Text(requestCount.toString()) }
                                }) {
                                    Icon(Icons.Default.Notifications, "Follow requests")
                                }
                            }
                        }
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
                                        when (user!!.followStatus) {
                                            "following", "requested" -> steliApi.unfollowUser(targetUsername)
                                            else -> steliApi.followUser(targetUsername)
                                        }
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
                            onPhotoUpdated = { updated -> user = updated },
                        )
                    }

                    if (isOwnProfile) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        if (user!!.isPublic) Icons.Default.Person else Icons.Default.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (user!!.isPublic) "Public profile" else "Private profile",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            if (user!!.isPublic)
                                                "Anyone can see your rankings and follow you without approval."
                                            else
                                                "Only approved followers can see your rankings.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = user!!.isPublic,
                                        onCheckedChange = { newValue ->
                                            scope.launch {
                                                try {
                                                    user = steliApi.updatePrivacy(
                                                        UpdatePrivacyRequest(newValue)
                                                    )
                                                } catch (_: Exception) { }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            if (isOwnProfile) "MY RANKINGS" else "RANKINGS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (!canViewRankings) {
                        item {
                            PrivateProfileMessage(
                                username = user!!.username,
                                followStatus = user!!.followStatus,
                                onFollowClick = {
                                    scope.launch {
                                        try {
                                            steliApi.followUser(targetUsername)
                                            refresh()
                                        } catch (_: Exception) { }
                                    }
                                },
                            )
                        }
                    } else if (rankings.isEmpty()) {
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

        if (showFollowRequests) {
            FollowRequestsDialog(
                requests = followRequestsList,
                onDismiss = {
                    showFollowRequests = false
                    refresh()
                },
                onApprove = { u ->
                    scope.launch {
                        try {
                            steliApi.approveFollowRequest(u.username)
                            followRequestsList = followRequestsList.filter { it.id != u.id }
                            refresh()
                        } catch (_: Exception) { }
                    }
                },
                onDeny = { u ->
                    scope.launch {
                        try {
                            steliApi.denyFollowRequest(u.username)
                            followRequestsList = followRequestsList.filter { it.id != u.id }
                            refresh()
                        } catch (_: Exception) { }
                    }
                },
                onUserClick = { u ->
                    showFollowRequests = false
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
    onPhotoUpdated: (UserPublic) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var uploading by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            try {
                val dataUri = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    val original = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    if (original == null) return@withContext null
                    val maxDim = 512
                    val scale = minOf(
                        maxDim.toFloat() / original.width,
                        maxDim.toFloat() / original.height,
                        1f,
                    )
                    val scaled = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            original,
                            (original.width * scale).toInt(),
                            (original.height * scale).toInt(),
                            true,
                        )
                    } else original
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    "data:image/jpeg;base64,$b64"
                }
                if (dataUri != null) {
                    val updated = steliApi.updateProfilePhoto(UpdatePhotoRequest(dataUri))
                    onPhotoUpdated(updated)
                }
            } catch (_: Exception) { }
            uploading = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isOwnProfile) Modifier.clickable { imagePicker.launch("image/*") }
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (user.profilePhotoUrl.isNotBlank()) {
                PhotoPreview(
                    photoUrl = user.profilePhotoUrl,
                    contentDescription = "${user.firstName}'s photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isOwnProfile && !uploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Change photo",
                        modifier = Modifier.size(20.dp),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            if (uploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
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
                when (user.followStatus) {
                    "following" -> OutlinedButton(onClick = onFollowToggle) { Text("Unfollow") }
                    "requested" -> OutlinedButton(onClick = onFollowToggle, enabled = true) {
                        Text("Requested")
                    }
                    else -> Button(onClick = onFollowToggle) { Text("Follow") }
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
    val scoreColor = when {
        ranked.score >= 8.0 -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
        ranked.score >= 6.0 -> androidx.compose.ui.graphics.Color(0xFFF9A825)
        else -> androidx.compose.ui.graphics.Color(0xFFC62828)
    }
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
                PhotoPreview(
                    photoUrl = ranked.photoUrl,
                    contentDescription = ranked.spot.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        color = scoreColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            text = ranked.tier,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = "%.1f".format(ranked.score),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scoreColor,
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
private fun PrivateProfileMessage(
    username: String,
    followStatus: String,
    onFollowClick: () -> Unit,
) {
    val isRequested = followStatus == "requested"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "This profile is private",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (isRequested)
                    "You've sent a follow request to @$username. Once they approve it, you'll be able to see their rankings."
                else
                    "Send @$username a follow request to see their rankings and activity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (!isRequested) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFollowClick) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Send Follow Request")
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (user.profilePhotoUrl.isNotBlank()) {
                    PhotoPreview(
                        photoUrl = user.profilePhotoUrl,
                        contentDescription = "${user.firstName}'s photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
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
private fun FollowRequestsDialog(
    requests: List<UserPublic>,
    onDismiss: () -> Unit,
    onApprove: (UserPublic) -> Unit,
    onDeny: (UserPublic) -> Unit,
    onUserClick: (UserPublic) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Follow Requests") },
        text = {
            if (requests.isEmpty()) {
                Text("No pending requests.", textAlign = TextAlign.Center)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(requests, key = { it.id }) { user ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    "${user.firstName} ${user.lastName}",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { onUserClick(user) },
                                )
                            },
                            supportingContent = {
                                Text("@${user.username}")
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { onUserClick(user) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (user.profilePhotoUrl.isNotBlank()) {
                                        PhotoPreview(
                                            photoUrl = user.profilePhotoUrl,
                                            contentDescription = "${user.firstName}'s photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onApprove(user) }) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Approve",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { onDeny(user) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Deny",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
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
