package com.example.clipcraft.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.clipcraft.models.VideoSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Видеоплеер, который воспроизводит сегменты без необходимости создания нового видеофайла.
 * Переключается между исходными видео в реальном времени.
 */
@Composable
fun CompositeVideoPlayer(
    segments: List<VideoSegment>,
    currentPosition: Float,
    isPlaying: Boolean,
    onPositionChange: (Float) -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentSegmentIndex by remember { mutableStateOf(0) }
    var currentPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var playersReady by remember { mutableStateOf(false) }
    
    // Calculate total duration
    val totalDuration = remember(segments) {
        segments.sumOf { it.duration.toDouble() }.toFloat()
    }
    
    // Предзагружаем плееры для каждого уникального видео
    val playerPool = remember(segments) {
        playersReady = false
        val uniqueVideos = segments.map { it.sourceVideoUri }.distinct()
        val pool = mutableMapOf<Uri, ExoPlayer>()
        
        uniqueVideos.forEach { uri ->
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = false
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            // Проверяем, все ли плееры готовы
                            coroutineScope.launch {
                                delay(100)
                                val allReady = pool.values.all { player ->
                                    player.playbackState == Player.STATE_READY
                                }
                                if (allReady) {
                                    playersReady = true
                                    isPlayerReady = true
                                }
                            }
                        }
                    }
                })
            }
            pool[uri] = player
        }
        pool.toMap()
    }
    
    // Находим текущий сегмент по позиции
    LaunchedEffect(currentPosition, segments) {
        var accumulatedTime = 0f
        for ((index, segment) in segments.withIndex()) {
            if (currentPosition >= accumulatedTime && currentPosition < accumulatedTime + segment.duration) {
                if (index != currentSegmentIndex) {
                    currentSegmentIndex = index
                    Log.d("CompositeVideoPlayer", "Switching to segment $index at position $currentPosition")
                }
                break
            }
            accumulatedTime += segment.duration
        }
    }
    
    // Обновляем текущий плеер при смене сегмента
    LaunchedEffect(currentSegmentIndex, segments) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        val segment = segments.getOrNull(currentSegmentIndex) ?: return@LaunchedEffect
        val newPlayer = playerPool[segment.sourceVideoUri] ?: return@LaunchedEffect
        
        // Сохраняем состояние воспроизведения
        val wasPlaying = currentPlayer?.isPlaying ?: isPlaying
        
        // Приостанавливаем предыдущий плеер
        currentPlayer?.playWhenReady = false
        
        // Настраиваем новый плеер
        currentPlayer = newPlayer
        
        // Вычисляем позицию в исходном видео
        var segmentStartInTimeline = 0f
        for (i in 0 until currentSegmentIndex) {
            segmentStartInTimeline += segments[i].duration
        }
        
        val positionInSegment = (currentPosition - segmentStartInTimeline).coerceIn(0f, segment.duration)
        val positionInSource = segment.inPoint + positionInSegment
        
        // Устанавливаем позицию в исходном видео
        newPlayer.seekTo((positionInSource * 1000).toLong())
        // Продолжаем воспроизведение если оно было
        newPlayer.playWhenReady = wasPlaying || isPlaying
        
        // Немедленно обновляем позицию при смене сегмента
        onPositionChange(currentPosition)
        
        Log.d("CompositeVideoPlayer", "Segment $currentSegmentIndex: seekTo ${positionInSource}s (inPoint=${segment.inPoint}, positionInSegment=$positionInSegment)")
    }
    
    // Управление воспроизведением
    LaunchedEffect(isPlaying) {
        currentPlayer?.playWhenReady = isPlaying
    }
    
    // Обновление позиции
    LaunchedEffect(currentPlayer, segments) {
        if (currentPlayer == null || segments.isEmpty()) return@LaunchedEffect
        
        var lastReportedPosition = -1f
        
        while (true) {
            delay(16) // ~60fps
            
            val player = currentPlayer ?: continue
            val segment = segments.getOrNull(currentSegmentIndex) ?: continue
            
            if (player.isPlaying) {
                val currentPosInSource = player.currentPosition / 1000f
                val positionInSegment = currentPosInSource - segment.inPoint
                
                // Вычисляем позицию на таймлайне
                var timelinePosition = positionInSegment
                for (i in 0 until currentSegmentIndex) {
                    timelinePosition += segments[i].duration
                }
                
                // Проверяем, не вышли ли за пределы сегмента
                if (positionInSegment >= segment.duration - 0.1f) { // Немного раньше для плавного перехода
                    // Переход к следующему сегменту
                    if (currentSegmentIndex < segments.size - 1) {
                        Log.d("CompositeVideoPlayer", "End of segment $currentSegmentIndex, moving to next")
                        
                        // Подготавливаем следующий плеер заранее
                        val nextSegment = segments[currentSegmentIndex + 1]
                        val nextPlayer = playerPool[nextSegment.sourceVideoUri]
                        nextPlayer?.let {
                            it.seekTo((nextSegment.inPoint * 1000).toLong())
                            it.playWhenReady = true
                        }
                        
                        // Обновляем индекс
                        currentSegmentIndex = currentSegmentIndex + 1
                    } else {
                        // Конец всех сегментов
                        player.playWhenReady = false
                        onPositionChange(segments.sumOf { it.duration.toDouble() }.toFloat())
                    }
                } else if (abs(timelinePosition - lastReportedPosition) > 0.01f) {
                    // Обновляем позицию только если изменение существенное
                    lastReportedPosition = timelinePosition
                    onPositionChange(timelinePosition)
                }
            }
        }
    }
    
    // Позиционирование при изменении извне
    LaunchedEffect(currentPosition, isPlaying) {
        if (!isPlaying && currentPlayer != null && segments.isNotEmpty()) {
            val segment = segments[currentSegmentIndex]
            var segmentStartInTimeline = 0f
            for (i in 0 until currentSegmentIndex) {
                segmentStartInTimeline += segments[i].duration
            }
            
            val positionInSegment = currentPosition - segmentStartInTimeline
            val positionInSource = segment.inPoint + positionInSegment
            
            if (abs(currentPlayer!!.currentPosition / 1000f - positionInSource) > 0.1f) {
                currentPlayer!!.seekTo((positionInSource * 1000).toLong())
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        currentPlayer?.let { player ->
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Custom controls overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Time display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(totalDuration),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
                
                // Progress slider
                Slider(
                    value = if (totalDuration > 0) currentPosition / totalDuration else 0f,
                    onValueChange = { value ->
                        val newPosition = value * totalDuration
                        onPositionChange(newPosition)
                        currentPlayer?.seekTo((newPosition * 1000).toLong())
                        // При seek также обновляем состояние воспроизведения
                        onPlayingStateChanged(currentPlayer?.isPlaying ?: false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
        
        // Индикатор загрузки
        if (!playersReady && segments.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    
    // Освобождаем ресурсы
    DisposableEffect(playerPool) {
        onDispose {
            playerPool.values.forEach { player ->
                player.release()
            }
        }
    }
}

private fun formatTime(seconds: Float): String {
    val totalSeconds = seconds.toInt()
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format("%d:%02d", mins, secs)
}