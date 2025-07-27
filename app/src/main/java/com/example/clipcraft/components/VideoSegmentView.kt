package com.example.clipcraft.components

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.clipcraft.models.VideoSegment
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VideoSegmentView(
    segment: VideoSegment,
    width: Dp,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onSegmentClick: () -> Unit = {},
    onTrimStart: (Float) -> Unit = {},
    onTrimEnd: (Float) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onTrimDragStart: (Boolean) -> Unit = {},
    onTrimDragEnd: (Float, Boolean) -> Unit = { _, _ -> },
    onDelete: () -> Unit = {},
    isDragging: Boolean = false,
    onSegmentDrag: (Offset) -> Unit = {}
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var segmentWidth by remember { mutableStateOf(0f) }
    var isDraggingStart by remember { mutableStateOf(false) }
    var isDraggingEnd by remember { mutableStateOf(false) }
    var showDeleteButton by remember { mutableStateOf(false) }
    var longPressDetected by remember { mutableStateOf(false) }
    var dragGestureStartTime by remember { mutableStateOf(0L) }
    var totalDragDelta by remember { mutableStateOf(0f) }
    var visualWidth by remember(width) { mutableStateOf(width) }
    
    // Анимация для выделения
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "borderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 0.dp,
        label = "borderWidth"
    )
    
    // Анимация для перетаскивания
    val elevationDp by animateDpAsState(
        targetValue = if (isDraggingStart || isDraggingEnd) 8.dp else 0.dp,
        label = "elevation"
    )
    val animatedScaleY by animateFloatAsState(
        targetValue = if (isDraggingStart || isDraggingEnd) 1.05f else 1f,
        label = "scaleY"
    )
    
    Box(
        modifier = modifier
            .width(visualWidth)
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                segmentWidth = coordinates.size.width.toFloat()
            }
            .graphicsLayer {
                scaleY = animatedScaleY
                shadowElevation = elevationDp.toPx()
                alpha = if (isDragging) 0.6f else 1f
            }
            .padding(horizontal = if (isSelected) 12.dp else 0.dp) // Место для манипуляторов
            .clip(MaterialTheme.shapes.small)
            .border(
                width = if (isSelected) borderWidth else 1.dp,
                color = if (isSelected) borderColor else Color.White.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            )
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Основной контент с обработкой кликов и перетаскивания
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isSelected) {
                    if (!isSelected) {
                        detectTapGestures(
                            onTap = {
                                Log.d("videoeditorclipcraft", "VideoSegmentView: Clicked on segment ${segment.id}")
                                onSegmentClick()
                            }
                        )
                    } else {
                        detectTapGestures(
                            onLongPress = {
                                Log.d("videoeditorclipcraft", "VideoSegmentView: Long press detected on segment ${segment.id}")
                                longPressDetected = true
                                onDragStart()
                            },
                            onTap = {
                                showDeleteButton = !showDeleteButton
                            }
                        )
                    }
                }
                .pointerInput(longPressDetected) {
                    if (longPressDetected) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                Log.d("videoeditorclipcraft", "VideoSegmentView: Started dragging at offset $offset")
                                onSegmentDrag(offset)
                            },
                            onDragEnd = {
                                Log.d("videoeditorclipcraft", "VideoSegmentView: Ending drag for segment ${segment.id}")
                                longPressDetected = false
                                onDragEnd()
                            },
                            onDrag = { change, _ ->
                                // Передаем абсолютную позицию касания для лучшего следования за пальцем
                                onSegmentDrag(change.position)
                            }
                        )
                    }
                }
        ) {
            // Tiled превью
            TiledThumbnails(
                thumbnails = segment.thumbnails,
                segmentWidth = visualWidth - if (isSelected) 24.dp else 0.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Затемнение при перетаскивании
        if (isDraggingStart || isDraggingEnd) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }
        
        // Манипуляторы обрезки (только для выделенного сегмента)
        if (isSelected) {
            // Левый манипулятор
            TrimHandle(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { 
                                dragGestureStartTime = System.currentTimeMillis()
                                isDraggingStart = true
                                totalDragDelta = 0f
                                onTrimDragStart(true) // true = редактируем начало
                            },
                            onDragEnd = { 
                                // Проверяем, прошло ли достаточно времени с начала жеста
                                val dragDuration = System.currentTimeMillis() - dragGestureStartTime
                                if (dragDuration > 100) { // 100 мс задержка
                                    isDraggingStart = false
                                    // Передаем общее изменение в секундах
                                    val totalDeltaTime = totalDragDelta / (100 * density.density)
                                    onTrimDragEnd(totalDeltaTime, true)
                                    totalDragDelta = 0f
                                    visualWidth = width // Сбрасываем визуальную ширину
                                } else {
                                    // Если жест был слишком коротким, игнорируем завершение
                                    coroutineScope.launch {
                                        delay(100 - dragDuration)
                                        isDraggingStart = false
                                        val totalDeltaTime = totalDragDelta / (100 * density.density)
                                        onTrimDragEnd(totalDeltaTime, true)
                                        totalDragDelta = 0f
                                        visualWidth = width
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                val deltaX = change.position.x - change.previousPosition.x
                                totalDragDelta += deltaX
                                // Обновляем визуальную ширину
                                val newWidth = (width.value - totalDragDelta / density.density).coerceAtLeast(20f)
                                visualWidth = newWidth.dp
                            }
                        )
                    },
                isLeft = true
            )
            
            // Правый манипулятор
            TrimHandle(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { 
                                dragGestureStartTime = System.currentTimeMillis()
                                isDraggingEnd = true
                                totalDragDelta = 0f
                                onTrimDragStart(false) // false = редактируем конец
                            },
                            onDragEnd = { 
                                // Проверяем, прошло ли достаточно времени с начала жеста
                                val dragDuration = System.currentTimeMillis() - dragGestureStartTime
                                if (dragDuration > 100) { // 100 мс задержка
                                    isDraggingEnd = false
                                    // Передаем общее изменение в секундах
                                    val totalDeltaTime = totalDragDelta / (100 * density.density)
                                    onTrimDragEnd(totalDeltaTime, false)
                                    totalDragDelta = 0f
                                    visualWidth = width
                                } else {
                                    // Если жест был слишком коротким, игнорируем завершение
                                    coroutineScope.launch {
                                        delay(100 - dragDuration)
                                        isDraggingEnd = false
                                        val totalDeltaTime = totalDragDelta / (100 * density.density)
                                        onTrimDragEnd(totalDeltaTime, false)
                                        totalDragDelta = 0f
                                        visualWidth = width
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                val deltaX = change.position.x - change.previousPosition.x
                                totalDragDelta += deltaX
                                // Обновляем визуальную ширину
                                val newWidth = (width.value + totalDragDelta / density.density).coerceAtLeast(20f)
                                visualWidth = newWidth.dp
                            }
                        )
                    },
                isLeft = false
            )
            
            // Кнопка удаления
            if (showDeleteButton) {
                IconButton(
                    onClick = {
                        onDelete()
                        showDeleteButton = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrimHandle(
    modifier: Modifier = Modifier,
    isLeft: Boolean
) {
    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                shape = if (isLeft) {
                    MaterialTheme.shapes.small.copy(
                        topEnd = androidx.compose.foundation.shape.CornerSize(0.dp),
                        bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)
                    )
                } else {
                    MaterialTheme.shapes.small.copy(
                        topStart = androidx.compose.foundation.shape.CornerSize(0.dp),
                        bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp)
                    )
                }
            )
    ) {
        // Индикатор захвата
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .height(24.dp)
                .background(Color.White)
        )
    }
}

