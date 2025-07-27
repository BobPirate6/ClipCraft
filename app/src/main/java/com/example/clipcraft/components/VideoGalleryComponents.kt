package com.example.clipcraft.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.clipcraft.models.SelectedVideo
import java.util.concurrent.TimeUnit

@Composable
fun VideoGalleryGrid(
    videos: List<SelectedVideo>,
    selectedVideos: List<SelectedVideo>,
    onVideoToggle: (SelectedVideo) -> Unit,
    onMaxVideosReached: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false
) {
    Column(modifier = modifier) {
        // Заголовок с количеством выбранных видео
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditMode) "добавь или убери видео" else "выбери видео",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selectedVideos.isNotEmpty()) {
                Text(
                    text = "Выбрано: ${selectedVideos.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Сетка видео
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(videos, key = { it.uri.toString() }) { video ->
                val selectionIndex = selectedVideos.indexOf(video)
                VideoThumbnail(
                    video = video,
                    selectionIndex = if (selectionIndex >= 0) selectionIndex + 1 else null,
                    onClick = {
                        if (!selectedVideos.contains(video) && selectedVideos.size >= 20) {
                            onMaxVideosReached()
                        } else {
                            onVideoToggle(video)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VideoThumbnail(
    video: SelectedVideo,
    selectionIndex: Int?,
    onClick: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        // Плейсхолдер во время загрузки
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // Превью видео с оптимизированной загрузкой
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .size(200) // Ограничиваем размер для быстрой загрузки
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { isLoading = false },
            onError = { isLoading = false }
        )

        // Затемнение при выборе
        if (selectionIndex != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        }

        // Длительность
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                fontSize = 10.sp
            )
        }

        // Индикатор выбора с галочкой
        if (selectionIndex != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                // Белый круг с галочкой
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = Color.White,
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return String.format("%d:%02d", minutes, seconds)
}

// Диалог превышения лимита
@Composable
fun MaxVideosDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Достигнут лимит") },
        text = {
            Text("Можно выбрать не более 20 видео для одного монтажа")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}