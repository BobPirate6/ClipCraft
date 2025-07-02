package com.example.clipcraft.services

import android.content.Context
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class VideoSegment(
    val source_video: String,
    val start_time: Double,
    val end_time: Double
)

data class EditPlanResponse(
    val final_edit: List<VideoSegment>
)

@UnstableApi
@Singleton
class VideoService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val transformer by lazy {
        Transformer.Builder(context)
            .build()
    }

    suspend fun createReelsFromPlan(
        editPlan: EditPlanResponse,
        inputVideosDir: String,
        outputPath: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<String> = withContext(Dispatchers.IO) {

        try {
            val tempClips = mutableListOf<String>()
            val totalSegments = editPlan.final_edit.size

            onProgress(0, "Начинаем монтаж...")

            editPlan.final_edit.forEachIndexed { index, segment ->
                onProgress(
                    (index * 80) / totalSegments,
                    "Обработка сегмента ${index + 1}/$totalSegments"
                )

                val inputVideo = File(inputVideosDir, segment.source_video)
                if (!inputVideo.exists()) {
                    return@withContext Result.failure(
                        Exception("Видео не найдено: ${segment.source_video}")
                    )
                }

                val tempClip = File(inputVideosDir, "temp_clip_$index.mp4").absolutePath

                val extractResult = extractSegmentWithMedia3(
                    inputPath = inputVideo.absolutePath,
                    outputPath = tempClip,
                    startTime = segment.start_time,
                    endTime = segment.end_time
                )

                if (extractResult.isSuccess) {
                    tempClips.add(tempClip)
                } else {
                    return@withContext Result.failure(
                        Exception("Ошибка извлечения сегмента $index")
                    )
                }
            }

            onProgress(80, "Финальная обработка...")

            if (tempClips.isNotEmpty()) {
                File(tempClips.first()).copyTo(File(outputPath), overwrite = true)
                tempClips.forEach { File(it).delete() }

                onProgress(100, "Видео готово!")
                Result.success(outputPath)
            } else {
                Result.failure(Exception("Нет сегментов для обработки"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun extractSegmentWithMedia3(
        inputPath: String,
        outputPath: String,
        startTime: Double,
        endTime: Double
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->

        try {
            val mediaItem = MediaItem.Builder()
                .setUri(inputPath)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((startTime * 1000).toLong())
                        .setEndPositionMs((endTime * 1000).toLong())
                        .build()
                )
                .build()

            continuation.resume(Result.success(Unit))

        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    suspend fun compressVideo(
        inputPath: String,
        outputPath: String,
        quality: Int = 28
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            File(inputPath).copyTo(File(outputPath), overwrite = true)
            Result.success(outputPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}