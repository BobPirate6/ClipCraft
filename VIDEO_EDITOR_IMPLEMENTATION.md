# Реализация видеоредактора с мультисегментным воспроизведением

## Обзор проекта

Проект ClipCraft - мобильный видеоредактор для Android с функцией редактирования нескольких видеосегментов на временной шкале (timeline). Основная задача - обеспечить бесшовное воспроизведение нескольких видеосегментов подряд без остановок на границах.

## Архитектура компонентов

### 1. OptimizedCompositeVideoPlayer
Основной компонент для воспроизведения нескольких видеосегментов как единого видео.

**Расположение**: `app/src/main/java/com/example/clipcraft/components/OptimizedCompositeVideoPlayer.kt`

**Ключевые особенности**:
- Использует один экземпляр ExoPlayer для всех сегментов
- Поддерживает клиппинг (обрезку) видео по времени
- Синхронизирует позицию воспроизведения с UI

### 2. VideoTimelineSimple
Компонент временной шкалы для визуализации и управления сегментами.

**Расположение**: `app/src/main/java/com/example/clipcraft/components/VideoTimelineSimple.kt`

**Функциональность**:
- Отображение сегментов с превью
- Drag & Drop для изменения порядка
- Визуализация прогресса воспроизведения
- Обрезка сегментов по краям

### 3. VideoRenderingService
Сервис для финального рендеринга видео.

**Расположение**: `app/src/main/java/com/example/clipcraft/services/VideoRenderingService.kt`

**Особенности**:
- Использует Media3 Transformer
- Поддерживает быстрое копирование без перекодирования
- Требует вызова из главного потока

## Проблемы и решения

### Проблема 1: Остановка воспроизведения на границах сегментов

**Симптомы**: При воспроизведении нескольких сегментов видео останавливалось при переходе от одного сегмента к другому.

**Причина**: Неправильное использование ConcatenatingMediaSource2 и создание отдельных MediaSource для каждого сегмента.

**Решение**:
```kotlin
// Вместо создания ConcatenatingMediaSource2 вручную
// используем встроенную функциональность плейлиста ExoPlayer
val mediaItems = segments.mapIndexed { index, segment ->
    MediaItem.Builder()
        .setUri(segment.sourceVideoUri)
        .setMediaId("segment_$index")
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs((segment.inPoint * 1000).toLong())
                .setEndPositionMs((segment.outPoint * 1000).toLong())
                .build()
        )
        .setTag(index)
        .build()
}

// ExoPlayer автоматически обрабатывает переходы
player.setMediaItems(mediaItems, false)
player.prepare()
```

### Проблема 2: Неправильное обновление позиции воспроизведения

**Симптомы**: Индикаторы прогресса (ползунок и белая линия на таймлайне) замирали при переходе на второй сегмент, хотя видео продолжало воспроизводиться.

**Причина**: Неправильный расчет общей позиции в плейлисте из нескольких сегментов.

**Решение**:
```kotlin
// Правильный расчет позиции с учетом всех предыдущих сегментов
LaunchedEffect(player, segments) {
    while (true) {
        delay(16) // ~60fps
        
        if (isPlayerReady && player.mediaItemCount > 0) {
            val currentItemIndex = player.currentMediaItemIndex
            val currentPositionMs = player.currentPosition
            
            // Рассчитываем общую позицию
            var timelinePosition = 0f
            
            // Добавляем длительность всех предыдущих сегментов
            for (i in 0 until currentItemIndex.coerceIn(0, segments.size - 1)) {
                timelinePosition += segments[i].duration
            }
            
            // Добавляем текущую позицию в сегменте
            if (currentItemIndex < segments.size) {
                val positionInSegment = (currentPositionMs / 1000f)
                    .coerceIn(0f, segments[currentItemIndex].duration)
                timelinePosition += positionInSegment
            }
            
            onPositionChange(timelinePosition)
        }
    }
}
```

### Проблема 3: Crash при использовании content:// URI

**Симптомы**: Приложение падало с NoSuchFileException при попытке удалить сегмент и выйти из редактора.

