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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpeechBubbleMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val type: MessageType = MessageType.SYSTEM,
    val timestamp: Long = System.currentTimeMillis(),
    val action: (() -> Unit)? = null
)

enum class MessageType {
    SYSTEM,        // Системные сообщения (определяем речь, композицию)
    TRANSCRIPTION, // Транскрибированная речь
    PLAN,          // План монтажа
    TIP,           // Советы
    PROGRESS,      // Сообщения о прогрессе
    SUCCESS,       // Сообщение об успешном завершении
    FEEDBACK       // Предложение обратной связи
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
    }

    val contentColor = when (message.type) {
        MessageType.SYSTEM -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.TRANSCRIPTION -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.PLAN -> MaterialTheme.colorScheme.onTertiaryContainer
        MessageType.TIP -> MaterialTheme.colorScheme.onSurfaceVariant
        MessageType.PROGRESS -> MaterialTheme.colorScheme.onSecondaryContainer
        MessageType.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        MessageType.FEEDBACK -> MaterialTheme.colorScheme.onTertiaryContainer
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
                            text = "Заполнить форму",
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
fun getRandomProgressMessage(): String {
    val messages = listOf(
        "✨ Ещё чуть-чуть...",
        "🎬 Ещё пару штрихов...",
        "🎨 Проверяем композицию...",
        "⚡ Работа кипит!",
        "🎯 Почти готово...",
        "🔧 Применяем магию монтажа...",
        "🎪 Творим чудеса...",
        "🚀 Обрабатываем кадры...",
        "🎭 Добавляем последние штрихи...",
        "🌟 Полируем результат...",
        "🎵 Синхронизируем элементы...",
        "📽️ Склеиваем фрагменты...",
        "🖼️ Оптимизируем качество...",
        "💫 Всё идёт по плану!",
        "🎉 Уже скоро увидишь результат!"
    )
    return messages.random()
}

// Компонент с примерами советов
fun getRandomTip(): String {
    val tips = listOf(
        "💡 Совет: Снимайте при хорошем освещении для лучшего качества",
        "💡 Совет: Держите камеру стабильно или используйте штатив",
        "💡 Совет: Записывайте короткие фрагменты для удобного монтажа",
        "💡 Совет: Говорите четко и делайте паузы между фразами",
        "💡 Совет: Оставляйте 2-3 секунды до и после основного действия",
        "💡 Совет: Снимайте в горизонтальной ориентации для Reels",
        "💡 Совет: Используйте правило третей для красивой композиции",
        "💡 Совет: Старайтесь снимать на уровне глаз",
        "💡 Совет: Избегайте съёмки против света",
        "💡 Совет: Проверяйте фокус перед началом записи"
    )
    return tips.random()
}