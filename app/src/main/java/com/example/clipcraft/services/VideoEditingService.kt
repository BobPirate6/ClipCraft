package com.example.clipcraft.services

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toFile
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItemSequence
import com.example.clipcraft.models.VideoSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class VideoInfo(
    val duration: Float,
    val width: Int,
    val height: Int,
    val frameRate: Float
)

@Singleton
class VideoEditingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VideoEditingService"
        private const val TEMP_DIR = "video_editor_temp"
        private const val THUMBNAIL_DIR = "thumbnails"
    }
    
    private val tempDir = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
    private val thumbnailDir = File(context.cacheDir, THUMBNAIL_DIR).apply { mkdirs() }
    
    /**
     * Получение информации о видео
     */
    suspend fun getVideoInfo(videoUri: Uri): VideoInfo = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.div(1000f) ?: 0f
            
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull() ?: 30f
            
            VideoInfo(duration, width, height, frameRate)
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Генерация превью для сегмента
     */
    suspend fun generateThumbnails(
        videoUri: Uri,
        startTime: Float,
        endTime: Float,
        count: Int = 5
    ): List<Uri> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val thumbnails = mutableListOf<Uri>()
        
        try {
            retriever.setDataSource(context, videoUri)
            val duration = endTime - startTime
            val interval = duration / count
            
            for (i in 0 until count) {
                val timeUs = ((startTime + i * interval) * 1_000_000).toLong()
                val bitmap = retriever.getFrameAtTime(timeUs)
                
                bitmap?.let {
                    val thumbnailFile = File(thumbnailDir, "thumb_${System.currentTimeMillis()}_$i.jpg")
                    FileOutputStream(thumbnailFile).use { out ->
                        it.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    thumbnails.add(Uri.fromFile(thumbnailFile))
                }
            }
            
            thumbnails
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Создание временного видео для предпросмотра
     */
    @OptIn(UnstableApi::class)
    suspend fun createTempVideo(segments: List<VideoSegment>): String = withContext(Dispatchers.Main) {
        val outputFile = File(tempDir, "temp_${System.currentTimeMillis()}.mp4")
        
        try {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Log.d(TAG, "Temp video created successfully: ${outputFile.absolutePath}")
                            continuation.resume(outputFile.absolutePath)
                        }
                        
                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "Error creating temp video", exportException)
                            // При ошибке возвращаем первый сегмент
                            if (segments.isNotEmpty()) {
                                continuation.resume(segments.first().sourceVideoUri.toString())
                            } else {
                                continuation.resumeWithException(exportException)
                            }
                        }
                    })
                    .build()
                
                val editedMediaItems = segments.map { segment ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(segment.sourceVideoUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((segment.startTime * 1000).toLong())
                                .setEndPositionMs((segment.endTime * 1000).toLong())
                                .build()
                        )
                        .build()
                    
                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveAudio(false)
                        .build()
                }
                
                val sequence = EditedMediaItemSequence(editedMediaItems)
                val composition = Composition.Builder(sequence)
                    .build()
                
                transformer.start(composition, outputFile.absolutePath)
                
                continuation.invokeOnCancellation {
                    transformer.cancel()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create temp video, using first segment", e)
            // В случае любой ошибки возвращаем первый сегмент
            if (segments.isNotEmpty()) {
                segments.first().sourceVideoUri.toString()
            } else {
                throw e
            }
        }
    }
    
    /**
     * Создание финального видео с прогрессом
     */
    @UnstableApi
    suspend fun createFinalVideo(
        segments: List<VideoSegment>,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): String {
        val outputFile = File(outputPath)
        
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val transformer = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            Log.d(TAG, "Final video created successfully")
                            onProgress(1f)
                            continuation.resume(outputFile.absolutePath)
                        }
                        
                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            Log.e(TAG, "Error creating final video", exportException)
                            continuation.resumeWithException(exportException)
                        }
                    })
                    .build()
                
                val editedMediaItems = segments.map { segment ->
                    val mediaItem = MediaItem.Builder()
                        .setUri(segment.sourceVideoUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs((segment.startTime * 1000).toLong())
                                .setEndPositionMs((segment.endTime * 1000).toLong())
                                .build()
                        )
                        .build()
                    
                    EditedMediaItem.Builder(mediaItem)
                        .setRemoveAudio(false)
                        .build()
                }
                
                val sequence = EditedMediaItemSequence(editedMediaItems)
                val composition = Composition.Builder(sequence)
                    .build()
                
                transformer.start(composition, outputPath)
                
                continuation.invokeOnCancellation {
                    transformer.cancel()
                }
            }
        }
    }
    
    /**
     * Очистка временных файлов
     */
    fun clearTempFiles() {
        try {
            tempDir.listFiles()?.forEach { it.delete() }
            thumbnailDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing temp files", e)
        }
    }
}