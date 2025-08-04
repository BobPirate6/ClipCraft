package com.example.clipcraft.services

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.clipcraft.models.VideoSegment
import com.example.clipcraft.utils.TemporaryFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Service for rendering video segments into a single video file
 * Optimized for memory efficiency and performance
 */
@Singleton
@UnstableApi
class VideoRenderingService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val temporaryFileManager: TemporaryFileManager
) {
    companion object {
        private const val TAG = "VideoRenderingService"
        private const val TAG_RENDER = "VideoRender" // Отдельный тег для процесса рендеринга
        private const val OUTPUT_DIR = "rendered_videos"
    }
    
    data class RenderingProgress(
        val progress: Float,
        val currentSegment: Int,
        val totalSegments: Int
    )
    
    private val _renderingProgress = MutableStateFlow(RenderingProgress(0f, 0, 0))
    val renderingProgress: StateFlow<RenderingProgress> = _renderingProgress
    
    /**
     * Render video segments into a single file
     * Uses Media3 Transformer for efficient memory usage
     */
    suspend fun renderSegments(
        segments: List<VideoSegment>,
        onProgress: ((Float) -> Unit)? = null,
        outputFileName: String? = null
    ): String {
        if (segments.isEmpty()) {
            throw IllegalArgumentException("No segments to render")
        }
        
        val outputDir = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, OUTPUT_DIR)
            dir.mkdirs()
            dir
        }
        
        val fileName = outputFileName ?: "rendered_${UUID.randomUUID()}.mp4"
        val outputFile = File(outputDir, fileName)
        
        // Register for cleanup
        temporaryFileManager.registerTemporaryFile(outputFile.absolutePath)
        
        Log.d(TAG, "Starting render of ${segments.size} segments to ${outputFile.absolutePath}")
        Log.d(TAG_RENDER, "=== RENDER START === segments: ${segments.size}, output: $fileName")
        Log.d("clipcraftlogic", "=== VideoRenderingService.renderSegments ===")
        Log.d("clipcraftlogic", "Output file: ${outputFile.absolutePath}")
        Log.d("clipcraftlogic", "Segments to render:")
        segments.forEachIndexed { index, segment ->
            Log.d("clipcraftlogic", "  Segment $index: ${segment.sourceFileName}, ${segment.inPoint}-${segment.outPoint}s, uri: ${segment.sourceVideoUri}")
        }
        
        try {
            // If only one segment and it's the full video, just copy it
            if (segments.size == 1 && canUseFastCopy(segments[0])) {
                Log.d(TAG_RENDER, "Using fast copy for single segment")
                val result = fastCopyVideo(segments[0], outputFile)
                Log.d(TAG_RENDER, "=== RENDER COMPLETE === fast copy: $fileName")
                return result
            }
            
            // Otherwise use transformer - must be called from Main thread
            return renderWithTransformer(segments, outputFile, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Rendering failed", e)
            Log.e(TAG_RENDER, "=== RENDER FAILED === error: ${e.message}")
            withContext(Dispatchers.IO) {
                outputFile.delete()
            }
            throw e
        } finally {
            _renderingProgress.value = RenderingProgress(0f, 0, 0)
        }
    }
    
    /**
     * Check if we can use fast copy instead of re-encoding
     */
    private fun canUseFastCopy(segment: VideoSegment): Boolean {
        // Can fast copy if segment uses entire video
        val videoDuration = getVideoDuration(segment.sourceVideoUri)
        return videoDuration > 0 && 
               segment.inPoint <= 0.1f && 
               segment.duration >= videoDuration - 0.2f
    }
    
    /**
     * Fast copy video without re-encoding
     */
    private suspend fun fastCopyVideo(
        segment: VideoSegment,
        outputFile: File
    ): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Using fast copy for single full segment")
        
        // Handle content:// URIs properly
        if (segment.sourceVideoUri.scheme == "content") {
            Log.d(TAG, "Copying from content URI: ${segment.sourceVideoUri}")
            context.contentResolver.openInputStream(segment.sourceVideoUri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Cannot open input stream for URI: ${segment.sourceVideoUri}")
        } else {
            // Handle file:// URIs
            val inputFile = File(segment.sourceVideoUri.path ?: throw IllegalArgumentException("Invalid URI"))
            inputFile.copyTo(outputFile, overwrite = true)
        }
        
        return@withContext outputFile.absolutePath
    }
    
    /**
     * Render using Media3 Transformer
     */
    private suspend fun renderWithTransformer(
        segments: List<VideoSegment>,
        outputFile: File,
        onProgress: ((Float) -> Unit)?
    ): String = withContext(Dispatchers.Main) {
        Log.d("clipcraftlogic", "=== renderWithTransformer started ===")
        Log.d("clipcraftlogic", "Output: ${outputFile.absolutePath}")
        suspendCancellableCoroutine { continuation ->
        
        val transformer = Transformer.Builder(context)
            .setVideoMimeType("video/avc") // H.264 for compatibility
            .setAudioMimeType("audio/mp4a-latm") // AAC
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult
                ) {
                    Log.d(TAG, "Rendering completed successfully")
                    Log.d(TAG_RENDER, "=== RENDER COMPLETE === transformer: ${outputFile.name}, duration: ${exportResult.durationMs}ms")
                    Log.d("clipcraftlogic", "Transformer rendering completed")
                    Log.d("clipcraftlogic", "Output file: ${outputFile.absolutePath}")
                    Log.d("clipcraftlogic", "File exists: ${outputFile.exists()}, size: ${outputFile.length()}")
                    continuation.resume(outputFile.absolutePath)
                }
                
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Log.e(TAG, "Rendering failed", exportException)
                    Log.e("clipcraftlogic", "ERROR: Transformer rendering failed: ${exportException.message}")
                    continuation.resumeWithException(exportException)
                }
            })
            .build()
        
        // Build composition from segments
        val composition = buildComposition(segments) { progress, current ->
            _renderingProgress.value = RenderingProgress(
                progress = progress,
                currentSegment = current,
                totalSegments = segments.size
            )
            Log.d(TAG_RENDER, "Render progress: ${(progress * 100).toInt()}% (segment $current/${segments.size})")
            onProgress?.invoke(progress)
        }
        
        // Start export
        transformer.start(composition, outputFile.absolutePath)
        
            // Handle cancellation
            continuation.invokeOnCancellation {
                transformer.cancel()
                outputFile.delete()
            }
        }
    }
    
    /**
     * Build composition from segments
     */
    private fun buildComposition(
        segments: List<VideoSegment>,
        onProgress: (Float, Int) -> Unit
    ): Composition {
        val editedMediaItems = segments.mapIndexed { index, segment ->
            onProgress(index.toFloat() / segments.size, index + 1)
            
            val mediaItem = MediaItem.Builder()
                .setUri(segment.sourceVideoUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((segment.inPoint * 1000).toLong())
                        .setEndPositionMs((segment.outPoint * 1000).toLong())
                        .build()
                )
                .build()
            
            EditedMediaItem.Builder(mediaItem)
                .build()
        }
        
        return Composition.Builder(
            EditedMediaItemSequence(editedMediaItems)
        ).build()
    }
    
    /**
     * Get video duration for optimization checks
     */
    private fun getVideoDuration(uri: Uri): Float {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            retriever.release()
            duration / 1000f
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get video duration", e)
            Float.MAX_VALUE // Prevent fast copy on error
        }
    }
    
    /**
     * Clean up old rendered videos
     */
    suspend fun cleanupOldRenderedVideos(keepLast: Int = 5) = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.cacheDir, OUTPUT_DIR)
            if (!outputDir.exists()) return@withContext
            
            val files = outputDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?: return@withContext
            
            // Keep only the most recent files
            files.drop(keepLast).forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted old rendered video: ${file.name}")
                    temporaryFileManager.unregisterTemporaryFile(file.absolutePath)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old videos", e)
        }
    }
    
    /**
     * Delete specific rendered video
     */
    suspend fun deleteRenderedVideo(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists() && file.delete()) {
                Log.d(TAG, "Deleted rendered video: $path")
                temporaryFileManager.unregisterTemporaryFile(path)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete video: $path", e)
        }
        Unit
    }
}

