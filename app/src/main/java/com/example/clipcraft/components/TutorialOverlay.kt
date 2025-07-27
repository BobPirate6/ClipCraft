package com.example.clipcraft.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import com.example.clipcraft.models.TutorialPosition
import com.example.clipcraft.models.TutorialStep
import com.example.clipcraft.models.TutorialTarget
import kotlinx.coroutines.delay

/**
 * Оверлей для отображения туториала
 */
@Composable
fun TutorialOverlay(
    step: TutorialStep,
    targetBounds: Map<TutorialTarget, IntRect>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentStepIndex: Int = 0,
    onSkip: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    
    // Получаем границы целевого элемента
    val bounds = targetBounds[step.targetElement]
    
    // Определяем цвет подсветки в зависимости от элемента
    val highlightColor = when (step.targetElement) {
        TutorialTarget.GALLERY -> MaterialTheme.colorScheme.primary
        TutorialTarget.INPUT_FIELD -> Color(0xFF9C27B0) // Фиолетовый
        TutorialTarget.VOICE_BUTTON -> Color(0xFFFF5722) // Оранжевый
        else -> MaterialTheme.colorScheme.primary
    }
    
    // Показываем обводку только начиная со второго шага (индекс 1)
    val showHighlight = currentStepIndex > 0 && bounds != null
    
    // Анимация появления обводки
    val highlightAlpha by animateFloatAsState(
        targetValue = if (showHighlight) 1f else 0f,
        animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
        ),
        label = "highlightFadeIn"
    )
    
    // Анимация пульсации для свечения
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Затемненный фон с вырезом для незатемненной области
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // Необходимо для правильной работы BlendMode
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() }
        ) {
            val padding = with(density) { 4.dp.toPx() }
            
            // Если есть целевой элемент, рисуем затемнение с вырезом
            if (step.targetElement != null && bounds != null) {
                // Создаем путь для затемнения с вырезом
                drawContext.canvas.save()
                
                // Рисуем затемнение на весь экран
                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    size = size
                )
                
                // Вырезаем область для целевого элемента
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(
                        bounds.left.toFloat() - padding,
                        bounds.top.toFloat() - padding
                    ),
                    size = Size(
                        bounds.width.toFloat() + padding * 2,
                        bounds.height.toFloat() + padding * 2
                    ),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    blendMode = BlendMode.DstOut
                )
                
                drawContext.canvas.restore()
                
                // Рисуем обводку вокруг вырезанной области с учетом fade-in анимации
                if (highlightAlpha > 0f) {
                    // Внешняя обводка с эффектом свечения
                    drawRoundRect(
                        color = highlightColor.copy(alpha = 0.3f * glowAlpha * highlightAlpha),
                        topLeft = Offset(
                            bounds.left.toFloat() - padding - 4.dp.toPx(),
                            bounds.top.toFloat() - padding - 4.dp.toPx()
                        ),
                        size = Size(
                            bounds.width.toFloat() + (padding + 4.dp.toPx()) * 2,
                            bounds.height.toFloat() + (padding + 4.dp.toPx()) * 2
                        ),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 8.dp.toPx())
                    )
                    
                    // Основная обводка
                    drawRoundRect(
                        color = highlightColor.copy(alpha = glowAlpha * highlightAlpha),
                        topLeft = Offset(
                            bounds.left.toFloat() - padding,
                            bounds.top.toFloat() - padding
                        ),
                        size = Size(
                            bounds.width.toFloat() + padding * 2,
                            bounds.height.toFloat() + padding * 2
                        ),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            } else {
                // Если нет целевого элемента, просто затемняем весь экран
                drawRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    size = size
                )
            }
        }
        
        // Центрированное окно туториала
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                TutorialTooltip(
                    step = step,
                    highlightColor = if (step.targetElement != null) highlightColor else MaterialTheme.colorScheme.primary,
                    onDismiss = onDismiss
                )
            }
        }
        
        // Показываем анимацию галочек для шага выбора видео (индекс 1)
        if (currentStepIndex == 1 && step.targetElement == TutorialTarget.GALLERY && bounds != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            bounds.left,
                            bounds.top + with(density) { 20.dp.roundToPx() }
                        )
                    }
            ) {
                CheckmarkAnimation(
                    modifier = Modifier
                        .width(with(density) { bounds.width.toDp() })
                        .height(with(density) { 100.dp }),
                    isActive = true,
                    checkmarkColor = Color.White
                )
            }
        }
        
        // Кнопка "Пропустить" в правом верхнем углу с отступом от системных элементов
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding() // Отступ от статус бара
                .padding(horizontal = 16.dp)
                .padding(top = 64.dp) // Опускаем кнопку ниже
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSkip?.invoke() ?: onDismiss() },
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Пропустить",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialTooltip(
    step: TutorialStep,
    highlightColor: Color,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Заголовок с кнопкой закрытия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = highlightColor
                )
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Описание
            Text(
                text = step.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Подсказка
            Text(
                text = "Нажмите в любом месте для продолжения",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

/**
 * Компонент для отслеживания позиции элементов UI
 */
@Composable
fun TutorialTargetBounds(
    target: TutorialTarget,
    onBoundsChanged: (IntRect) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            val size = coordinates.size
            onBoundsChanged(
                IntRect(
                    offset = IntOffset(position.x.toInt(), position.y.toInt()),
                    size = size
                )
            )
        }
    ) {
        content()
    }
}

