package com.example.clipcraft.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clipcraft.models.TimelineState
import com.example.clipcraft.models.VideoSegment
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

@Composable
fun TimelineView(
    timelineState: TimelineState,
    modifier: Modifier = Modifier,
    onSegmentClick: (VideoSegment) -> Unit = {},
    onSegmentTrimStart: (String, Float) -> Unit = { _, _ -> },
    onSegmentTrimEnd: (String, Float) -> Unit = { _, _ -> },
    onSegmentMove: (String, Int, Int) -> Unit = { _, _, _ -> },
    onSegmentDelete: (String) -> Unit = {},
    onPlayheadMove: (Float) -> Unit = {},
    onZoomChange: (Float) -> Unit = {},
    onScrollChange: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onTrimStart: (String, Boolean) -> Unit = { _, _ -> },
    onTrimEnd: (String, Float, Boolean) -> Unit = { _, _, _ -> }
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    var timelineWidth by remember { mutableStateOf(0f) }
    var isDraggingPlayhead by remember { mutableStateOf(false) }
    var draggedSegmentId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var targetIndex by remember { mutableStateOf(-1) }
    var dragStartX by remember { mutableStateOf(0f) }
    
    // Рассчитываем ширину контента с учетом зума
    val contentWidth = with(density) {
        val width = timelineState.totalDuration * 100 * timelineState.zoomLevel
        width.dp.toPx()
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onGloballyPositioned { coordinates ->
                timelineWidth = coordinates.size.width.toFloat()
                Log.d("videoeditorclipcraft", "TimelineView width: $timelineWidth px")
            }
    ) {
        // Основной контейнер без жестов зума (используем слайдер)
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Скроллируемый контент
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                // Контент таймлайна
                Row(
                    modifier = Modifier
                        .width(with(density) { contentWidth.toDp() })
                        .fillMaxHeight()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Рендерим сегменты
                    Log.d("videoeditorclipcraft", "TimelineView: Rendering ${timelineState.segments.size} segments")
                    var cumulativeWidth = 0f
                    
                    timelineState.segments.forEachIndexed { index, segment ->
                        // Рассчитываем ширину сегмента
                        val segmentWidthPx = segment.duration * 100 * timelineState.zoomLevel
                        val segmentWidth = with(density) { segmentWidthPx.toDp() }
                        
                        Log.d("videoeditorclipcraft", "TimelineView: Segment $index - id=${segment.id}, duration=${segment.duration}s, width=$segmentWidth")
                        
                        // Сохраняем текущую накопленную ширину для использования в drag callback
                        val currentCumulativeWidth = cumulativeWidth
                        
                        VideoSegmentView(
                            segment = segment,
                            width = segmentWidth,
                            isSelected = segment.id == timelineState.selectedSegmentId,
                            isDragging = segment.id == draggedSegmentId,
                            modifier = if (segment.id == draggedSegmentId) {
                                Modifier
                                    .offset { 
                                        IntOffset(dragOffset.x.toInt(), 0)
                                    }
                                    .graphicsLayer {
                                        scaleX = 1.1f
                                        scaleY = 1.1f
                                        shadowElevation = 8.dp.toPx()
                                    }
                            } else {
                                Modifier
                            },
                            onSegmentClick = { 
                                Log.d("videoeditorclipcraft", "TimelineView: Segment clicked - ${segment.id}")
                                onSegmentClick(segment) 
                            },
                            onTrimStart = { /* Теперь не используется */ },
                            onTrimEnd = { /* Теперь не используется */ },
                            onDragStart = { 
                                Log.d("videoeditorclipcraft", "TimelineView: Started dragging segment ${segment.id}")
                                draggedSegmentId = segment.id
                                targetIndex = index
                                dragStartX = currentCumulativeWidth
                                dragOffset = Offset.Zero
                            },
                            onDragEnd = { 
                                Log.d("videoeditorclipcraft", "TimelineView: Finished dragging segment ${segment.id}")
                                if (draggedSegmentId != null && targetIndex != -1 && targetIndex != index) {
                                    Log.d("videoeditorclipcraft", "TimelineView: Moving segment from $index to $targetIndex")
                                    onSegmentMove(segment.id, index, targetIndex)
                                }
                                draggedSegmentId = null
                                dragOffset = Offset.Zero
                                targetIndex = -1
                                dragStartX = 0f
                                onDragEnd()
                            },
                            onTrimDragStart = { isStart ->
                                Log.d("videoeditorclipcraft", "TimelineView: Started trimming segment ${segment.id}, isStart=$isStart")
                                onTrimStart(segment.id, isStart)
                            },
                            onTrimDragEnd = { deltaTime, isStart ->
                                Log.d("videoeditorclipcraft", "TimelineView: Finished trimming segment ${segment.id}, delta=$deltaTime, isStart=$isStart")
                                onTrimEnd(segment.id, deltaTime, isStart)
                            },
                            onSegmentDrag = { offset ->
                                // Рассчитываем смещение для следования за пальцем
                                // offset.x - это позиция касания относительно начала сегмента
                                dragOffset = Offset(offset.x - with(density) { segmentWidth.toPx() } / 2, 0f)
                                
                                // Определяем целевой индекс на основе центра перетаскиваемого сегмента
                                val dragCenterX = currentCumulativeWidth + dragOffset.x + with(density) { segmentWidth.toPx() / 2 }
                                var newTargetIndex = 0
                                var accumulatedWidth = 0f
                                
                                for ((idx, seg) in timelineState.segments.withIndex()) {
                                    val segWidth = with(density) { (seg.duration * 100 * timelineState.zoomLevel).dp.toPx() }
                                    if (dragCenterX > accumulatedWidth + segWidth / 2) {
                                        newTargetIndex = idx.coerceAtMost(timelineState.segments.size - 1)
                                    }
                                    accumulatedWidth += segWidth
                                }
                                
                                if (newTargetIndex != targetIndex) {
                                    targetIndex = newTargetIndex
                                    Log.d("videoeditorclipcraft", "TimelineView: New target index = $targetIndex")
                                }
                            },
                            onDelete = {
                                Log.d("videoeditorclipcraft", "TimelineView: Delete segment ${segment.id}")
                                onSegmentDelete(segment.id)
                            }
                        )
                        
                        cumulativeWidth += with(density) { segmentWidth.toPx() }
                    }
                }
                
                // Отметки секунд
                Box(
                    modifier = Modifier
                        .width(with(density) { contentWidth.toDp() })
                        .height(20.dp)
                        .align(Alignment.BottomStart)
                ) {
                    val secondsCount = floor(timelineState.totalDuration).toInt() + 1
                    for (second in 0 until secondsCount) {
                        val xPosition = with(density) { (second * 100 * timelineState.zoomLevel).dp }
                        
                        // Вертикальная линия
                        Box(
                            modifier = Modifier
                                .offset(x = xPosition)
                                .width(1.dp)
                                .height(if (second % 5 == 0) 10.dp else 5.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        )
                        
                        // Текст секунды (каждые 5 секунд)
                        if (second % 5 == 0) {
                            Text(
                                text = "${second}s",
                                modifier = Modifier
                                    .offset(x = xPosition - 10.dp, y = 8.dp)
                                    .width(20.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Playhead (индикатор позиции)
                val playheadX = with(density) {
                    (timelineState.currentPosition * 100 * timelineState.zoomLevel).dp
                }
                
                PlayheadIndicator(
                    modifier = Modifier
                        .offset(x = playheadX)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { isDraggingPlayhead = true },
                                onDragEnd = { isDraggingPlayhead = false },
                                onDrag = { change, _ ->
                                    val newX = (playheadX.toPx() + change.position.x).coerceIn(0f, contentWidth)
                                    val newPosition = newX / (100 * timelineState.zoomLevel)
                                    onPlayheadMove(newPosition)
                                }
                            )
                        }
                )
            }
        }
        
        
        // Автоскролл к playhead при воспроизведении
        LaunchedEffect(timelineState.currentPosition, timelineState.isPlaying) {
            if (timelineState.isPlaying && !isDraggingPlayhead) {
                val playheadXPx = timelineState.currentPosition * 100 * timelineState.zoomLevel * density.density
                val viewportStart = scrollState.value.toFloat()
                val viewportEnd = viewportStart + timelineWidth
                
                // Если playhead вышел за пределы видимой области
                if (playheadXPx < viewportStart || playheadXPx > viewportEnd - 50) {
                    scrollState.animateScrollTo(
                        (playheadXPx - timelineWidth / 2).coerceIn(0f, scrollState.maxValue.toFloat()).toInt()
                    )
                }
            }
        }
        
        // Сохраняем позицию скролла
        LaunchedEffect(scrollState.value) {
            onScrollChange(scrollState.value.toFloat())
        }
    }
}

@Composable
private fun PlayheadIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(2.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primary)
    ) {
        // Верхний треугольник-указатель
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-6).dp)
                .size(12.dp, 6.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                        moveTo(size.width / 2, 0f)
                        lineTo(0f, size.height)
                        lineTo(size.width, size.height)
                        close()
                    }
                )
        )
    }
}