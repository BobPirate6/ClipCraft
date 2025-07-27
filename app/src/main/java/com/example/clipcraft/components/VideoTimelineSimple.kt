package com.example.clipcraft.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import coil.compose.AsyncImage
import com.example.clipcraft.models.VideoSegment
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
    
    // Текущий уровень масштаба
    var currentZoom by remember { mutableStateOf(zoomLevel) }
    LaunchedEffect(zoomLevel) {
        currentZoom = zoomLevel
    }
    
    // Состояние для drag & drop
    var draggingSegmentId by remember { mutableStateOf<String?>(null) }
    var dropTargetIndex by remember { mutableStateOf(-1) }
    
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
                    .padding(top = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(
                    items = segments,
                    key = { _, segment -> segment.id }
                ) { index, segment ->
                    // Показываем индикатор слева от сегмента если это целевая позиция
                    if (dropTargetIndex == index && draggingSegmentId != null) {
                        // Превью сегмента с яркой обводкой
                        Box(
                            modifier = Modifier
                                .width(with(density) { (PIXELS_PER_SECOND * currentZoom * 1f).toDp() }) // 1 секунда длительность
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    
                    VideoSegmentSimple(
                        segment = segment,
                        index = index,
                        isSelected = segment.id == selectedSegmentId,
                        isDragging = segment.id == draggingSegmentId,
                        isDropTarget = index == dropTargetIndex && draggingSegmentId != null,
                        zoomLevel = currentZoom,
                        segments = segments,
                        onSegmentClick = { onSegmentClick(segment) },
                        onSegmentDelete = { onSegmentDelete(segment.id) },
                        onSegmentReorder = onSegmentReorder,
                        onSegmentTrim = { deltaTime, isStart ->
                            onSegmentTrim(segment.id, deltaTime, isStart)
                        },
                        onDragStateChange = { isDragging, targetIndex ->
                            draggingSegmentId = if (isDragging) segment.id else null
                            dropTargetIndex = targetIndex
                        }
                    )
                }
                
                // Показываем индикатор в конце если это последняя позиция
                if (dropTargetIndex == segments.size && draggingSegmentId != null) {
                    item {
                        Spacer(modifier = Modifier.width(4.dp))
                        // Превью сегмента с яркой обводкой
                        Box(
                            modifier = Modifier
                                .width(with(density) { (PIXELS_PER_SECOND * currentZoom * 1f).toDp() }) // 1 секунда длительность
                                .height(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                        )
                    }
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
    isDropTarget: Boolean,
    zoomLevel: Float,
    segments: List<VideoSegment>,
    onSegmentClick: () -> Unit,
    onSegmentDelete: () -> Unit,
    onSegmentReorder: (Int, Int) -> Unit,
    onSegmentTrim: (Float, Boolean) -> Unit,
    onDragStateChange: (Boolean, Int) -> Unit
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
            isDragging -> 16.dp
            isSelected -> 8.dp
            else -> 2.dp
        },
        animationSpec = spring(),
        label = "elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(),
        label = "scale"
    )
    
    // Вычисляем размер с учетом визуального триммирования
    val baseWidthPx = segment.duration * PIXELS_PER_SECOND * zoomLevel
    val visualWidth = if (isTrimming) {
        when (trimType) {
            TrimType.LEFT -> baseWidthPx - visualTrimOffset
            TrimType.RIGHT -> baseWidthPx + visualTrimOffset
            else -> baseWidthPx
        }
    } else {
        baseWidthPx
    }
    val segmentWidthDp = with(density) { visualWidth.coerceAtLeast(20f).toDp() }
    
    // Вычисляем смещение для правильного anchor при триммировании
    val trimOffsetX = if (isTrimming && trimType == TrimType.RIGHT) {
        // При триммировании правого края, левый край остается на месте (anchor)
        0f
    } else if (isTrimming && trimType == TrimType.LEFT) {
        visualTrimOffset
    } else {
        0f
    }
    
    Box(
        modifier = Modifier
            .width(segmentWidthDp)
            .height(80.dp)
            .offset { 
                IntOffset(
                    (if (isDragging) dragOffset.x else 0f).roundToInt() + trimOffsetX.roundToInt(),
                    if (isDragging) dragOffset.y.roundToInt() else 0
                )
            }
            .shadow(elevation, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary 
                       else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
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
                        val edgeThreshold = 32.dp.toPx()
                        when {
                            // Левый край
                            offset.x < edgeThreshold -> {
                                isTrimming = true
                                trimType = TrimType.LEFT
                                trimOffset = 0f
                                visualTrimOffset = 0f
                            }
                            // Правый край
                            offset.x > size.width - edgeThreshold -> {
                                isTrimming = true
                                trimType = TrimType.RIGHT
                                trimOffset = 0f
                                visualTrimOffset = 0f
                            }
                            // Центр - drag
                            else -> {
                                isDragging = true
                                dragOffset = Offset.Zero
                                onDragStateChange(true, index)
                            }
                        }
                    },
                    onDrag = { _, dragAmount ->
                        when {
                            isTrimming && trimType == TrimType.LEFT -> {
                                trimOffset += dragAmount.x
                                visualTrimOffset += dragAmount.x
                                // Только визуальная обратная связь, без вызова onSegmentTrim
                            }
                            isTrimming && trimType == TrimType.RIGHT -> {
                                trimOffset += dragAmount.x
                                visualTrimOffset += dragAmount.x
                                // Только визуальная обратная связь, без вызова onSegmentTrim
                            }
                            isDragging -> {
                                dragOffset += dragAmount
                                // Обновляем dropTargetIndex во время перетаскивания
                                val currentCenterX = (segments.indexOf(segment) * (baseWidthPx + 2.dp.toPx())) + baseWidthPx / 2 + dragOffset.x
                                var newDropIndex = segments.size // По умолчанию в конец
                                var accumulatedX = 0f
                                
                                segments.forEachIndexed { idx, seg ->
                                    val segWidth = seg.duration * PIXELS_PER_SECOND * zoomLevel
                                    val segCenterX = accumulatedX + segWidth / 2
                                    
                                    if (currentCenterX < segCenterX && idx != index) {
                                        newDropIndex = idx
                                        return@forEachIndexed
                                    }
                                    accumulatedX += segWidth + 2.dp.toPx()
                                }
                                
                                onDragStateChange(true, newDropIndex)
                            }
                        }
                    },
                    onDragEnd = {
                        when {
                            isDragging -> {
                                // Определяем новую позицию
                                val totalOffset = dragOffset.x
                                var newIndex = index
                                var cumulativeWidth = 0f
                                
                                // Проходим по всем сегментам
                                for (i in segments.indices) {
                                    val segWidth = segments[i].duration * PIXELS_PER_SECOND * zoomLevel
                                    if (i < index && totalOffset < -cumulativeWidth) {
                                        newIndex = i
                                        break
                                    } else if (i > index && totalOffset > cumulativeWidth) {
                                        newIndex = i
                                        break
                                    }
                                    cumulativeWidth += segWidth + 2.dp.toPx()
                                }
                                
                                if (newIndex != index) {
                                    onSegmentReorder(index, newIndex)
                                }
                                
                                isDragging = false
                                dragOffset = Offset.Zero
                                onDragStateChange(false, -1)
                            }
                            isTrimming && trimType == TrimType.LEFT -> {
                                // Применяем изменения только в конце
                                val deltaTime = trimOffset / (PIXELS_PER_SECOND * zoomLevel)
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
        
        // Визуальные индикаторы триммирования для выделенного сегмента
        if (isSelected) {
            // Левый индикатор
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        if (isTrimming && trimType == TrimType.LEFT) 
                            MaterialTheme.colorScheme.primary 
                        else Color.White.copy(alpha = 0.5f)
                    )
            )
            
            // Правый индикатор
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        if (isTrimming && trimType == TrimType.RIGHT) 
                            MaterialTheme.colorScheme.primary 
                        else Color.White.copy(alpha = 0.5f)
                    )
            )
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