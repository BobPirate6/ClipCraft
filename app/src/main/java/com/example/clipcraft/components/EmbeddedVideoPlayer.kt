package com.example.clipcraft.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import android.util.Log
import androidx.media3.common.PlaybackException


@OptIn(UnstableApi::class)
@Composable
fun EmbeddedVideoPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    val exoPlayer = remember(videoUri) {  // Пересоздаем плеер при изменении URI
        Log.d("EmbeddedVideoPlayer", "Creating ExoPlayer for URI: $videoUri")
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_IDLE -> Log.d("EmbeddedVideoPlayer", "Player state: IDLE")
                        Player.STATE_BUFFERING -> Log.d("EmbeddedVideoPlayer", "Player state: BUFFERING")
                        Player.STATE_READY -> {
                            Log.d("EmbeddedVideoPlayer", "Player state: READY, duration: $duration")
                            duration = duration
                        }
                        Player.STATE_ENDED -> {
                            Log.d("EmbeddedVideoPlayer", "Player state: ENDED")
                            // Перематываем в начало при окончании
                            seekTo(0)
                            pause()
                        }
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("EmbeddedVideoPlayer", "Player error: ${error.message}", error)
                    Log.e("EmbeddedVideoPlayer", "Error code: ${error.errorCode}")
                    Log.e("EmbeddedVideoPlayer", "URI that failed: $videoUri")
                }
            })
        }
    }

    // Обновление позиции
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            delay(100)
        }
    }

    // Автоскрытие контролов
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    DisposableEffect(exoPlayer) {  // Привязываем к exoPlayer, чтобы освобождать при пересоздании
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Видео
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false // Используем кастомные контролы
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Кастомные контролы
        VideoControls(
            isPlaying = isPlaying,
            isMuted = isMuted,
            currentPosition = currentPosition,
            duration = duration,
            showControls = showControls,
            onPlayPauseClick = {
                if (isPlaying) {
                    exoPlayer.pause()
                } else {
                    exoPlayer.play()
                }
            },
            onMuteClick = {
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else 1f
            },
            onSeek = { position ->
                exoPlayer.seekTo(position.toLong())
            },
            onControlsVisibilityChange = { show ->
                showControls = show
            }
        )
    }
}

@Composable
private fun VideoControls(
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPosition: Long,
    duration: Long,
    showControls: Boolean,
    onPlayPauseClick: () -> Unit,
    onMuteClick: () -> Unit,
    onSeek: (Float) -> Unit,
    onControlsVisibilityChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .noRippleClickable { onControlsVisibilityChange(!showControls) }
    ) {
        // Затемнение при показе контролов
        if (showControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        // Контролы
        androidx.compose.animation.AnimatedVisibility(
            visible = showControls,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Центральная кнопка play/pause
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Нижняя панель
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Время
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Прогресс
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() else 0f,
                        onValueChange = onSeek,
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    // Длительность
                    Text(
                        text = formatTime(duration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Кнопка звука
                    IconButton(
                        onClick = onMuteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Включить звук" else "Выключить звук",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// Расширение для клика без ripple эффекта
@Composable
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier {
    return this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) { onClick() }
}