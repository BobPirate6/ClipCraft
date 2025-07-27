package com.example.clipcraft.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.clipcraft.models.VideoSegment
import kotlin.math.roundToInt

@Composable
fun DraggableVideoSegment(
    segment: VideoSegment,
    segmentWidth: androidx.compose.ui.unit.Dp,
    index: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    dragOffset: Offset,
    onSegmentClick: () -> Unit,
    onTrimStart: (Float) -> Unit,
    onTrimEnd: (Float) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var segmentPosition by remember { mutableStateOf(Offset.Zero) }
    
    // Анимация для перетаскивания
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val shadowElevation by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 0.dp,
        label = "shadow"
    )
    
    Box(
        modifier = modifier
            .width(segmentWidth)
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                segmentPosition = coordinates.positionInWindow()
            }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = 0f // Ограничиваем перемещение только по горизонтали
                    scaleX = scale
                    scaleY = scale
                }
            }
            .pointerInput(segment.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onDragStart(offset)
                    },
                    onDrag = { _, dragAmount ->
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd
                )
            }
    ) {
        // Индикатор места вставки при перетаскивании
        if (!isDragging) {
            DragTargetIndicator(
                isActive = false, // Будет активен когда другой сегмент перетаскивается рядом
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Сам сегмент с тенью при перетаскивании
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(shadowElevation, MaterialTheme.shapes.small)
                .alpha(if (isDragging) 0.9f else 1f)
        ) {
            VideoSegmentView(
                segment = segment,
                width = segmentWidth,
                isSelected = isSelected,
                onSegmentClick = onSegmentClick,
                onTrimStart = onTrimStart,
                onTrimEnd = onTrimEnd,
                onDragStart = { /* Обрабатывается выше */ },
                onDragEnd = { /* Обрабатывается выше */ },
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun DragTargetIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val targetWidth by animateDpAsState(
        targetValue = if (isActive) 4.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "targetWidth"
    )
    
    if (isActive) {
        Box(
            modifier = modifier
        ) {
            // Индикатор слева
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(targetWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * Компонент для визуализации перетаскиваемого сегмента
 */
@Composable
fun DraggedSegmentOverlay(
    segment: VideoSegment?,
    segmentWidth: androidx.compose.ui.unit.Dp,
    dragOffset: Offset,
    modifier: Modifier = Modifier
) {
    if (segment != null) {
        Box(
            modifier = modifier
                .offset { IntOffset(dragOffset.x.roundToInt(), 0) }
                .width(segmentWidth)
                .height(80.dp)
                .alpha(0.8f)
                .shadow(12.dp, MaterialTheme.shapes.small)
        ) {
            VideoSegmentView(
                segment = segment,
                width = segmentWidth,
                isSelected = false,
                onSegmentClick = {},
                onTrimStart = {},
                onTrimEnd = {},
                onDragStart = {},
                onDragEnd = {},
                onDelete = {}
            )
        }
    }
}