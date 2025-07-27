package com.example.clipcraft.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.clipcraft.models.VideoSegment
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Современный видео таймлайн с поддержкой:
 * - Drag and drop для изменения порядка
 * - Изменение размера сегментов
 * - Добавление и удаление сегментов
 * - Плавная анимация
 * - Предпросмотр
 */
// Единая константа для всех преобразований
private const val PIXELS_PER_SECOND = 100f

// Типы жестов
private enum class GestureType {
    NONE,
    PINCH_ZOOM,
    TRIM_LEFT,
    TRIM_RIGHT,
    DRAG_SEGMENT,
    SCROLL_TIMELINE,
    TAP
}

// Состояние для отслеживания активных жестов
private data class GestureState(
    val type: GestureType = GestureType.NONE,
    val startOffset: Offset = Offset.Zero,
    val activePointers: Int = 0,
    val targetSegmentId: String? = null,
    val initialZoom: Float = 1f
)

@Composable
fun VideoTimeline(
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
    android.util.Log.d("VideoTimeline", "Rendering with ${segments.size} segments, zoom=$zoomLevel")
    val density = LocalDensity.current
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    // Calculate timeline width for proper scaling
    var timelineWidthPx by remember { mutableStateOf(0f) }
    val timelineWidthDp = with(density) { timelineWidthPx.toDp() }
    
    // Состояние для drag and drop
    var draggedSegment by remember { mutableStateOf<VideoSegment?>(null) }
    var draggedFromIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTargetIndex by remember { mutableStateOf(-1) }
    var currentDragOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Состояние для триммирования
    var trimOffsets by remember { mutableStateOf(mutableMapOf<String, Pair<Float, Float>>()) }
    
    // Состояние для pinch-to-zoom
    var currentZoom by remember { mutableStateOf(zoomLevel) }
    
    // Состояние для отслеживания жестов
    var activeGesture by remember { mutableStateOf<GestureType?>(null) }
    var pointerCount by remember { mutableStateOf(0) }
    
    // Фиксированные лимиты зума без привязки к ширине таймлайна
    val minZoom = 0.5f  // 50% от базового размера
    val maxZoom = 5.0f  // 500% от базового размера
    
    // Обновляем текущий зум при изменении внешнего значения
    LaunchedEffect(zoomLevel) {
        currentZoom = zoomLevel
    }
    
    // Состояние жестов
    var gestureState by remember { mutableStateOf(GestureState()) }
    var pinchCenter by remember { mutableStateOf(Offset.Zero) }
    
    // Линейка времени сверху - закомментирована по запросу пользователя
    val totalDuration = segments.sumOf { it.duration.toDouble() }.toFloat()
    android.util.Log.d("VideoTimeline", "Total duration: $totalDuration")
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Color.Black.copy(alpha = 0.9f))
            .clip(RoundedCornerShape(8.dp))
            .onGloballyPositioned { coordinates ->
                timelineWidthPx = coordinates.size.width.toFloat()
            }
            .pointerInput(segments, currentZoom, selectedSegmentId) {
                coroutineScope {
                    // Обработчик pinch zoom
                    launch {
                        detectTransformGestures(
                            onGesture = { _, _, zoom, _ ->
                                android.util.Log.d("VideoTimeline", "Pinch zoom: $currentZoom * $zoom")
                                val newZoom = (currentZoom * zoom).coerceIn(minZoom, maxZoom)
                                currentZoom = newZoom
                                onZoomChange(newZoom)
                                gestureState = gestureState.copy(type = GestureType.PINCH_ZOOM)
                            }
                        )
                    }
                    
                    // Обработчик tap и drag
                    launch {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val targetSegment = findSegmentAtPosition(offset, segments, density, currentZoom, totalDuration, timelineWidthDp, selectedSegmentId)
                                
                                when {
                                    targetSegment == null -> {
                                        // Не потребляем, даем LazyRow скроллить
                                        gestureState = gestureState.copy(type = GestureType.SCROLL_TIMELINE)
                                    }
                                    !targetSegment.isSelected -> {
                                        // Невыделенный сегмент - скроллим
                                        gestureState = gestureState.copy(type = GestureType.SCROLL_TIMELINE)
                                    }
                                    else -> {
                                        val relativeX = offset.x - targetSegment.offset
                                        when {
                                            relativeX < 48.dp.toPx() -> {
                                                gestureState = gestureState.copy(
                                                    type = GestureType.TRIM_LEFT,
                                                    targetSegmentId = targetSegment.id
                                                )
                                                activeGesture = GestureType.TRIM_LEFT
                                            }
                                            relativeX > targetSegment.width - 48.dp.toPx() -> {
                                                gestureState = gestureState.copy(
                                                    type = GestureType.TRIM_RIGHT,
                                                    targetSegmentId = targetSegment.id
                                                )
                                                activeGesture = GestureType.TRIM_RIGHT
                                            }
                                            else -> {
                                                gestureState = gestureState.copy(
                                                    type = GestureType.DRAG_SEGMENT,
                                                    targetSegmentId = targetSegment.id
                                                )
                                                activeGesture = GestureType.DRAG_SEGMENT
                                                draggedSegment = targetSegment.segment
                                                draggedFromIndex = targetSegment.index
                                            }
                                        }
                                    }
                                }
                            },
                            onDrag = { _, dragAmount ->
                                when (gestureState.type) {
                                    GestureType.DRAG_SEGMENT -> {
                                        currentDragOffset += dragAmount
                                        
                                        // Определяем drop target
                                        gestureState.targetSegmentId?.let { id ->
                                            val segIndex = segments.indexOfFirst { it.id == id }
                                            if (segIndex != -1) {
                                                val segmentWidthPx = with(density) {
                                                    (segments[segIndex].duration * PIXELS_PER_SECOND * currentZoom).dp.toPx()
                                                }
                                                val thresholdPx = maxOf(40.dp.toPx(), segmentWidthPx / 3f)
                                                
                                                var targetIndex = segIndex
                                                if (currentDragOffset.x < -thresholdPx && segIndex > 0) {
                                                    targetIndex = segIndex - 1
                                                } else if (currentDragOffset.x > thresholdPx && segIndex < segments.size - 1) {
                                                    targetIndex = segIndex + 1
                                                }
                                                
                                                dropTargetIndex = targetIndex
                                            }
                                        }
                                    }
                                    GestureType.TRIM_LEFT -> {
                                        gestureState.targetSegmentId?.let { id ->
                                            val segment = segments.find { it.id == id }
                                            segment?.let { seg ->
                                                val currentTrimOffset = trimOffsets[id]?.first ?: 0f
                                                val newOffset = currentTrimOffset + dragAmount.x
                                                
                                                val maxLeftExpansion = -seg.startTime * PIXELS_PER_SECOND * currentZoom
                                                val maxRightMovement = (seg.duration - 0.5f) * PIXELS_PER_SECOND * currentZoom
                                                
                                                val clampedOffset = newOffset.coerceIn(maxLeftExpansion, maxRightMovement)
                                                trimOffsets[id] = (clampedOffset to (trimOffsets[id]?.second ?: 0f))
                                            }
                                        }
                                    }
                                    GestureType.TRIM_RIGHT -> {
                                        gestureState.targetSegmentId?.let { id ->
                                            val segment = segments.find { it.id == id }
                                            segment?.let { seg ->
                                                val currentTrimOffset = trimOffsets[id]?.second ?: 0f
                                                val newOffset = currentTrimOffset + dragAmount.x
                                                
                                                val availableExpansionRight = seg.originalDuration - seg.endTime
                                                val maxRightExpansion = availableExpansionRight * PIXELS_PER_SECOND * currentZoom
                                                val maxLeftMovement = -(seg.duration - 0.5f) * PIXELS_PER_SECOND * currentZoom
                                                
                                                val clampedOffset = newOffset.coerceIn(maxLeftMovement, maxRightExpansion)
                                                trimOffsets[id] = ((trimOffsets[id]?.first ?: 0f) to clampedOffset)
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                when (gestureState.type) {
                                    GestureType.DRAG_SEGMENT -> {
                                        if (dropTargetIndex != -1 && dropTargetIndex != draggedFromIndex) {
                                            onSegmentReorder(draggedFromIndex, dropTargetIndex)
                                        }
                                        draggedSegment = null
                                        draggedFromIndex = -1
                                        currentDragOffset = Offset.Zero
                                        dropTargetIndex = -1
                                    }
                                    GestureType.TRIM_LEFT -> {
                                        gestureState.targetSegmentId?.let { id ->
                                            trimOffsets[id]?.first?.let { offset ->
                                                val deltaTime = offset / (PIXELS_PER_SECOND * currentZoom)
                                                onSegmentTrim(id, deltaTime, true)
                                            }
                                            trimOffsets.remove(id)
                                        }
                                    }
                                    GestureType.TRIM_RIGHT -> {
                                        gestureState.targetSegmentId?.let { id ->
                                            trimOffsets[id]?.second?.let { offset ->
                                                val deltaTime = offset / (PIXELS_PER_SECOND * currentZoom)
                                                onSegmentTrim(id, deltaTime, false)
                                            }
                                            trimOffsets.remove(id)
                                        }
                                    }
                                    else -> {}
                                }
                                
                                gestureState = GestureState()
                                activeGesture = GestureType.NONE
                            }
                        )
                    }
                    
                    // Обработчик tap
                    launch {
                        detectTapGestures(
                            onTap = { offset ->
                                val targetSegment = findSegmentAtPosition(offset, segments, density, currentZoom, totalDuration, timelineWidthDp, selectedSegmentId)
                                if (targetSegment != null) {
                                    onSegmentClick(targetSegment.segment)
                                }
                            }
                        )
                    }
                }
            }
    ) {
        // TimeRuler убрана - белые риски больше не показываются
        /*
        TimeRuler(
            totalDuration = totalDuration,
            zoomLevel = zoomLevel,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .align(Alignment.TopCenter)
        )
        */
        
        
        // Основной контент таймлайна
        LazyRow(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = true
        ) {
            itemsIndexed(
                items = segments,
                key = { _, segment -> segment.id }
            ) { index, segment ->
                VideoSegmentItem(
                    segment = segment,
                    index = index,
                    isSelected = segment.id == selectedSegmentId,
                    isDragging = segment.id == gestureState.targetSegmentId && gestureState.type == GestureType.DRAG_SEGMENT,
                    isDropTarget = index == dropTargetIndex,
                    zoomLevel = zoomLevel,
                    totalDuration = totalDuration,
                    timelineWidthDp = timelineWidthDp,
                    dragOffset = if (segment.id == gestureState.targetSegmentId && gestureState.type == GestureType.DRAG_SEGMENT) currentDragOffset else Offset.Zero,
                    activeGesture = if (segment.id == gestureState.targetSegmentId) gestureState.type else GestureType.NONE,
                    leftTrimOffset = trimOffsets[segment.id]?.first ?: 0f,
                    rightTrimOffset = trimOffsets[segment.id]?.second ?: 0f,
                    onActiveGestureChange = { /* Обрабатывается в едином GestureDetector */ },
                    onSegmentClick = { /* Обрабатывается в GestureDetector */ },
                    onSegmentDelete = { onSegmentDelete(segment.id) },
                    onDragStart = { /* Обрабатывается в GestureDetector */ },
                    onDrag = { offset ->
                        if (gestureState.type == GestureType.DRAG_SEGMENT && segment.id == gestureState.targetSegmentId) {
                            currentDragOffset = offset
                        }
                    },
                    onDragEnd = { /* Обрабатывается в GestureDetector */ },
                    onTrimStart = { delta ->
                        if (gestureState.type == GestureType.TRIM_LEFT && segment.id == gestureState.targetSegmentId) {
                            onSegmentTrim(segment.id, delta, true)
                        }
                    },
                    onTrimEnd = { delta ->
                        if (gestureState.type == GestureType.TRIM_RIGHT && segment.id == gestureState.targetSegmentId) {
                            onSegmentTrim(segment.id, delta, false)
                        }
                    }
                )
            }
        }
        
        // Маркер времени убран из таймлайна редактора
    }
}

