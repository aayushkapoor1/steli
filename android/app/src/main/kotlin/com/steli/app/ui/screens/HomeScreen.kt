package com.steli.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.steli.app.ui.components.PhotoPreview
import com.steli.app.data.AddCommentRequest
import com.steli.app.data.FeedComment
import com.steli.app.data.FeedItem
import com.steli.app.data.UserPublic
import com.steli.app.data.steliApi
import com.steli.app.ui.theme.SteliTheme
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var feed by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun refreshFeed() {
        scope.launch {
            loading = true
            error = null
            try {
                feed = steliApi.getFeed()
            } catch (_: Exception) {
                error = "Could not load feed."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshFeed() }

    when {
        loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        error != null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { refreshFeed() }) {
                        Text("Retry")
                    }
                }
            }
        }
        feed.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your feed is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add friends to see their study spot rankings and reviews. Search for people you know and follow them to get started!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Steli", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Recent Rankings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(feed, key = { it.id }) { item ->
                    FeedCard(item = item)
                }
            }
        }
    }
}

private fun scoreAccent(score: Double): androidx.compose.ui.graphics.Color = when {
    score >= 8.0 -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    score >= 6.0 -> androidx.compose.ui.graphics.Color(0xFFF9A825)
    else -> androidx.compose.ui.graphics.Color(0xFFC62828)
}

@Composable
private fun FeedCard(item: FeedItem) {
    val scope = rememberCoroutineScope()
    var liked by remember(item.id) { mutableStateOf(item.isLiked) }
    var likesCount by remember(item.id) { mutableIntStateOf(item.likesCount) }
    var commentsExpanded by remember { mutableStateOf(false) }
    var comments by remember(item.id) { mutableStateOf(item.comments) }
    var commentsCount by remember(item.id) { mutableIntStateOf(item.commentsCount) }
    var commentText by remember { mutableStateOf("") }
    var sendingComment by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UserAvatar(user = item.user, size = 32)
                Column {
                    Text(
                        text = "${item.user.firstName} ${item.user.lastName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "@${item.user.username} · ${item.createdAt.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    PhotoPreview(
                        photoUrl = item.photoUrl,
                        contentDescription = item.spot.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.spot.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.tier,
                            style = MaterialTheme.typography.labelSmall,
                            color = scoreAccent(item.score),
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = scoreAccent(item.score),
                        ) {
                            Text(
                                text = "%.1f".format(item.score),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val heartColor by animateColorAsState(
                    targetValue = if (liked)
                        androidx.compose.ui.graphics.Color(0xFFE53935)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "heartColor",
                )
                IconButton(onClick = {
                    scope.launch {
                        try {
                            val resp = steliApi.toggleLike(item.id)
                            liked = resp.liked
                            likesCount += if (resp.liked) 1 else -1
                        } catch (_: Exception) {}
                    }
                }) {
                    Icon(
                        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (liked) "Unlike" else "Like",
                        tint = heartColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (likesCount > 0) {
                    Text(
                        text = "$likesCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = {
                    commentsExpanded = !commentsExpanded
                    if (commentsExpanded) {
                        scope.launch {
                            try {
                                comments = steliApi.getComments(item.id)
                                commentsCount = comments.size
                            } catch (_: Exception) {}
                        }
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (commentsCount > 0) {
                    Text(
                        text = "$commentsCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (commentsExpanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    comments.forEach { comment ->
                        CommentRow(comment)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Add a comment...",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (commentText.isNotBlank() && !sendingComment) {
                                        val text = commentText
                                        commentText = ""
                                        sendingComment = true
                                        scope.launch {
                                            try {
                                                val newComment = steliApi.addComment(
                                                    item.id,
                                                    AddCommentRequest(text),
                                                )
                                                comments = comments + newComment
                                                commentsCount = comments.size
                                            } catch (_: Exception) {}
                                            sendingComment = false
                                        }
                                    }
                                },
                            ),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank() && !sendingComment) {
                                    val text = commentText
                                    commentText = ""
                                    sendingComment = true
                                    scope.launch {
                                        try {
                                            val newComment = steliApi.addComment(
                                                item.id,
                                                AddCommentRequest(text),
                                            )
                                            comments = comments + newComment
                                            commentsCount = comments.size
                                        } catch (_: Exception) {}
                                        sendingComment = false
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank() && !sendingComment,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = "Send",
                                modifier = Modifier.size(20.dp),
                                tint = if (commentText.isNotBlank())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: FeedComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(user = comment.user, size = 20)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "@${comment.user.username}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UserAvatar(user: UserPublic, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size((size / 2).dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SteliTheme {
        HomeScreen()
    }
}