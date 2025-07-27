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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    var lastLoadedUri by remember { mutableStateOf<Uri?>(null) }
    
    // Calculate total duration
    val totalDuration = remember(segments) {
        segments.sumOf { it.duration.toDouble() }.toFloat()
    }
    
    // Use a single player from the pool
    val player = remember {
        VideoPlayerPool.getPlayer(context, Uri.EMPTY, "composite_player").apply {
            playWhenReady = false
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }
    
    // Handle lifecycle to pause when backgrounded
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
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
            player.pause()
            player.clearMediaItems()
        }
    }
    
    // Find current segment by position
    LaunchedEffect(currentPosition, segments) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        var accumulatedTime = 0f
        for ((index, segment) in segments.withIndex()) {
            if (currentPosition >= accumulatedTime && currentPosition < accumulatedTime + segment.duration) {
                if (index != currentSegmentIndex) {
                    currentSegmentIndex = index
                    Log.d("OptimizedCompositePlayer", "Switching to segment $index at position $currentPosition")
                }
                break
            }
            accumulatedTime += segment.duration
        }
    }
    
    // Load media for current segment
    LaunchedEffect(currentSegmentIndex, segments) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        val segment = segments.getOrNull(currentSegmentIndex) ?: return@LaunchedEffect
        
        // Only reload if URI changed
        if (segment.sourceVideoUri != lastLoadedUri) {
            Log.d("OptimizedCompositePlayer", "Loading new media: ${segment.sourceVideoUri}")
            lastLoadedUri = segment.sourceVideoUri
            isPlayerReady = false
            
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(segment.sourceVideoUri))
            player.prepare()
        }
        
        // Calculate position in source video
        var segmentStartInTimeline = 0f
        for (i in 0 until currentSegmentIndex) {
            segmentStartInTimeline += segments[i].duration
        }
        
        val positionInSegment = (currentPosition - segmentStartInTimeline).coerceIn(0f, segment.duration)
        val positionInSource = segment.inPoint + positionInSegment
        
        // Seek to position
        player.seekTo((positionInSource * 1000).toLong())
        
        Log.d("OptimizedCompositePlayer", "Segment $currentSegmentIndex: seekTo ${positionInSource}s")
    }
    
    // Control playback
    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }
    
    // Monitor player state
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isPlayerReady = true
                        Log.d("OptimizedCompositePlayer", "Player ready")
                    }
                    Player.STATE_ENDED -> {
                        // Move to next segment or stop
                        coroutineScope.launch {
                            if (currentSegmentIndex < segments.size - 1) {
                                val nextPos = segments.take(currentSegmentIndex + 1)
                                    .sumOf { it.duration.toDouble() }.toFloat()
                                onPositionChange(nextPos)
                            } else {
                                onPlayingStateChanged(false)
                            }
                        }
                    }
                }
            }
            
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingStateChanged(playing)
            }
        }
        
        player.addListener(listener)
        
        onDispose {
            player.removeListener(listener)
        }
    }
    
    // Update position during playback
    LaunchedEffect(player, segments, currentSegmentIndex) {
        if (segments.isEmpty()) return@LaunchedEffect
        
        while (true) {
            delay(16) // ~60fps
            
            if (player.isPlaying) {
                val segment = segments.getOrNull(currentSegmentIndex) ?: continue
                val currentPosInSource = player.currentPosition / 1000f
                val positionInSegment = (currentPosInSource - segment.inPoint).coerceIn(0f, segment.duration)
                
                // Calculate timeline position
                var timelinePosition = positionInSegment
                for (i in 0 until currentSegmentIndex) {
                    timelinePosition += segments[i].duration
                }
                
                // Check segment boundary
                if (positionInSegment >= segment.duration - 0.05f) {
                    if (currentSegmentIndex < segments.size - 1) {
                        // Move to next segment
                        val nextPos = segments.take(currentSegmentIndex + 1)
                            .sumOf { it.duration.toDouble() }.toFloat()
                        onPositionChange(nextPos)
                    }
                } else {
                    onPositionChange(timelinePosition)
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