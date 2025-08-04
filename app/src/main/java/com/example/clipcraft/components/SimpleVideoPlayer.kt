package com.example.clipcraft.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Simple video player without pooling for final screen display
 * Creates a new player instance each time to ensure fresh state
 */
@Composable
fun SimpleVideoPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    val context = LocalContext.current
    
    Log.d("SimpleVideoPlayer", "Creating player for URI: $videoUri")
    Log.d("clipcraftlogic", "SimpleVideoPlayer: $videoUri")
    
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            Log.d("clipcraftlogic", "SimpleVideoPlayer READY for: $videoUri")
                        }
                        Player.STATE_ENDED -> {
                            Log.d("clipcraftlogic", "SimpleVideoPlayer ENDED, looping")
                        }
                        Player.STATE_IDLE -> {
                            Log.d("clipcraftlogic", "SimpleVideoPlayer IDLE")
                        }
                        Player.STATE_BUFFERING -> {
                            Log.d("clipcraftlogic", "SimpleVideoPlayer BUFFERING")
                        }
                    }
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("clipcraftlogic", "SimpleVideoPlayer ERROR: ${error.message}")
                }
            })
        }
    }
    
    // Initialize and update player when URI changes
    LaunchedEffect(videoUri) {
        Log.d("clipcraftlogic", "SimpleVideoPlayer setting URI: $videoUri")
        exoPlayer.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            // Seek to start to ensure video is visible
            seekTo(0)
        }
    }
    
    DisposableEffect(exoPlayer) {
        onDispose {
            Log.d("clipcraftlogic", "SimpleVideoPlayer disposing")
            exoPlayer.release()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    controllerAutoShow = showControls
                    controllerHideOnTouch = true
                    controllerShowTimeoutMs = 3000
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.useController = showControls
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}