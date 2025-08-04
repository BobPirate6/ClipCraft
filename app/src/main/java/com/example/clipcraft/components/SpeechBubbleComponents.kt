package com.example.clipcraft.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import com.example.clipcraft.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpeechBubbleMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val type: MessageType = MessageType.SYSTEM,
    val timestamp: Long = System.currentTimeMillis(),
    val action: (() -> Unit)? = null,
    val progress: Float? = null, // Progress 0..1
    val thumbnails: List<String>? = null, // Base64 encoded frame previews
    val currentVideo: Int? = null,
    val totalVideos: Int? = null
)

enum class MessageType {
    SYSTEM,        // Системные сообщения (определяем речь, композицию)
    TRANSCRIPTION, // Транскрибированная речь
    PLAN,          // План монтажа
    TIP,           // Советы
    PROGRESS,      // Сообщения о прогрессе (филлеры)
    SUCCESS,       // Сообщение об успешном завершении
    FEEDBACK,      // Предложение обратной связи
    VIDEO_PROGRESS // Прогресс обработки видео с прогресс-баром
}

@Composable
fun SpeechBubbleChat(
    messages: List<SpeechBubbleMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Автоскролл к последнему сообщению
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                delay(100) // Даем время на анимацию
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        reverseLayout = false // Сообщения идут сверху вниз
    ) {
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(
                    animationSpec = tween(300, easing = EaseOutCubic)
                ) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(300, easing = EaseOutCubic)
                )
            ) {
                SpeechBubble(message = message)
            }
        }
    }
}

@Composable
fun SpeechBubble(
    message: SpeechBubbleMessage
) {
    val backgroundColor = when (message.type) {
        MessageType.SYSTEM -> MaterialTheme.colorScheme.secondaryContainer
        MessageType.TRANSCRIPTION -> MaterialTheme.colorScheme.primaryContainer
        MessageType.PLAN -> MaterialTheme.colorScheme.tertiaryContainer
        MessageType.TIP -> MaterialTheme.colorScheme.surfaceVariant
        MessageType.PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
        MessageType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        MessageType.FEEDBACK -> MaterialTheme.colorScheme.tertiaryContainer
        MessageType.VIDEO_PROGRESS -> MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when (message.type) {
        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.TRANSCRIPTION -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.PLAN -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageType.TIP -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageType.PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.FEEDBACK -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageType.VIDEO_PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when (message.type) {
            MessageType.TRANSCRIPTION -> Arrangement.End
            else -> Arrangement.Start
        }
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.type == MessageType.TRANSCRIPTION) 16.dp else 4.dp,
                        bottomEnd = if (message.type == MessageType.TRANSCRIPTION) 4.dp else 16.dp
                    )
                )
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Имя отправителя (только для системных сообщений)
                if (message.type != MessageType.TRANSCRIPTION) {
                    Text(
                        text = "ClipCraft",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Основной текст
                Text(
                    text = message.text,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = if (message.type == MessageType.TIP) 13.sp else 14.sp
                )
                
                // Прогресс бар для VIDEO_PROGRESS
                if (message.type == MessageType.VIDEO_PROGRESS && message.progress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Прогресс бар
                    LinearProgressIndicator(
                        progress = message.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    
                    // Превью кадров, если есть
                    if (!message.thumbnails.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            message.thumbnails.take(3).forEach { base64Thumbnail ->
                                // Здесь можно отобразить превью из base64
                                // Пока показываем заглушку
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                    }
                }
                
                // Кнопка для feedback сообщений
                if (message.type == MessageType.FEEDBACK && message.action != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = message.action,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_fill_form),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Время в правом нижнем углу
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    fontSize = 10.sp,
                    color = contentColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

// Фразы для заполнения пауз
fun getRandomProgressMessage(context: Context): String {
    val messages = listOf(
        context.getString(R.string.speech_bubble_progress_1),
        context.getString(R.string.speech_bubble_progress_2),
        context.getString(R.string.speech_bubble_progress_3),
        context.getString(R.string.speech_bubble_progress_4),
        context.getString(R.string.speech_bubble_progress_5),
        context.getString(R.string.speech_bubble_progress_6),
        context.getString(R.string.speech_bubble_progress_7),
        context.getString(R.string.speech_bubble_progress_8),
        context.getString(R.string.speech_bubble_progress_9),
        context.getString(R.string.speech_bubble_progress_10),
        context.getString(R.string.speech_bubble_progress_11),
        context.getString(R.string.speech_bubble_progress_12),
        context.getString(R.string.speech_bubble_progress_13),
        context.getString(R.string.speech_bubble_progress_14),
        context.getString(R.string.speech_bubble_progress_15)
    )
    return messages.random()
}

// Компонент с примерами советов
fun getRandomTip(context: Context): String {
    val tips = listOf(
        context.getString(R.string.speech_bubble_tip_1),
        context.getString(R.string.speech_bubble_tip_2),
        context.getString(R.string.speech_bubble_tip_3),
        context.getString(R.string.speech_bubble_tip_4),
        context.getString(R.string.speech_bubble_tip_5),
        context.getString(R.string.speech_bubble_tip_6),
        context.getString(R.string.speech_bubble_tip_7),
        context.getString(R.string.speech_bubble_tip_8)
    )
    return tips.random()
}