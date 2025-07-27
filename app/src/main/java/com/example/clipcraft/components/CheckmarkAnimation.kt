package com.example.clipcraft.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Компонент анимации галочек для туториала
 * Показывает циклическую анимацию проставления галочек
 */
@Composable
fun CheckmarkAnimation(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    checkmarkColor: Color = Color.White,
    onAnimationEnd: () -> Unit = {}
) {
    var checkmarksVisible by remember { mutableStateOf(listOf(false, false, false)) }
    
    LaunchedEffect(isActive) {
        if (isActive) {
            while (isActive) {
                // Показываем галочки по одной
                for (i in checkmarksVisible.indices) {
                    checkmarksVisible = checkmarksVisible.toMutableList().apply { this[i] = true }
                    delay(800) // 0.8 секунды между галочками
                }
                
                // Пауза после показа всех галочек
                delay(1000)
                
                // Скрываем все галочки
                checkmarksVisible = listOf(false, false, false)
                
                // Пауза перед новым циклом
                delay(2000)
            }
        } else {
            checkmarksVisible = listOf(false, false, false)
            onAnimationEnd()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Горизонтальный ряд из 3 галочек
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AnimatedCheckmark(
                isVisible = checkmarksVisible[0],
                color = checkmarkColor,
                modifier = Modifier.size(50.dp)
            )
            AnimatedCheckmark(
                isVisible = checkmarksVisible[1],
                color = checkmarkColor,
                modifier = Modifier.size(50.dp)
            )
            AnimatedCheckmark(
                isVisible = checkmarksVisible[2],
                color = checkmarkColor,
                modifier = Modifier.size(50.dp)
            )
        }
    }
}

@Composable
private fun AnimatedCheckmark(
    isVisible: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "checkmark"
    )
    
    Canvas(modifier = modifier) {
        if (animatedProgress > 0f) {
            drawCheckmark(
                progress = animatedProgress,
                color = color.copy(alpha = animatedProgress),
                strokeWidth = 4.dp.toPx()
            )
        }
    }
}

private fun DrawScope.drawCheckmark(
    progress: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path().apply {
        val width = size.width
        val height = size.height
        
        // Начальная точка галочки
        moveTo(width * 0.2f, height * 0.5f)
        
        // Рисуем галочку по прогрессу анимации
        if (progress > 0.5f) {
            lineTo(width * 0.4f, height * 0.7f)
            
            val secondProgress = (progress - 0.5f) * 2f
            lineTo(
                width * 0.4f + (width * 0.4f * secondProgress),
                height * 0.7f - (height * 0.4f * secondProgress)
            )
        } else {
            val firstProgress = progress * 2f
            lineTo(
                width * 0.2f + (width * 0.2f * firstProgress),
                height * 0.5f + (height * 0.2f * firstProgress)
            )
        }
    }
    
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Использование в TutorialOverlay:
 * 
 * // Для шага выбора видео (targetElement == TutorialTarget.GALLERY)
 * if (step.targetElement == TutorialTarget.GALLERY && currentStep == 1) {
 *     CheckmarkAnimation(
 *         modifier = Modifier.align(Alignment.Center),
 *         isActive = true,
 *         checkmarkColor = highlightColor,
 *         onAnimationEnd = {
 *             // Анимация завершена после нажатия на экран
 *         }
 *     )
 * }
 */