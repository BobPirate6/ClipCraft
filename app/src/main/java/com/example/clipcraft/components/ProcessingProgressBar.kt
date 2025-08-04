package com.example.clipcraft.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.example.clipcraft.R

enum class ProcessingStep {
    ANALYZING,    // Видео обрабатываются
    SENDING,      // Отправлены на сервер
    PROCESSING,   // Получен ответ/план
    FINALIZING    // Видео готово
}

@Composable
fun ProcessingProgressBar(
    currentStep: ProcessingStep,
    modifier: Modifier = Modifier
) {
    val steps = listOf(
        stringResource(R.string.processing_preparation) to ProcessingStep.ANALYZING,
        stringResource(R.string.processing_analysis) to ProcessingStep.SENDING,
        stringResource(R.string.processing_editing) to ProcessingStep.PROCESSING,
        stringResource(R.string.processing_ready) to ProcessingStep.FINALIZING
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress steps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp), // Уменьшаем горизонтальные отступы
                horizontalArrangement = Arrangement.SpaceEvenly, // Используем SpaceEvenly для равномерного распределения
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.forEachIndexed { index, (label, step) ->
                    val isCompleted = step.ordinal <= currentStep.ordinal
                    val isActive = step == currentStep
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                    ) {
                        // Step indicator
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isActive -> MaterialTheme.colorScheme.primary
                                        isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isActive) {
                                // Анимация для активного шага
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.8f,
                                    targetValue = 1.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary)
                                )
                            } else if (isCompleted) {
                                Text(
                                    "✓",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Step label
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = when {
                                isActive -> MaterialTheme.colorScheme.primary
                                isCompleted -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
            
            // Status text
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (currentStep) {
                    ProcessingStep.ANALYZING -> "Собираем материал..."
                    ProcessingStep.SENDING -> "Анализируем видео..."
                    ProcessingStep.PROCESSING -> "AI генерирует план монтажа..."
                    ProcessingStep.FINALIZING -> "Создаем финальное видео..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Определяет текущий шаг обработки на основе сообщений прогресса
 */
@Composable
fun rememberProcessingStep(progressMessages: List<String>): ProcessingStep {
    return remember(progressMessages) {
        when {
            progressMessages.any { it.contains("готово", ignoreCase = true) } -> ProcessingStep.FINALIZING
            progressMessages.any { it.contains("план", ignoreCase = true) || it.contains("💭", ignoreCase = false) } -> ProcessingStep.PROCESSING
            progressMessages.any { it.contains("отправ", ignoreCase = true) } -> ProcessingStep.SENDING
            else -> ProcessingStep.ANALYZING
        }
    }
}