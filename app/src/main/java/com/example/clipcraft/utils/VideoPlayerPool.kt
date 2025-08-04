package com.example.clipcraft.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages a pool of ExoPlayer instances to prevent memory issues.
 * Implements player recycling and limits the number of concurrent players.
 */
object VideoPlayerPool {
    private const val TAG = "VideoPlayerPool"
    private const val MAX_PLAYERS = 3 // Limit concurrent players
    
    private val players = ConcurrentHashMap<String, ExoPlayer>()
    private val playerUsage = ConcurrentHashMap<String, Long>()
    private val _activePlayersCount = MutableStateFlow(0)
    val activePlayersCount: StateFlow<Int> = _activePlayersCount
    
    /**
     * Get or create a player for the given URI.
     * Automatically manages the pool size by releasing least recently used players.
     */
    fun getPlayer(context: Context, uri: Uri, key: String = uri.toString()): ExoPlayer {
        Log.d(TAG, "Requesting player for key: $key")
        
        // Update usage timestamp
        playerUsage[key] = System.currentTimeMillis()
        
        // Return existing player if available
        players[key]?.let { 
            Log.d(TAG, "Reusing existing player for key: $key")
            return it 
        }
        
        // Check if we need to release players
        if (players.size >= MAX_PLAYERS) {
            releaseLeastRecentlyUsedPlayer()
        }
        
        // Create new player with optimized load control for seamless playback
        Log.d(TAG, "Creating new player for key: $key")
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                50000,  // Min buffer duration (50 seconds)
                200000, // Max buffer duration (200 seconds)
                2500,   // Buffer for playback (2.5 seconds)
                5000    // Buffer for rebuffering (5 seconds)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        
        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setReleaseTimeoutMs(60000) // Release after 60 seconds of inactivity
            .build().apply {
                if (uri != Uri.EMPTY) {
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                }
                playWhenReady = false
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_IDLE -> Log.d(TAG, "Player state IDLE for: $key")
                            Player.STATE_READY -> Log.d(TAG, "Player state READY for: $key")
                            Player.STATE_ENDED -> {
                                Log.d(TAG, "Player state ENDED for: $key")
                                seekTo(0)
                                pause()
                            }
                        }
                    }
                })
            }
        
        players[key] = player
        _activePlayersCount.value = players.size
        
        return player
    }
    
    /**
     * Release a specific player by key.
     */
    fun releasePlayer(key: String) {
        Log.d(TAG, "Releasing player for key: $key")
        
        players.remove(key)?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
                player.release()
                Log.d(TAG, "Successfully released player for key: $key")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing player for key: $key", e)
            }
        }
        
        playerUsage.remove(key)
        _activePlayersCount.value = players.size
    }
    
    /**
     * Release all players.
     */
    fun releaseAll() {
        Log.d(TAG, "Releasing all players (count: ${players.size})")
        
        players.keys.toList().forEach { key ->
            releasePlayer(key)
        }
    }
    
    /**
     * Release players that haven't been used recently.
     */
    fun releaseUnusedPlayers(maxAgeMs: Long = 30000) {
        try {
            val currentTime = System.currentTimeMillis()
            
            playerUsage.entries
                .filter { (_, lastUsed) -> currentTime - lastUsed > maxAgeMs }
                .forEach { (key, _) ->
                    Log.d(TAG, "Releasing unused player: $key")
                    releasePlayer(key)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing unused players", e)
        }
    }
    
    private fun releaseLeastRecentlyUsedPlayer() {
        val lruKey = playerUsage.entries
            .minByOrNull { it.value }
            ?.key
        
        lruKey?.let {
            Log.d(TAG, "Releasing LRU player: $it")
            releasePlayer(it)
        }
    }
}