@Composable
private fun TiledThumbnails(
    thumbnails: List<android.net.Uri>,
    segmentWidth: Dp,
    modifier: Modifier = Modifier
) {
    val thumbnailWidth = 60.dp
    val density = LocalDensity.current
    
    // Рассчитываем количество превью, которое влезет
    val segmentWidthPx = with(density) { segmentWidth.toPx() }
    val thumbnailWidthPx = with(density) { thumbnailWidth.toPx() }
    val thumbnailCount = (segmentWidthPx / thumbnailWidthPx).toInt()
    
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Start
    ) {
        // Показываем превью
        repeat(thumbnailCount) { index ->
            val thumbnailIndex = if (thumbnails.isNotEmpty()) {
                (index * thumbnails.size / thumbnailCount).coerceIn(0, thumbnails.size - 1)
            } else 0
            
            if (thumbnails.isNotEmpty() && thumbnailIndex < thumbnails.size) {
                Image(
                    painter = rememberAsyncImagePainter(thumbnails[thumbnailIndex]),
                    contentDescription = null,
                    modifier = Modifier
                        .width(thumbnailWidth)
                        .fillMaxHeight(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Заглушка если нет превью
                Box(
                    modifier = Modifier
                        .width(thumbnailWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        
        // Заполняем остаток одним цветом
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}