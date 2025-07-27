package com.example.clipcraft.ui.components

import com.example.clipcraft.models.TutorialTarget

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.clipcraft.models.SelectedVideo

@Composable
fun InstagramStyleGallery(
    recentVideos: List<SelectedVideo>, // Последние 9 видео
    allVideos: List<SelectedVideo>, // Все видео
    selectedVideos: List<SelectedVideo>,
    onVideoToggle: (SelectedVideo) -> Unit,
    onMaxVideosReached: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onManualEdit: (() -> Unit)? = null,
    onTutorialBoundsChanged: ((TutorialTarget, androidx.compose.ui.unit.IntRect) -> Unit)? = null
) {
    var showFullGallery by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Заголовок
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Выбрано: ${selectedVideos.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isEditMode) {
                        // Кнопка ручного редактирования
                        if (onTutorialBoundsChanged != null) {
                            TutorialTargetBounds(
                                target = TutorialTarget.MANUAL_EDIT_BUTTON,
                                onBoundsChanged = { bounds ->
                                    onTutorialBoundsChanged(TutorialTarget.MANUAL_EDIT_BUTTON, bounds)
                                }
                            ) {
                                TextButton(
                                    onClick = { onManualEdit?.invoke() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Редактировать вручную",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        } else {
                            TextButton(
                                onClick = { onManualEdit?.invoke() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Редактировать вручную",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Сетка 3x3 с кнопкой галереи
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Первые 8 видео
            items(recentVideos.take(8), key = { it.uri.toString() }) { video ->
                val isSelected = selectedVideos.any { selected ->
                    selected.uri.toString() == video.uri.toString() ||
                            selected.fileName == video.fileName
                }
                val selectionIndex = if (isSelected) {
                    selectedVideos.indexOfFirst { selected ->
                        selected.uri.toString() == video.uri.toString() ||
                                selected.fileName == video.fileName
                    }
                } else -1

                CompactVideoThumbnail(
                    video = video,
                    isSelected = isSelected,
                    selectionNumber = if (selectionIndex >= 0) selectionIndex + 1 else null,
                    onClick = {
                        Log.d("InstagramGallery", "Clicked video: ${video.fileName}, isSelected: $isSelected")
                        if (!isSelected && selectedVideos.size >= 20) {
                            onMaxVideosReached()
                        } else {
                            onVideoToggle(video)
                        }
                    }
                )
            }

            // Кнопка "Галерея" на 9-й позиции
            item {
                GalleryButton(
                    onClick = { showFullGallery = true }
                )
            }
        }
    }

    // Полноэкранная галерея
    if (showFullGallery) {
        FullScreenGalleryDialog(
            videos = allVideos,
            selectedVideos = selectedVideos,
            onVideoToggle = onVideoToggle,
            onMaxVideosReached = onMaxVideosReached,
            onDismiss = { showFullGallery = false }
        )
    }
}

@Composable
private fun CompactVideoThumbnail(
    video: SelectedVideo,
    isSelected: Boolean,
    selectionNumber: Int?,
    onClick: () -> Unit
) {
    // Отладка
    LaunchedEffect(isSelected) {
        Log.d("CompactVideoThumbnail", "Video: ${video.fileName}, isSelected: $isSelected, selectionNumber: $selectionNumber")
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        // Превью видео
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .crossfade(true)
                .size(300)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Затемнение при выборе
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        // Галочка при выборе с анимацией
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
            exit = scaleOut(animationSpec = tween(200)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Номер выбора с анимацией
        AnimatedVisibility(
            visible = isSelected && selectionNumber != null,
            enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
            exit = scaleOut(animationSpec = tween(200)) + fadeOut()
        ) {
            selectionNumber?.let { number ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = number.toString(),
                        color = MaterialTheme.colorScheme.onSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Длительность
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(2.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun GalleryButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Галерея",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Галерея",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreenGalleryDialog(
    videos: List<SelectedVideo>,
    selectedVideos: List<SelectedVideo>,
    onVideoToggle: (SelectedVideo) -> Unit,
    onMaxVideosReached: () -> Unit,
    onDismiss: () -> Unit
) {
    val pageSize = 30
    var loadedItems by remember { mutableStateOf(pageSize) }
    var isLoading by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Выберите видео") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Закрыть")
                        }
                    },
                    actions = {
                        if (selectedVideos.isNotEmpty()) {
                            TextButton(onClick = onDismiss) {
                                Text("Готово (${selectedVideos.size})")
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            val listState = rememberLazyGridState()
            val coroutineScope = rememberCoroutineScope()
            
            // Проверяем, нужно ли загрузить больше элементов
            LaunchedEffect(listState) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                    .collect { lastVisibleIndex ->
                        if (lastVisibleIndex != null && 
                            lastVisibleIndex >= loadedItems - 5 && 
                            loadedItems < videos.size && 
                            !isLoading) {
                            isLoading = true
                            // Имитируем загрузку (в реальном приложении здесь был бы запрос к БД)
                            delay(300)
                            loadedItems = minOf(loadedItems + pageSize, videos.size)
                            isLoading = false
                        }
                    }
            }
            
            LazyVerticalGrid(
                state = listState,
                columns = GridCells.Fixed(3),
                contentPadding = paddingValues,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                items(videos.take(loadedItems), key = { it.uri.toString() }) { video ->
                    val isSelected = selectedVideos.any { selected ->
                        selected.uri.toString() == video.uri.toString() ||
                                selected.fileName == video.fileName
                    }
                    val selectionIndex = if (isSelected) {
                        selectedVideos.indexOfFirst { selected ->
                            selected.uri.toString() == video.uri.toString() ||
                                    selected.fileName == video.fileName
                        }
                    } else -1

                    CompactVideoThumbnail(
                        video = video,
                        isSelected = isSelected,
                        selectionNumber = if (selectionIndex >= 0) selectionIndex + 1 else null,
                        onClick = {
                            if (!isSelected && selectedVideos.size >= 20) {
                                onMaxVideosReached()
                            } else {
                                onVideoToggle(video)
                            }
                        }
                    )
                }
                
                // Индикатор загрузки
                if (isLoading && loadedItems < videos.size) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}