package com.example.clipcraft.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import coil.compose.AsyncImage
import com.example.clipcraft.models.VideoSegment
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val PIXELS_PER_SECOND = 100f
private val ZOOM_LEVELS = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)

@Composable
fun VideoTimelineSimple(
    segments: List<VideoSegment>,
    currentPosition: Float,
    isPlaying: Boolean,
    zoomLevel: Float,
    modifier: Modifier = Modifier,
    onSegmentClick: (VideoSegment) -> Unit = {},
    onSegmentDelete: (String) -> Unit = {},
    onSegmentReorder: (Int, Int) -> Unit = { _, _ -> },
    onSegmentTrim: (String, Float, Boolean) -> Unit = { _, _, _ -> },
    onPositionChange: (Float) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    selectedSegmentId: String? = null
) {
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Размер таймлайна для вычисления минимального зума
    var timelineWidth by remember { mutableStateOf(0f) }
    
    // Текущий уровень масштаба
    var currentZoom by remember { mutableStateOf(zoomLevel) }
    
    // Состояние для drag & drop
    var draggingSegmentId by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    
    // Для мгновенной перестановки
    var currentDraggedIndex by remember { mutableStateOf(-1) }
    var reorderedSegments by remember(segments) { mutableStateOf(segments) }
    
    // Эффективный зум для рендеринга
    val effectiveZoom = currentZoom
    
    LaunchedEffect(zoomLevel) {
        currentZoom = zoomLevel
    }
    
    // Общая длительность
    val totalDuration = segments.sumOf { it.duration.toDouble() }.toFloat()
    
    // Вычисляем минимальный зум для показа всех сегментов
    val minZoom = if (totalDuration > 0 && timelineWidth > 0) {
        val padding = with(density) { 32.dp.toPx() } // Учитываем отступы
        val availableWidth = timelineWidth - padding
        val minZoomToFit = availableWidth / (totalDuration * PIXELS_PER_SECOND)
        minZoomToFit.coerceIn(0.1f, 1.0f) // Ограничиваем минимальный зум
    } else {
        0.5f
    }
    
    // Обновляем порядок сегментов при перетаскивании
    LaunchedEffect(dragPosition, draggingSegmentId, reorderedSegments) {
        if (draggingSegmentId != null && currentDraggedIndex != -1) {
            // Вычисляем текущую позицию центра перетаскиваемого сегмента
            var accumulatedX = 0f
            val segmentSpacing = with(density) { 2.dp.toPx() }
            
            // Находим x-координату перетаскиваемого сегмента
            for (i in 0 until currentDraggedIndex) {
                accumulatedX += reorderedSegments[i].duration * PIXELS_PER_SECOND * effectiveZoom + segmentSpacing
            }
            
            val draggedSegment = reorderedSegments[currentDraggedIndex]
            val draggedWidth = draggedSegment.duration * PIXELS_PER_SECOND * effectiveZoom
            val draggedCenterX = accumulatedX + draggedWidth / 2 + dragPosition.x
            
            // Проверяем, нужно ли поменять местами с соседними сегментами
            var newIndex = currentDraggedIndex
            accumulatedX = 0f
            
            reorderedSegments.forEachIndexed { idx, segment ->
                if (idx != currentDraggedIndex) {
                    val segWidth = segment.duration * PIXELS_PER_SECOND * effectiveZoom
                    val segCenterX = accumulatedX + segWidth / 2
                    
                    // Если центр перетаскиваемого сегмента пересек центр другого сегмента
                    if (draggedCenterX > segCenterX && idx > currentDraggedIndex) {
                        newIndex = idx
                    } else if (draggedCenterX < segCenterX && idx < currentDraggedIndex) {
                        newIndex = idx
                        return@forEachIndexed
                    }
                }
                accumulatedX += reorderedSegments[idx].duration * PIXELS_PER_SECOND * effectiveZoom + segmentSpacing
            }
            
            // Если нужно переместить сегмент
            if (newIndex != currentDraggedIndex) {
                val mutableList = reorderedSegments.toMutableList()
                val movedSegment = mutableList.removeAt(currentDraggedIndex)
                mutableList.add(newIndex, movedSegment)
                reorderedSegments = mutableList
                currentDraggedIndex = newIndex
                
                android.util.Log.d("VideoTimeline", "Segments reordered: moved from $currentDraggedIndex to $newIndex")
            }
            
            // Автоскролл при приближении к краям
            val layoutInfo = lazyListState.layoutInfo
            val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val edgeThreshold = with(density) { 50.dp.toPx() }
            val scrollSpeed = 5f
            
            val fingerViewportX = draggedCenterX - lazyListState.firstVisibleItemScrollOffset
            
            when {
                fingerViewportX < edgeThreshold -> {
                    coroutineScope.launch {
                        lazyListState.scrollBy(-scrollSpeed)
                    }
                }
                fingerViewportX > viewportWidth.toFloat() - edgeThreshold -> {
                    coroutineScope.launch {
                        lazyListState.scrollBy(scrollSpeed)
                    }
                }
            }
        }
    }
    
    Column(modifier = modifier) {
        // Контролы масштабирования
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // Кнопка уменьшения масштаба
            IconButton(
                onClick = {
                    val currentIndex = ZOOM_LEVELS.indexOfFirst { it >= currentZoom }
                    if (currentIndex > 0) {
                        val newZoom = ZOOM_LEVELS[currentIndex - 1].coerceAtLeast(minZoom)
                        currentZoom = newZoom
                        onZoomChange(newZoom)
                    } else {
                        // Если уже на минимальном уровне из списка, используем вычисленный минимум
                        currentZoom = minZoom
                        onZoomChange(minZoom)
                    }
                },
                enabled = currentZoom > minZoom,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Уменьшить масштаб",
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Кнопка "показать все"
            IconButton(
                onClick = {
                    currentZoom = minZoom
                    onZoomChange(minZoom)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.FitScreen,
                    contentDescription = "Показать все сегменты",
                    modifier = Modifier.size(20.dp),
                    tint = if (currentZoom == minZoom) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        LocalContentColor.current
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Кнопка увеличения масштаба
            IconButton(
                onClick = {
                    val currentIndex = ZOOM_LEVELS.indexOfLast { it <= currentZoom }
                    if (currentIndex >= 0 && currentIndex < ZOOM_LEVELS.size - 1) {
                        val newZoom = ZOOM_LEVELS[currentIndex + 1]
                        currentZoom = newZoom
                        onZoomChange(newZoom)
                    }
                },
                enabled = currentZoom < ZOOM_LEVELS.last(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Увеличить масштаб",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Black.copy(alpha = 0.9f))
                .clip(RoundedCornerShape(8.dp))
                .onGloballyPositioned { coordinates ->
                    timelineWidth = coordinates.size.width.toFloat()
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            // При тапе вне сегментов снимаем выделение
                            // Это сработает только если тап не попал на сегмент
                            onSegmentClick(segments.firstOrNull { it.id == selectedSegmentId } ?: return@detectTapGestures)
                        }
                    )
                }
        ) {
            // Адаптивная линейка времени
            TimeRulerAdaptive(
                totalDuration = totalDuration,
                zoomLevel = effectiveZoom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .align(Alignment.TopCenter)
            )
            
            // Основной контент таймлайна
            LazyRow(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(top = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(
                    items = reorderedSegments,
                    key = { _, segment -> segment.id }
                ) { index, segment ->
                    VideoSegmentSimple(
                        segment = segment,
                        index = index,
                        isSelected = segment.id == selectedSegmentId,
                        isDragging = segment.id == draggingSegmentId,
                        zoomLevel = effectiveZoom,
                        onSegmentClick = { onSegmentClick(segment) },
                        onSegmentDelete = { onSegmentDelete(segment.id) },
                        onSegmentReorder = onSegmentReorder,
                        onSegmentTrim = { deltaTime, isStart ->
                            onSegmentTrim(segment.id, deltaTime, isStart)
                        },
                        onDragStateChange = { isDragging, offset ->
                            if (isDragging) {
                                draggingSegmentId = segment.id
                                dragPosition = offset
                                currentDraggedIndex = index
                                android.util.Log.d("VideoTimeline", "Drag started: segment=${segment.id}, index=$index")
                            } else {
                                // Применяем окончательную перестановку
                                if (currentDraggedIndex != -1) {
                                    val originalIndex = segments.indexOfFirst { it.id == segment.id }
                                    if (originalIndex != -1 && originalIndex != currentDraggedIndex) {
                                        android.util.Log.d("VideoTimeline", "Final reorder: from=$originalIndex to=$currentDraggedIndex")
                                        onSegmentReorder(originalIndex, currentDraggedIndex)
                                    }
                                }
                                
                                draggingSegmentId = null
                                dragPosition = Offset.Zero
                                currentDraggedIndex = -1
                                reorderedSegments = segments // Сбрасываем к оригинальному порядку
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSegmentSimple(
    segment: VideoSegment,
    index: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    zoomLevel: Float,
    onSegmentClick: () -> Unit,
    onSegmentDelete: () -> Unit,
    onSegmentReorder: (Int, Int) -> Unit,
    onSegmentTrim: (Float, Boolean) -> Unit,
    onDragStateChange: (Boolean, Offset) -> Unit
) {
    val density = LocalDensity.current
    
    // Состояние для drag
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Состояние для trim
    var isTrimming by remember { mutableStateOf(false) }
    var trimType by remember { mutableStateOf<TrimType?>(null) }
    var trimOffset by remember { mutableStateOf(0f) }
    var visualTrimOffset by remember { mutableStateOf(0f) } // Для визуальной обратной связи
    
    // Анимация
    val elevation by animateDpAsState(
        targetValue = when {
            isDragging -> 24.dp  // Увеличиваем тень при перетаскивании
            isSelected -> 8.dp
            else -> 2.dp
        },
        animationSpec = spring(),
        label = "elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,  // Увеличиваем масштаб при перетаскивании
        animationSpec = spring(),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 0.8f else 1f,  // Делаем полупрозрачным при перетаскивании
        animationSpec = spring(),
        label = "alpha"
    )
    
    // ВАЖНО: Базовая ширина сегмента НЕ МЕНЯЕТСЯ при триммировании
    // Это предотвращает сдвиг последующих сегментов
    val baseWidthPx = segment.duration * PIXELS_PER_SECOND * zoomLevel
    val segmentWidthDp = with(density) { baseWidthPx.toDp() }
    
    // Визуальная ширина для отображения обрезанного контента
    val visualWidth = if (isTrimming) {
        when (trimType) {
            TrimType.LEFT -> baseWidthPx - visualTrimOffset
            TrimType.RIGHT -> baseWidthPx + visualTrimOffset
            else -> baseWidthPx
        }
    } else {
        baseWidthPx
    }
    
    Box(
        modifier = Modifier
            .width(segmentWidthDp) // Фиксированная ширина, не меняется при триммировании
            .height(80.dp)
            .offset { 
                IntOffset(
                    if (isDragging) dragOffset.x.roundToInt() else 0,
                    if (isDragging) dragOffset.y.roundToInt() else 0
                )
            }
    ) {
        // Контейнер для визуального обрезания при триммировании левого края
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(with(density) { visualWidth.coerceAtLeast(20f).toDp() })
                .align(if (isTrimming && trimType == TrimType.LEFT) Alignment.CenterEnd else Alignment.CenterStart)
        ) {
            // Основной контейнер сегмента
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = if (isTrimming && trimType == TrimType.LEFT) with(density) { (-visualTrimOffset).toDp() } else 0.dp)
                    .shadow(elevation, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                    width = when {
                        isDragging -> 3.dp  // Толще обводка при перетаскивании
                        isSelected -> 2.dp
                        else -> 1.dp
                    },
                    color = when {
                        isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> Color.White.copy(alpha = 0.3f)
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .pointerInput(segment.id, isSelected) {
                    detectTapGestures(
                        onTap = {
                            // Выбираем сегмент по тапу всегда (toggle selection)
                            onSegmentClick()
                        }
                    )
                }
                .pointerInput(segment.id + "_drag", isSelected) {
                    if (!isSelected) return@pointerInput
                    
                    detectDragGestures(
                    onDragStart = { offset ->
                        
                        // Зона для ручек триммирования (уменьшена для удобства)
                        val trimHandleZone = 32.dp.toPx()
                        val leftHandleHit = offset.x < trimHandleZone
                        val rightHandleHit = offset.x > this.size.width - trimHandleZone
                        
                        when {
                            // В зоне триммирования - сразу начинаем триммировать
                            leftHandleHit -> {
                                isTrimming = true
                                trimType = TrimType.LEFT
                                trimOffset = 0f
                                visualTrimOffset = 0f
                            }
                            rightHandleHit -> {
                                isTrimming = true
                                trimType = TrimType.RIGHT
                                trimOffset = 0f
                                visualTrimOffset = 0f
                            }
                            // В центре - сразу начинаем drag без long press
                            else -> {
                                // Упрощаем логику - убираем long press
                                isDragging = true
                                dragOffset = Offset.Zero
                                onDragStateChange(true, Offset.Zero)
                            }
                        }
                    },
                    onDrag = { _, dragAmount ->
                        when {
                            isTrimming && trimType == TrimType.LEFT -> {
                                // Ограничиваем визуальное изменение, чтобы сегмент не выходил за пределы
                                val newVisualOffset = visualTrimOffset + dragAmount.x
                                val maxLeftMovement = segment.duration * PIXELS_PER_SECOND * zoomLevel - 20f // Минимум 20px ширина
                                val maxLeftExpansion = -segment.startTime * PIXELS_PER_SECOND * zoomLevel
                                
                                visualTrimOffset = newVisualOffset.coerceIn(maxLeftExpansion, maxLeftMovement)
                                trimOffset = visualTrimOffset
                            }
                            isTrimming && trimType == TrimType.RIGHT -> {
                                trimOffset += dragAmount.x
                                visualTrimOffset += dragAmount.x
                                // Только визуальная обратная связь, без вызова onSegmentTrim
                            }
                            isDragging -> {
                                dragOffset += dragAmount
                                // Передаем текущую позицию для отслеживания
                                onDragStateChange(true, dragOffset)
                            }
                        }
                    },
                    onDragEnd = {
                        when {
                            isDragging -> {
                                // Просто завершаем перетаскивание - перестановка уже произошла в родительском компоненте
                                android.util.Log.d("VideoTimeline", "Drag end for segment at index=$index")
                                
                                isDragging = false
                                dragOffset = Offset.Zero
                                onDragStateChange(false, Offset.Zero)
                            }
                            isTrimming && trimType == TrimType.LEFT -> {
                                // Применяем изменения только в конце
                                val deltaTime = trimOffset / (PIXELS_PER_SECOND * zoomLevel)
                                // Ограничиваем trim чтобы сегмент не двигался вправо
                                val maxTrim = segment.duration - 0.5f
                                val clampedDelta = deltaTime.coerceIn(-segment.startTime, maxTrim)
                                if (abs(clampedDelta) > 0.01f) {
                                    onSegmentTrim(clampedDelta, true)
                                }
                                isTrimming = false
                                trimType = null
                                trimOffset = 0f
                                visualTrimOffset = 0f
                            }
                            isTrimming && trimType == TrimType.RIGHT -> {
                                // Применяем изменения только в конце
                                val deltaTime = trimOffset / (PIXELS_PER_SECOND * zoomLevel)
                                val maxExpansion = segment.originalDuration - segment.endTime
                                val maxTrim = -(segment.duration - 0.5f)
                                val clampedDelta = deltaTime.coerceIn(maxTrim, maxExpansion)
                                if (abs(clampedDelta) > 0.01f) {
                                    onSegmentTrim(clampedDelta, false)
                                }
                                isTrimming = false
                                trimType = null
                                trimOffset = 0f
                                visualTrimOffset = 0f
                            }
                        }
                    }
                )
                }
        ) {
            // Превью видео
            segment.thumbnails.firstOrNull()?.let { thumbnail ->
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.7f
            )
        }
        
        // Эффект при перетаскивании
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
        
        // Длительность сегмента с учетом визуального триммирования
        val displayDuration = if (isTrimming) {
            val deltaTime = visualTrimOffset / (PIXELS_PER_SECOND * zoomLevel)
            when (trimType) {
                TrimType.LEFT -> (segment.duration - deltaTime).coerceAtLeast(0.5f)
                TrimType.RIGHT -> (segment.duration + deltaTime).coerceAtLeast(0.5f)
                else -> segment.duration
            }
        } else {
            segment.duration
        }
        
        Text(
            text = "${displayDuration.roundToInt()}s",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
            } // Закрываем основной контейнер сегмента
        } // Закрываем контейнер для визуального обрезания
        
        // Визуальные индикаторы триммирования для выделенного сегмента
        if (isSelected) {
            // Левая ручка для триммирования
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-12).dp) // Выносим ручку за пределы сегмента
                    .size(width = 24.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isTrimming && trimType == TrimType.LEFT)
                            Color.White
                        else Color.White.copy(alpha = 0.9f)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = "Обрезать слева",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black.copy(alpha = 0.7f)
                )
            }
            
            // Правая ручка для триммирования
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp) // Выносим ручку за пределы сегмента
                    .size(width = 24.dp, height = 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isTrimming && trimType == TrimType.RIGHT)
                            Color.White
                        else Color.White.copy(alpha = 0.9f)
                    )
                    .border(
                        width = 1.dp,
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Обрезать справа",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Black.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private enum class TrimType {
    LEFT, RIGHT
}

@Composable
private fun TimeRulerAdaptive(
    totalDuration: Float,
    zoomLevel: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Определяем интервал между засечками в зависимости от масштаба
        val tickInterval = when {
            zoomLevel >= 3.0f -> 0.1f  // Десятые доли секунды
            zoomLevel >= 2.0f -> 0.5f  // Полсекунды
            zoomLevel >= 1.5f -> 1.0f  // Секунды
            zoomLevel >= 1.0f -> 5.0f  // 5 секунд
            zoomLevel >= 0.75f -> 10.0f // 10 секунд
            else -> 30.0f  // 30 секунд
        }
        
        val labelInterval = when {
            zoomLevel >= 3.0f -> 1.0f
            zoomLevel >= 2.0f -> 5.0f
            zoomLevel >= 1.5f -> 10.0f
            zoomLevel >= 1.0f -> 30.0f
            else -> 60.0f  // Минуты
        }
        
        // Рисуем засечки
        var currentTime = 0f
        while (currentTime <= totalDuration) {
            val x = currentTime * PIXELS_PER_SECOND * zoomLevel + with(density) { 16.dp.toPx() }
            
            if (x > width) break
            
            val isMainTick = currentTime % labelInterval < 0.01f
            val tickHeight = if (isMainTick) with(density) { 15.dp.toPx() } else with(density) { 8.dp.toPx() }
            
            drawLine(
                color = Color.White.copy(alpha = if (isMainTick) 0.8f else 0.4f),
                start = Offset(x, height),
                end = Offset(x, height - tickHeight),
                strokeWidth = with(density) { 1.dp.toPx() }
            )
            
            // Подписи для основных засечек
            if (isMainTick && currentTime > 0) {
                drawIntoCanvas { canvas ->
                    val text = formatTime(currentTime)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = with(density) { 10.sp.toPx() }
                        alpha = 200
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    canvas.nativeCanvas.drawText(text, x, height - with(density) { 17.dp.toPx() }, paint)
                }
            }
            
            currentTime += tickInterval
        }
    }
}

private fun formatTime(seconds: Float): String {
    return when {
        seconds < 10 -> "%.1fs".format(seconds)
        seconds < 60 -> "${seconds.toInt()}s"
        else -> {
            val minutes = seconds.toInt() / 60
            val secs = seconds.toInt() % 60
            if (secs == 0) "${minutes}m" else "${minutes}m${secs}s"
        }
    }
}