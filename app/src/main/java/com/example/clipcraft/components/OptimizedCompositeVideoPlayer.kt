package com.example.clipcraft.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.DefaultLoadControl
import com.example.clipcraft.models.VideoSegment
import com.example.clipcraft.utils.VideoPlayerPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Optimized video player that plays segments without creating multiple players.
 * Uses a single player and switches media items for better memory efficiency.
 */
@Composable
fun OptimizedCompositeVideoPlayer(
    segments: List<VideoSegment>,
    currentPosition: Float,
    isPlaying: Boolean,
    onPositionChange: (Float) -> Unit,
    onPlayingStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var currentSegmentIndex by remember { mutableStateOf(0) }
    var isPlayerReady by remember { mutableStateOf(false) }
    
    // Calculate total duration
    val totalDuration = remember(segments) {
        segments.sumOf { it.duration.toDouble() }.toFloat()
    }
    
    // Use a single player from the pool (already has optimized load control)
    val player = remember {
        VideoPlayerPool.getPlayer(context, Uri.EMPTY, "composite_player").apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
            // Важно для плавного перехода между сегментами
            (this as? ExoPlayer)?.apply {
                setSeekParameters(SeekParameters.EXACT)
                // Оптимизация для плавного воспроизведения
                setHandleAudioBecomingNoisy(true)
                setPauseAtEndOfMediaItems(false)
            }
        }
    }
    
    
    // Helper functions
    fun findSegmentByPosition(position: Float): Int {
        var accumulatedTime = 0f
        for ((index, segment) in segments.withIndex()) {
            if (position >= accumulatedTime && position < accumulatedTime + segment.duration) {
                return index
            }
            accumulatedTime += segment.duration
        }
        return segments.size - 1
    }
    
    fun calculateCurrentTimelinePosition(player: ExoPlayer, segmentList: List<VideoSegment>): Float {
        if (segmentList.isEmpty() || player.mediaItemCount == 0) return 0f
        
        val currentIndex = player.currentMediaItemIndex
        val currentPosMs = player.currentPosition
        
        var timelinePos = 0f
        
        // Add durations of previous segments
        for (i in 0 until currentIndex.coerceIn(0, segmentList.size - 1)) {
            timelinePos += segmentList[i].duration
        }
        
        // Add current position
        if (currentIndex < segmentList.size) {
            timelinePos += (currentPosMs / 1000f).coerceIn(0f, segmentList[currentIndex].duration)
        }
        
        return timelinePos
    }
    
    fun seekToPosition(timelinePosition: Float) {
        if (player.mediaItemCount == 0 || segments.isEmpty()) return
        
        val targetSegmentIndex = findSegmentByPosition(timelinePosition)
        
        if (targetSegmentIndex in 0 until minOf(segments.size, player.mediaItemCount)) {
            // Рассчитываем накопленное время до целевого сегмента
            var accumulatedTime = 0f
            for (i in 0 until targetSegmentIndex) {
                accumulatedTime += segments[i].duration
            }
            
            // Позиция внутри целевого сегмента
            val positionInSegment = (timelinePosition - accumulatedTime).coerceIn(0f, segments[targetSegmentIndex].duration)
            val seekPosMs = (positionInSegment * 1000).toLong()
            
            // Переходим к нужному элементу и позиции
            player.seekTo(targetSegmentIndex, seekPosMs)
            Log.d("OptimizedCompositePlayer", "Seeking to segment $targetSegmentIndex at ${positionInSegment}s")
        }
    }
    
    // Handle lifecycle to pause when backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                }
                Lifecycle.Event.ON_STOP -> {
                    // Stop the player when activity is stopped to prevent DeadObjectException
                    player.stop()
                    player.clearMediaItems()
                }
                Lifecycle.Event.ON_DESTROY -> {
                    player.pause()
                    player.clearMediaItems()
                }
                else -> {}
            }
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Properly stop and clear the player to avoid DeadObjectException
            try {
                player.stop()
                player.clearMediaItems()
                // Remove the player from the pool to ensure it's released
                VideoPlayerPool.releasePlayer("composite_player")
            } catch (e: Exception) {
                Log.e("OptimizedCompositePlayer", "Error disposing player", e)
            }
        }
    }
    
    LaunchedEffect(currentPosition, segments) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        val newSegmentIndex = findSegmentByPosition(currentPosition)
        if (newSegmentIndex != currentSegmentIndex) {
            currentSegmentIndex = newSegmentIndex
            Log.d("OptimizedCompositePlayer", "Position changed to segment $currentSegmentIndex")
        }
    }
    
    // Preload all media items
    LaunchedEffect(segments) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        Log.d("OptimizedCompositePlayer", "Loading ${segments.size} media items")
        player.stop()
        player.clearMediaItems()
        currentSegmentIndex = 0
        
        // Create media items with clipping configuration and unique IDs
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
                .setTag(index) // Для отслеживания позиции
                .build()
        }
        
        // Set media items directly - ExoPlayer will handle concatenation
        player.setMediaItems(mediaItems, false) // false = don't reset position
        player.prepare()
        
        Log.d("OptimizedCompositePlayer", "Prepared player with ${mediaItems.size} media items")
        
        // Seek to initial position after prepare
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !isPlayerReady) {
                    isPlayerReady = true
                    seekToPosition(currentPosition)
                    player.removeListener(this)
                }
            }
        })
    }
    
    // Handle position changes from external sources (user dragging slider)
    LaunchedEffect(currentPosition) {
        if (segments.isEmpty() || !isPlayerReady) return@LaunchedEffect
        
        // Only seek if not playing or if position changed significantly
        if (!player.isPlaying) {
            val currentTimelinePos = calculateCurrentTimelinePosition(player, segments)
            
            if (kotlin.math.abs(currentTimelinePos - currentPosition) > 0.1f) {
                seekToPosition(currentPosition)
            }
        }
    }
    
    // Control playback
    LaunchedEffect(isPlaying) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        Log.d("OptimizedCompositePlayer", "Setting playWhenReady=$isPlaying, currentState=${player.playbackState}")
        player.playWhenReady = isPlaying
    }
    
    // Monitor player state
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            // Используем onEvents для более эффективного обновления UI
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    when (player.playbackState) {
                        Player.STATE_READY -> {
                            if (!isPlayerReady) {
                                isPlayerReady = true
                                Log.d("OptimizedCompositePlayer", "Player ready, mediaItemCount=${player.mediaItemCount}")
                            }
                        }
                        Player.STATE_ENDED -> {
                            Log.d("OptimizedCompositePlayer", "Player reached end of all segments")
                            onPlayingStateChanged(false)
                            onPositionChange(totalDuration)
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d("OptimizedCompositePlayer", "Player buffering")
                        }
                        Player.STATE_IDLE -> {
                            Log.d("OptimizedCompositePlayer", "Player idle")
                        }
                    }
                }
                
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    onPlayingStateChanged(player.isPlaying)
                }
                
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    currentSegmentIndex = player.currentMediaItemIndex
                }
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val reasonStr = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
                    Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
                    Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
                    else -> "UNKNOWN"
                }
                
                val newIndex = player.currentMediaItemIndex
                Log.d("OptimizedCompositePlayer", 
                    "Media item transition: reason=$reasonStr, " +
                    "newIndex=$newIndex, " +
                    "mediaId=${mediaItem?.mediaId}, " +
                    "position=${player.currentPosition}ms, " +
                    "totalMediaItems=${player.mediaItemCount}")
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                super.onPositionDiscontinuity(oldPosition, newPosition, reason)
                
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    // Переход между сегментами
                    Log.d("OptimizedCompositePlayer", 
                        "Auto transition: ${oldPosition.mediaItemIndex} -> ${newPosition.mediaItemIndex}")
                }
            }
        }
        
        player.addListener(listener)
        
        onDispose {
            player.removeListener(listener)
        }
    }
    
    // Update position during playback
    LaunchedEffect(player, segments) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        var lastReportedPosition = -1f
        
        while (true) {
            delay(16) // ~60fps
            
            if (isPlayerReady && player.mediaItemCount > 0) {
                // Используем getCurrentMediaItemIndex и getCurrentPosition
                val currentItemIndex = player.currentMediaItemIndex
                val currentPositionMs = player.currentPosition
                
                // Рассчитываем общую позицию в timeline
                var timelinePosition = 0f
                
                // Добавляем длительность всех предыдущих сегментов
                for (i in 0 until currentItemIndex.coerceIn(0, segments.size - 1)) {
                    timelinePosition += segments[i].duration
                }
                
                // Добавляем текущую позицию в сегменте
                if (currentItemIndex < segments.size) {
                    val positionInSegment = (currentPositionMs / 1000f).coerceIn(0f, segments[currentItemIndex].duration)
                    timelinePosition += positionInSegment
                }
                
                // Обновляем позицию только если она изменилась значительно
                if (kotlin.math.abs(timelinePosition - lastReportedPosition) > 0.01f) {
                    onPositionChange(timelinePosition)
                    lastReportedPosition = timelinePosition
                }
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
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
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }
        
        // Loading indicator
        if (!isPlayerReady && segments.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
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