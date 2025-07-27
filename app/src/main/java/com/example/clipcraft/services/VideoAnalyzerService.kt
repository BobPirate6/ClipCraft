package com.example.clipcraft.services

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.clipcraft.models.SceneAnalysis
import com.example.clipcraft.models.SelectedVideo
import com.example.clipcraft.models.TranscriptionSegment
import com.example.clipcraft.models.VideoAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VideoAnalyzerService – извлекает метаданные, определяет сцены (через [SceneDetectorService])
 * и формирует структуру [VideoAnalysis], пригодную для отправки на сервер ClipCraft.
 */
@Singleton
class VideoAnalyzerService @Inject constructor(
    private val appContext: Context
    // private val sceneDetector: SceneDetectorService, // <-- Зависимость удалена
) {

    companion object {
        /** JPEG‑качество кадра */
        private const val FRAME_QUALITY = 80
        /** Масштаб изображения ≤ 1.0 (0.5 == уменьшить в 2 раза)  */
        private const val SCALE_FACTOR = 0.5f
        /** Дополнительное ограничение на ширину кадра (px)  */
        private const val MAX_FRAME_WIDTH = 720
    }

    // ---------------------------------------------------------------------
    //  Публичное API
    // ---------------------------------------------------------------------

    /**
     * Быстро извлекает основную информацию о медиа‑файле.
     */
    suspend fun getVideoInfo(uri: Uri): SelectedVideo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(appContext, uri)

            val fileName = uri.lastPathSegment ?: "video_${System.currentTimeMillis()}.mp4"
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            val hasAudioTrack = hasAudio(uri)

            SelectedVideo(
                uri = uri,
                path = uri.toString(),
                fileName = fileName,
                durationMs = durationMs,
                hasAudio = hasAudioTrack,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * УПРОЩЕННЫЙ АНАЛИЗ: берет ОДИН кадр из центра видео.
     */
    suspend fun analyzeVideoWithScenes(video: SelectedVideo): VideoAnalysis =
        withContext(Dispatchers.IO) {
            Log.d("VideoAnalyzerService", "Упрощенный анализ видео: ${video.fileName}")

            // 1. Берем кадр из середины видео
            val middleFrameUs = (video.durationMs / 2) * 1_000
            val frameB64 = extractFrameBase64(video.uri, middleFrameUs)

            // 2. Создаем один "анализ сцены", который представляет все видео
            val singleScene = SceneAnalysis(
                sceneNumber = 1,
                startTime = 0f,
                endTime = video.durationMs / 1000f,
                frameBase64 = frameB64,
                transcription = emptyList<TranscriptionSegment>(),
            )

            // 3. Возвращаем результат в виде списка с одним элементом
            Log.d("VideoAnalyzerService", "Упрощенный анализ завершен. Кадр получен: ${frameB64 != null}")
            return@withContext VideoAnalysis(
                fileName = video.fileName,
                scenes = listOf(singleScene)
            )
        }


    // ---------------------------------------------------------------------
    //  Вспомогательные методы
    // ---------------------------------------------------------------------

    /**
     * Проверяет наличие аудио‑дорожки в контейнере.
     */
    private fun hasAudio(uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(appContext, uri, /* headers = */ null)
            (0 until extractor.trackCount).any { idx ->
                val mime = extractor.getTrackFormat(idx).getString(MediaFormat.KEY_MIME) ?: ""
                mime.startsWith("audio/")
            }
        } catch (e: Exception) {
            false
        } finally {
            extractor.release()
        }
    }

    /**
     * Извлекает кадр, масштабирует и кодирует в Base‑64 (JPEG).
     * @param timeUs время в микросекундах
     */
    private fun extractFrameBase64(uri: Uri, timeUs: Long): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            retriever.getFrameAtTime(timeUs)?.let { bmp ->
                val scaled = scaleBitmap(bmp, SCALE_FACTOR).let { candidate ->
                    if (candidate.width > MAX_FRAME_WIDTH) {
                        val extraScale = MAX_FRAME_WIDTH / candidate.width.toFloat()
                        scaleBitmap(candidate, extraScale).also { if (candidate != bmp) candidate.recycle() }
                    } else candidate
                }
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, FRAME_QUALITY, baos)
                if (scaled != bmp) scaled.recycle()
                bmp.recycle()
                Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            e.printStackTrace(); null
        } finally {
            retriever.release()
        }
    }

    /**
     * Быстрое масштабирование [Bitmap] (с фильтрацией).
     */
    private fun scaleBitmap(src: Bitmap, factor: Float): Bitmap {
        if (factor >= 0.999f) return src
        val w = (src.width * factor).toInt().coerceAtLeast(1)
        val h = (src.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, /* filter = */ true)
    }
}
