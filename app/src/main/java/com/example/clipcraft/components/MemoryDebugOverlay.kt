package com.example.clipcraft.components

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clipcraft.BuildConfig
import com.example.clipcraft.utils.VideoPlayerPool
import kotlinx.coroutines.delay

/**
 * Debug overlay that shows memory usage and video player pool stats.
 * Only visible in debug builds.
 */
@Composable
fun MemoryDebugOverlay(
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.DEBUG) return
    
    val context = LocalContext.current
    var memoryInfo by remember { mutableStateOf(getMemoryInfo(context)) }
    val activePlayersCount by VideoPlayerPool.activePlayersCount.collectAsState()
    
    // Update memory info every second
    LaunchedEffect(Unit) {
        while (true) {
            memoryInfo = getMemoryInfo(context)
            delay(1000)
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd
    ) {
        Card(
            modifier = Modifier
                .padding(8.dp)
                .widthIn(max = 200.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.8f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Memory Debug",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Memory usage
                Text(
                    text = "Used: ${memoryInfo.usedMemoryMB}MB",
                    color = getMemoryColor(memoryInfo.usedMemoryPercentage),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Text(
                    text = "Total: ${memoryInfo.totalMemoryMB}MB",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Text(
                    text = "Usage: ${memoryInfo.usedMemoryPercentage}%",
                    color = getMemoryColor(memoryInfo.usedMemoryPercentage),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Video player pool stats
                Text(
                    text = "Players: $activePlayersCount/3",
                    color = getPlayerCountColor(activePlayersCount),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                
                // Native heap
                Text(
                    text = "Native: ${memoryInfo.nativeHeapMB}MB",
                    color = Color.Cyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private data class MemoryInfo(
    val usedMemoryMB: Int,
    val totalMemoryMB: Int,
    val usedMemoryPercentage: Int,
    val nativeHeapMB: Int
)

private fun getMemoryInfo(context: Context): MemoryInfo {
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxMemory = runtime.maxMemory() / (1024 * 1024)
    val percentage = ((usedMemory.toFloat() / maxMemory) * 100).toInt()
    
    // Get native heap size
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    
    val nativeHeap = android.os.Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
    
    return MemoryInfo(
        usedMemoryMB = usedMemory.toInt(),
        totalMemoryMB = maxMemory.toInt(),
        usedMemoryPercentage = percentage,
        nativeHeapMB = nativeHeap.toInt()
    )
}

private fun getMemoryColor(percentage: Int): Color {
    return when {
        percentage < 50 -> Color.Green
        percentage < 75 -> Color.Yellow
        percentage < 90 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
}

private fun getPlayerCountColor(count: Int): Color {
    return when (count) {
        0 -> Color.Gray
        1 -> Color.Green
        2 -> Color.Yellow
        else -> Color.Red
    }
}