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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import coil.compose.AsyncImage
import com.example.clipcraft.models.VideoSegment
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.abs

// Единая константа для всех преобразований
private const val PIXELS_PER_SECOND = 100f

// Типы жестов для новой версии таймлайна
private enum class GestureTypeNew {
    NONE,
    TRIM_LEFT,
    TRIM_RIGHT,
    DRAG_SEGMENT,
    SCROLL_TIMELINE,
    TAP
}

// Состояние для отслеживания активных жестов в новой версии
private data class GestureStateNew(
    val type: GestureTypeNew = GestureTypeNew.NONE,
    val startOffset: Offset = Offset.Zero,
    val targetSegmentId: String? = null
)

// Дискретные уровни масштаба
private val ZOOM_LEVELS = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)

@Composable
fun VideoTimelineNew(
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
    
    // Calculate timeline width for proper scaling
    var timelineWidthPx by remember { mutableStateOf(0f) }
    val timelineWidthDp = with(density) { timelineWidthPx.toDp() }
    
    // Состояние для drag and drop
    var draggedSegment by remember { mutableStateOf<VideoSegment?>(null) }
    var draggedFromIndex by remember { mutableStateOf(-1) }
    var dropTargetIndex by remember { mutableStateOf(-1) }
    var currentDragOffset by remember { mutableStateOf(Offset.Zero) }
    
    // Состояние для триммирования с визуальной обратной связью
    var trimOffsets by remember { mutableStateOf(mutableMapOf<String, Pair<Float, Float>>()) }
    var activeTrimSegment by remember { mutableStateOf<String?>(null) }
    
    // Визуальные индикаторы для немедленной обратной связи
    var isDraggingTrim by remember { mutableStateOf(false) }
    
    // Текущий уровень масштаба
    var currentZoom by remember { mutableStateOf(zoomLevel) }
    
    // Обновляем текущий зум при изменении внешнего значения
    LaunchedEffect(zoomLevel) {
        currentZoom = zoomLevel
    }
    
    // Состояние жестов
    var gestureState by remember { mutableStateOf(GestureStateNew()) }
    
    // Общая длительность
    val totalDuration = segments.sumOf { it.duration.toDouble() }.toFloat()
    
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
                    val currentIndex = ZOOM_LEVELS.indexOf(currentZoom)
                    if (currentIndex > 0) {
                        val newZoom = ZOOM_LEVELS[currentIndex - 1]
                        currentZoom = newZoom
                        onZoomChange(newZoom)
                    }
                },
                enabled = currentZoom > ZOOM_LEVELS.first(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Уменьшить масштаб",
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Кнопка увеличения масштаба
            IconButton(
                onClick = {
                    val currentIndex = ZOOM_LEVELS.indexOf(currentZoom)
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
                    timelineWidthPx = coordinates.size.width.toFloat()
                }
        ) {
            // Линейка времени с адаптивными засечками
            TimeRulerAdaptive(
                totalDuration = totalDuration,
                zoomLevel = currentZoom,
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
                    .padding(top = 30.dp)
                    .pointerInput(segments, currentZoom, selectedSegmentId) {
                        coroutineScope {
                            // Обработчик drag для сегментов
                            launch {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val adjustedOffset = offset.copy(y = offset.y - 30.dp.toPx())
                                        val targetSegment = findSegmentAtPositionNew(
                                            adjustedOffset, segments, density, currentZoom, 
                                            totalDuration, timelineWidthDp, selectedSegmentId
                                        )
                                        
                                        when {
                                            targetSegment == null -> {
                                                gestureState = gestureState.copy(type = GestureTypeNew.SCROLL_TIMELINE)
                                            }
                                            !targetSegment.isSelected -> {
                                                // Сначала выделяем сегмент
                                                onSegmentClick(targetSegment.segment)
                                            }
                                            else -> {
                                                val relativeX = adjustedOffset.x - targetSegment.offset
                                                val edgeThreshold = 32.dp.toPx()
                                                
                                                when {
                                                    relativeX < edgeThreshold -> {
                                                        // Начинаем триммирование левого края
                                                        gestureState = gestureState.copy(
                                                            type = GestureTypeNew.TRIM_LEFT,
                                                            targetSegmentId = targetSegment.id,
                                                            startOffset = adjustedOffset
                                                        )
                                                        activeTrimSegment = targetSegment.id
                                                        isDraggingTrim = true
                                                        // Инициализируем начальное смещение если его нет
                                                        if (trimOffsets[targetSegment.id] == null) {
                                                            trimOffsets[targetSegment.id] = (0f to 0f)
                                                        }
                                                    }
                                                    relativeX > targetSegment.width - edgeThreshold -> {
                                                        // Начинаем триммирование правого края
                                                        gestureState = gestureState.copy(
                                                            type = GestureTypeNew.TRIM_RIGHT,
                                                            targetSegmentId = targetSegment.id,
                                                            startOffset = adjustedOffset
                                                        )
                                                        activeTrimSegment = targetSegment.id
                                                        isDraggingTrim = true
                                                        // Инициализируем начальное смещение если его нет
                                                        if (trimOffsets[targetSegment.id] == null) {
                                                            trimOffsets[targetSegment.id] = (0f to 0f)
                                                        }
                                                    }
                                                    else -> {
                                                        // Начинаем перетаскивание мгновенно
                                                        gestureState = gestureState.copy(
                                                            type = GestureTypeNew.DRAG_SEGMENT,
                                                            targetSegmentId = targetSegment.id,
                                                            startOffset = adjustedOffset
                                                        )
                                                        draggedSegment = targetSegment.segment
                                                        draggedFromIndex = targetSegment.index
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onDrag = { _, dragAmount ->
                                        when (gestureState.type) {
                                            GestureTypeNew.NONE -> {
                                                // Ничего не делаем для NONE состояния
                                            }
                                            GestureTypeNew.DRAG_SEGMENT -> {
                                                currentDragOffset += dragAmount
                                                
                                                // Определяем целевую позицию для вставки
                                                gestureState.targetSegmentId?.let { id ->
                                                    val segIndex = segments.indexOfFirst { it.id == id }
                                                    if (segIndex != -1) {
                                                        val segmentWidthPx = segments[segIndex].duration * PIXELS_PER_SECOND * currentZoom
                                                        val thresholdPx = segmentWidthPx / 2
                                                        
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
                                            GestureTypeNew.TRIM_LEFT -> {
                                                gestureState.targetSegmentId?.let { id ->
                                                    val segment = segments.find { it.id == id }
                                                    segment?.let { seg ->
                                                        val currentTrimOffset = trimOffsets[id]?.first ?: 0f
                                                        val newOffset = currentTrimOffset + dragAmount.x
                                                        
                                                        // Лимиты триммирования
                                                        val maxLeftExpansion = -seg.startTime * PIXELS_PER_SECOND * currentZoom
                                                        val maxRightMovement = (seg.duration - 0.5f) * PIXELS_PER_SECOND * currentZoom
                                                        
                                                        val clampedOffset = newOffset.coerceIn(maxLeftExpansion, maxRightMovement)
                                                        trimOffsets[id] = (clampedOffset to (trimOffsets[id]?.second ?: 0f))
                                                    }
                                                }
                                            }
                                            GestureTypeNew.TRIM_RIGHT -> {
                                                gestureState.targetSegmentId?.let { id ->
                                                    val segment = segments.find { it.id == id }
                                                    segment?.let { seg ->
                                                        val currentTrimOffset = trimOffsets[id]?.second ?: 0f
                                                        val newOffset = currentTrimOffset + dragAmount.x
                                                        
                                                        // Лимиты триммирования
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
                                            GestureTypeNew.DRAG_SEGMENT -> {
                                                if (dropTargetIndex != -1 && dropTargetIndex != draggedFromIndex) {
                                                    onSegmentReorder(draggedFromIndex, dropTargetIndex)
                                                }
                                                draggedSegment = null
                                                draggedFromIndex = -1
                                                currentDragOffset = Offset.Zero
                                                dropTargetIndex = -1
                                            }
                                            GestureTypeNew.TRIM_LEFT -> {
                                                gestureState.targetSegmentId?.let { id ->
                                                    trimOffsets[id]?.first?.let { offset ->
                                                        val deltaTime = offset / (PIXELS_PER_SECOND * currentZoom)
                                                        onSegmentTrim(id, deltaTime, true)
                                                    }
                                                    trimOffsets.remove(id)
                                                }
                                                activeTrimSegment = null
                                                isDraggingTrim = false
                                            }
                                            GestureTypeNew.TRIM_RIGHT -> {
                                                gestureState.targetSegmentId?.let { id ->
                                                    trimOffsets[id]?.second?.let { offset ->
                                                        val deltaTime = offset / (PIXELS_PER_SECOND * currentZoom)
                                                        onSegmentTrim(id, deltaTime, false)
                                                    }
                                                    trimOffsets.remove(id)
                                                }
                                                activeTrimSegment = null
                                                isDraggingTrim = false
                                            }
                                            else -> {}
                                        }
                                        
                                        gestureState = GestureStateNew()
                                    }
                                )
                            }
                            
                            // Обработчик tap для выделения
                            launch {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val adjustedOffset = offset.copy(y = offset.y - 30.dp.toPx())
                                        val targetSegment = findSegmentAtPositionNew(
                                            adjustedOffset, segments, density, currentZoom,
                                            totalDuration, timelineWidthDp, selectedSegmentId
                                        )
                                        targetSegment?.let {
                                            onSegmentClick(it.segment)
                                        }
                                    }
                                )
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                userScrollEnabled = gestureState.type == GestureTypeNew.SCROLL_TIMELINE || gestureState.type == GestureTypeNew.NONE
            ) {
                itemsIndexed(
                    items = segments,
                    key = { _, segment -> segment.id }
                ) { index, segment ->
                    VideoSegmentItemNew(
                        segment = segment,
                        index = index,
                        isSelected = segment.id == selectedSegmentId,
                        isDragging = segment.id == gestureState.targetSegmentId && gestureState.type == GestureTypeNew.DRAG_SEGMENT,
                        isDropTarget = index == dropTargetIndex && draggedSegment != null,
                        zoomLevel = currentZoom,
                        dragOffset = if (segment.id == gestureState.targetSegmentId && gestureState.type == GestureTypeNew.DRAG_SEGMENT) currentDragOffset else Offset.Zero,
                        activeGesture = if (segment.id == gestureState.targetSegmentId) gestureState.type else GestureTypeNew.NONE,
                        leftTrimOffset = trimOffsets[segment.id]?.first ?: 0f,
                        rightTrimOffset = trimOffsets[segment.id]?.second ?: 0f,
                        isTrimming = segment.id == activeTrimSegment,
                        showTrimHandles = segment.id == selectedSegmentId  // Показываем ручки для выделенного сегмента
                    )
                }
            }
        }
    }
}

/**
 * Адаптивная линейка времени с засечками
 */
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
            val x = currentTime * PIXELS_PER_SECOND * zoomLevel + 16.dp.toPx()
            
            if (x > width) break
            
            val isMainTick = currentTime % labelInterval < 0.01f
            val tickHeight = if (isMainTick) 15.dp.toPx() else 8.dp.toPx()
            
            drawLine(
                color = Color.White.copy(alpha = if (isMainTick) 0.8f else 0.4f),
                start = Offset(x, height),
                end = Offset(x, height - tickHeight),
                strokeWidth = 1.dp.toPx()
            )
            
            // Подписи для основных засечек
            if (isMainTick && currentTime > 0) {
                drawIntoCanvas { canvas ->
                    val text = formatTime(currentTime)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 10.sp.toPx()
                        alpha = 200
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    canvas.nativeCanvas.drawText(text, x, height - 17.dp.toPx(), paint)
                }
            }
            
            currentTime += tickInterval
        }
    }
}

/**
 * Форматирование времени для отображения
 */
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

/**
 * Компонент отдельного сегмента видео
 */
@Composable
private fun VideoSegmentItemNew(
    segment: VideoSegment,
    index: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    isDropTarget: Boolean,
    zoomLevel: Float,
    dragOffset: Offset = Offset.Zero,
    activeGesture: GestureTypeNew?,
    leftTrimOffset: Float = 0f,
    rightTrimOffset: Float = 0f,
    isTrimming: Boolean = false,
    showTrimHandles: Boolean = false
) {
    val density = LocalDensity.current
    
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
            isDragging -> 1.05f
            isDropTarget -> 0.95f
            else -> 1f
        },
        label = "scale"
    )
    
    // Вычисляем ширину с учетом триммирования - края должны следовать за пальцем в реальном времени
    val baseWidthPx = segment.duration * PIXELS_PER_SECOND * zoomLevel
    val visualWidthPx = baseWidthPx + rightTrimOffset - leftTrimOffset
    val calculatedWidth = with(density) { visualWidthPx.toDp() }
    
    Box(
        modifier = Modifier
            .width(calculatedWidth)
            .height(80.dp)
            .offset(x = with(density) { leftTrimOffset.toDp() }) // Смещаем весь сегмент при левом триммировании
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
                    .border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
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
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary 
                           else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (isDragging) 0.9f else 1f
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
            
            // Эффект яркости при перетаскивании
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.2f))
                )
            }
            
            // Длительность сегмента - обновляется в реальном времени при триммировании
            val displayDuration = segment.duration + (rightTrimOffset - leftTrimOffset) / (PIXELS_PER_SECOND * zoomLevel)
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
            
            // Визуальные индикаторы триммирования и ручки
            if (showTrimHandles) {
                // Левая ручка триммирования
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(32.dp)
                        .fillMaxHeight()
                        .offset(x = with(density) { leftTrimOffset.toDp() })
                        .background(
                            color = if (activeGesture == GestureTypeNew.TRIM_LEFT && isTrimming) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                ) {
                    // Визуальная линия при активном триммировании
                    if (activeGesture == GestureTypeNew.TRIM_LEFT && isTrimming) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    // Всегда показываем тонкую линию для выделенного сегмента
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }
                
                // Правая ручка триммирования
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(32.dp)
                        .fillMaxHeight()
                        .offset(x = with(density) { rightTrimOffset.toDp() })
                        .background(
                            color = if (activeGesture == GestureTypeNew.TRIM_RIGHT && isTrimming) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                ) {
                    // Визуальная линия при активном триммировании
                    if (activeGesture == GestureTypeNew.TRIM_RIGHT && isTrimming) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    // Всегда показываем тонкую линию для выделенного сегмента
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}

/**
 * Data class для информации о сегменте в новой версии
 */
private data class SegmentInfoNew(
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
private fun findSegmentAtPositionNew(
    position: Offset,
    segments: List<VideoSegment>,
    density: Density,
    zoomLevel: Float,
    totalDuration: Float,
    timelineWidthDp: androidx.compose.ui.unit.Dp,
    selectedSegmentId: String?
): SegmentInfoNew? {
    var currentX = with(density) { 16.dp.toPx() }  // Начальный отступ
    
    segments.forEachIndexed { index, segment ->
        val segmentWidthPx = segment.duration * PIXELS_PER_SECOND * zoomLevel
        
        if (position.x >= currentX && position.x <= currentX + segmentWidthPx) {
            return SegmentInfoNew(
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