/**
 * Компонент отдельного сегмента видео
 */
@Composable
private fun VideoSegmentItem(
    segment: VideoSegment,
    index: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    zoomLevel: Float,
    totalDuration: Float,
    timelineWidthDp: androidx.compose.ui.unit.Dp,
    dragOffset: Offset = Offset.Zero,
    activeGesture: GestureType?,
    leftTrimOffset: Float = 0f,
    rightTrimOffset: Float = 0f,
    onActiveGestureChange: (GestureType?) -> Unit,
    onSegmentClick: () -> Unit,
    onSegmentDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTrimStart: (Float) -> Unit,
    onTrimEnd: (Float) -> Unit
) {
    val density = LocalDensity.current
    var segmentWidth by remember { mutableStateOf(0f) }
    
    // Анимация для выделения и перетаскивания
    val elevation by animateDpAsState(
        targetValue = when {
            isDragging -> 16.dp
            isSelected -> 8.dp
            else -> 2.dp
        },
        label = "elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.1f
            isDropTarget -> 0.95f
            else -> 1f
        },
        label = "scale"
    )
    
    // Текущая и максимальная длительность
    val currentDuration = segment.duration
    val maxDuration = segment.originalDuration
    val availableExpansionLeft = segment.startTime
    val availableExpansionRight = maxDuration - segment.endTime
    
    // Вычисляем ширину с учетом временного изменения
    val visualDuration = currentDuration + (rightTrimOffset - leftTrimOffset) / (PIXELS_PER_SECOND * zoomLevel)
    
    val baseWidthPx = visualDuration * PIXELS_PER_SECOND * zoomLevel
    val calculatedWidth = with(density) { baseWidthPx.toDp() }
    
    Box(
        modifier = Modifier
            .width(calculatedWidth)
            .height(80.dp)
    ) {
        // Визуальный индикатор drop target
        AnimatedVisibility(
            visible = isDropTarget && !isDragging,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .shadow(elevation, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.surface
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                           else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (isDragging) 0.85f else 1f
                }
                .onGloballyPositioned { coordinates ->
                    segmentWidth = coordinates.size.width.toFloat()
                }
        ) {
            // Превью видео с учетом обрезки
            Box(modifier = Modifier.fillMaxSize()) {
                segment.thumbnails.firstOrNull()?.let { thumbnail ->
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.7f
                    )
                }
                
                // Убираем визуальные маски - теперь сегмент реально изменяет размер
            }
            
            // Эффект яркости при перетаскивании
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.2f))
                )
            }
            
            // Полупрозрачная версия для места назначения
            if (isDropTarget && isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.1f))
                )
            }
            
            // Кнопка удаления (только для выделенного)
            if (isSelected) {
                IconButton(
                    onClick = onSegmentDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.7f))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // Длительность сегмента
            Text(
                text = "${segment.duration.roundToInt()}s",
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
            
            // Кнопка перетаскивания по центру (только для выделенного)
            if (isSelected) {
                val isDragging = activeGesture == GestureType.DRAG_SEGMENT
                
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp),  // Увеличенная зона касания
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDragging)
                                        MaterialTheme.colorScheme.primary
                                    else 
                                        Color.Black.copy(alpha = 0.6f)
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (isDragging) Color.White else Color.White.copy(alpha = 0.5f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.DragHandle,
                                contentDescription = "Перетащить",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            
            // Невидимые зоны для изменения размера (только для выделенного сегмента)
            if (isSelected) {
                // Состояние для визуальной обратной связи
                val isTrimmingLeft = activeGesture == GestureType.TRIM_LEFT
                val isTrimmingRight = activeGesture == GestureType.TRIM_RIGHT
                
                // Левая зона для изменения размера с увеличенной областью касания
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(48.dp)  // Увеличенная зона касания
                        .fillMaxHeight()
                        .offset(x = with(density) { (leftTrimOffset - 8.dp.toPx()).toDp() })  // Смещаем зону влево для удобства
                )
                
                // Правая зона для изменения размера с увеличенной областью касания
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(48.dp)  // Увеличенная зона касания
                        .fillMaxHeight()
                        .offset(x = with(density) { (rightTrimOffset - 8.dp.toPx()).toDp() })  // Смещаем зону для удобства
                )
                
                // Визуальные индикаторы при триммировании (опциональные)
                if (isTrimmingLeft) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(4.dp)
                            .fillMaxHeight()
                            .offset(x = with(density) { leftTrimOffset.toDp() })
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                
                if (isTrimmingRight) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(4.dp)
                            .fillMaxHeight()
                            .offset(x = with(density) { rightTrimOffset.toDp() })
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