**Причина**: Попытка работать с content:// URI как с обычными файлами.

**Решение**:
```kotlin
// В VideoRenderingService
if (segment.sourceVideoUri.scheme == "content") {
    context.contentResolver.openInputStream(segment.sourceVideoUri)?.use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
} else {
    val inputFile = File(segment.sourceVideoUri.path ?: 
        throw IllegalArgumentException("Invalid URI"))
    inputFile.copyTo(outputFile, overwrite = true)
}
```

### Проблема 4: IndexOutOfBoundsException при добавлении сегментов

**Симптомы**: Crash при нажатии кнопки "+" для добавления нового сегмента.

**Причина**: Индекс для вставки выходил за границы массива.

**Решение**:
```kotlin
// В VideoEditorViewModel
fun addSegment(index: Int, uri: Uri) {
    val segments = _timelineState.value.segments.toMutableList()
    val actualIndex = if (index == -1) segments.size else index
    
    // Обеспечиваем, что индекс в допустимых границах
    val safeIndex = actualIndex.coerceIn(0, segments.size)
    
    segments.add(safeIndex, newSegment)
}
```

### Проблема 5: Transformer требует главный поток

**Симптомы**: IllegalStateException: Transformer is accessed on the wrong thread при рендеринге видео.

**Причина**: Media3 Transformer требует вызова из главного потока Android.

**Решение**:
```kotlin
private suspend fun renderWithTransformer(
    segments: List<VideoSegment>,
    outputFile: File,
    onProgress: ((Float) -> Unit)?
): String = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { continuation ->
        val transformer = Transformer.Builder(context)
            // ... настройки
            .build()
        
        // Теперь start() вызывается в главном потоке
        transformer.start(composition, outputFile.absolutePath)
    }
}
```

## Ключевые настройки ExoPlayer для бесшовного воспроизведения

```kotlin
// В VideoPlayerPool
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        50000,  // Минимальный буфер (50 секунд)
        200000, // Максимальный буфер (200 секунд)
        2500,   // Буфер для воспроизведения (2.5 секунды)
        5000    // Буфер для ребуферизации (5 секунд)
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()

val player = ExoPlayer.Builder(context)
    .setLoadControl(loadControl)
    .build().apply {
        setSeekParameters(SeekParameters.EXACT)
        setHandleAudioBecomingNoisy(true)
        setPauseAtEndOfMediaItems(false) // Важно для плавных переходов
    }
```

## Структура данных VideoSegment

```kotlin
data class VideoSegment(
    val id: String = UUID.randomUUID().toString(),
    val sourceVideoUri: Uri,
    val inPoint: Float,    // Начальная точка в секундах
    val outPoint: Float,   // Конечная точка в секундах
    val duration: Float,   // Длительность сегмента
    val originalDuration: Float,
    val thumbnails: List<Bitmap> = emptyList()
)
```

## Рекомендации по использованию

1. **Всегда проверяйте тип URI** перед работой с файлами (content:// vs file://)
2. **Используйте встроенную функциональность плейлистов ExoPlayer** вместо ручного управления источниками
3. **Вызывайте Media3 Transformer из главного потока**
4. **Настройте буферизацию** для плавного воспроизведения между сегментами
5. **Используйте onEvents()** вместо отдельных listener-методов для лучшей производительности

## Зависимости

```gradle
implementation "androidx.media3:media3-exoplayer:1.1.1"
implementation "androidx.media3:media3-ui:1.1.1"
implementation "androidx.media3:media3-transformer:1.1.1"
```

## Тестирование

При тестировании обратите внимание на:
1. Плавность переходов между сегментами разной длительности
2. Корректность отображения позиции при быстрой перемотке
3. Работу с различными форматами видео
4. Обработку content:// URI из галереи
5. Производительность при большом количестве сегментов

## Известные ограничения

1. Transformer может быть медленным на слабых устройствах
2. Большие видеофайлы могут вызвать проблемы с памятью
3. Некоторые форматы видео могут не поддерживаться для быстрого копирования