package com.example.clipcraft.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Данные для шага туториала
 */
data class EditorTutorialStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val highlightArea: HighlightArea? = null
)

/**
 * Область подсветки
 */
enum class HighlightArea {
    TIMELINE,
    TRIM_HANDLES,
    PLAY_BUTTON,
    SAVE_BUTTON,
    VOICE_EDIT,
    ZOOM_SLIDER,
    DRAG_HANDLE
}

@Composable
fun VideoEditorTutorial(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    
    val steps = remember {
        listOf(
            EditorTutorialStep(
                title = "Добро пожаловать в редактор",
                description = "Здесь вы можете точно настроить ваше видео. Редактор работает как с AI-сгенерированным планом, так и при ручном монтаже.",
                icon = Icons.Default.Movie
            ),
            EditorTutorialStep(
                title = "Таймлайн",
                description = "Внизу находится таймлайн с сегментами вашего видео. Нажмите на сегмент, чтобы выбрать его (появится синяя рамка)",
                icon = Icons.Default.Timeline,
                highlightArea = HighlightArea.TIMELINE
            ),
            EditorTutorialStep(
                title = "Управление зумом",
                description = "Используйте жест пинч (сведение/разведение пальцев) на таймлайне для изменения масштаба. Кнопка с квадратиками сбрасывает зум до 100%",
                icon = Icons.Default.ZoomIn,
                highlightArea = HighlightArea.ZOOM_SLIDER
            ),
            EditorTutorialStep(
                title = "Обрезка видео",
                description = "После выбора сегмента появятся полупрозрачные ручки по краям. Потяните за них, чтобы обрезать или расширить сегмент до пределов исходного видео",
                icon = Icons.Default.ContentCut,
                highlightArea = HighlightArea.TRIM_HANDLES
            ),
            EditorTutorialStep(
                title = "Изменение порядка",
                description = "Выберите сегмент, затем нажмите и удерживайте центральную иконку (≡) более 0.5 секунды. После этого перетащите сегмент на новое место",
                icon = Icons.Default.DragHandle,
                highlightArea = HighlightArea.DRAG_HANDLE
            ),
            EditorTutorialStep(
                title = "Добавление и удаление",
                description = "Используйте кнопку '+' для добавления нового видео. Для удаления выделите сегмент и нажмите кнопку корзины или красный крестик на сегменте",
                icon = Icons.Default.Add,
                highlightArea = HighlightArea.TIMELINE
            ),
            EditorTutorialStep(
                title = "Отмена действий",
                description = "Используйте стрелки отмены и повтора для возврата к предыдущим состояниям. Кнопка сброса (↻) возвращает к исходному состоянию",
                icon = Icons.Default.Undo,
                highlightArea = HighlightArea.TIMELINE
            ),
            EditorTutorialStep(
                title = "Просмотр",
                description = "Нажмите кнопку воспроизведения в панели инструментов. Видео проигрывается в реальном времени без пересоздания файла",
                icon = Icons.Default.PlayArrow,
                highlightArea = HighlightArea.PLAY_BUTTON
            ),
            EditorTutorialStep(
                title = "Редактирование голосом",
                description = "Используйте кнопку 'Голосом' для внесения изменений голосовыми командами. AI поймет, что нужно изменить в монтаже",
                icon = Icons.Default.Mic,
                highlightArea = HighlightArea.VOICE_EDIT
            ),
            EditorTutorialStep(
                title = "Сохранение",
                description = "Когда закончите, нажмите 'Сохранить' для создания финального видео. Также доступны опции 'Поделиться' и 'Создать новое'",
                icon = Icons.Default.Save,
                highlightArea = HighlightArea.SAVE_BUTTON
            )
        )
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
            ) {
                // Кнопка "Пропустить" в правом верхнем углу
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                
                // Контент туториала
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val step = steps[currentStep]
                    
                    // Иконка
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = step.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Заголовок
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Описание
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Индикаторы шагов
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        steps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == currentStep) Color.White
                                        else Color.White.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Кнопки навигации
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (currentStep > 0) {
                            OutlinedButton(
                                onClick = { currentStep-- },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Color.White)
                                )
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Назад")
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (currentStep < steps.size - 1) {
                                    currentStep++
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                if (currentStep < steps.size - 1) "Далее" else "Начать"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                if (currentStep < steps.size - 1) Icons.Default.ArrowForward 
                                else Icons.Default.Check,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }
    }
}