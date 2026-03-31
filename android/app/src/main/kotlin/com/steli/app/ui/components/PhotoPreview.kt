package com.steli.app.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoPreview(
    photoUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val trimmed = photoUrl.trim()
    if (trimmed.isBlank()) {
        Placeholder(modifier = modifier)
        return
    }

    // Handle base64 "data:" URLs ourselves (Coil support varies by config).
    if (trimmed.startsWith("data:", ignoreCase = true)) {
        var bitmap: ImageBitmap? by remember(trimmed) { mutableStateOf(null) }
        var failed by remember(trimmed) { mutableStateOf(false) }

        LaunchedEffect(trimmed) {
            bitmap = null
            failed = false
            val comma = trimmed.indexOf(',')
            if (comma <= 0) {
                failed = true
                return@LaunchedEffect
            }
            val meta = trimmed.substring(0, comma)
            val b64 = trimmed.substring(comma + 1)
            // Expect "...;base64"
            if (!meta.contains(";base64", ignoreCase = true)) {
                failed = true
                return@LaunchedEffect
            }
            try {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                bitmap = bmp?.asImageBitmap()
                if (bitmap == null) failed = true
            } catch (_: Exception) {
                failed = true
            }
        }

        when {
            bitmap != null -> Image(
                bitmap = bitmap!!,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
            failed -> Placeholder(modifier = modifier)
            else -> Placeholder(modifier = modifier)
        }
        return
    }

    SubcomposeAsyncImage(
        model = trimmed,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> {
                // Only show the image if it actually decoded.
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
            }
            else -> Placeholder(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Placeholder(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