/**
 * Ручка для изменения размера сегмента
 */
@Composable
private fun TrimHandle(
    isStart: Boolean,
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .width(32.dp)  // Увеличенная зона касания
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDrag = { _, dragAmount ->
                        onDrag(dragAmount.x)
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd()
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(if (isStart) Alignment.CenterStart else Alignment.CenterEnd)
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.3f)
                )
        )
    }
}

/**
 * Data class для информации о сегменте
 */
private data class SegmentInfo(
    val segment: VideoSegment,
    val index: Int,
    val offset: Float,
    val width: Float,
    val id: String,
    val isSelected: Boolean
)

/**
 * Функция для поиска сегмента по позиции
 */
private fun findSegmentAtPosition(
    position: Offset,
    segments: List<VideoSegment>,
    density: androidx.compose.ui.unit.Density,
    zoomLevel: Float,
    totalDuration: Float,
    timelineWidthDp: androidx.compose.ui.unit.Dp,
    selectedSegmentId: String?
): SegmentInfo? {
    var currentX = with(density) { 16.dp.toPx() }  // Начальный отступ
    
    segments.forEachIndexed { index, segment ->
        val segmentWidthPx = segment.duration * PIXELS_PER_SECOND * zoomLevel
        
        if (position.x >= currentX && position.x <= currentX + segmentWidthPx) {
            return SegmentInfo(
                segment = segment,
                index = index,
                offset = currentX,
                width = segmentWidthPx,
                id = segment.id,
                isSelected = segment.id == selectedSegmentId
            )
        }
        
        currentX += segmentWidthPx + with(density) { 2.dp.toPx() }  // Добавляем промежуток
    }
    
    return null
}

/**
 * Компонент линейки времени
 * (Закомментирован по запросу пользователя)
 */
/*
@Composable
private fun TimeRuler(
    totalDuration: Float,
    zoomLevel: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier
    ) {
        val baseMarkerSpacing = PIXELS_PER_SECOND * zoomLevel  // Маркеры каждую секунду
        
        // Рисуем маркеры
        var currentX = 0f
        var currentTime = 0f
        
        while (currentTime <= totalDuration) {
            val markerHeight = when {
                currentTime.toInt() % 5 == 0 -> 20.dp.toPx()  // Каждые 5 секунд - длинные
                else -> 10.dp.toPx()  // Каждую секунду - короткие
            }
            
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(currentX, size.height),
                end = Offset(currentX, size.height - markerHeight),
                strokeWidth = 1.dp.toPx()
            )
            
            // Подписи времени каждые 5 секунд
            if (currentTime.toInt() % 5 == 0) {
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        "${currentTime.toInt()}s",
                        currentX + 4.dp.toPx(),
                        size.height - 22.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 10.sp.toPx()
                            alpha = 153  // 60% opacity
                        }
                    )
                }
            }
            
            currentX += baseMarkerSpacing
            currentTime += 1f
        }
    }
}
*/