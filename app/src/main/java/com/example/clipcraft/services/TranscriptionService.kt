package com.example.clipcraft.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.clipcraft.data.remote.WhisperApiService
import com.example.clipcraft.data.remote.WhisperMapper
import com.example.clipcraft.data.remote.toMultipartBodyPart
import com.example.clipcraft.models.TranscriptionSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File // Добавьте этот импорт, если его нет
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionService @Inject constructor(
    private val context: Context,
    private val audioExtractor: AudioExtractorService,
    private val whisperApi: WhisperApiService
) {
    companion object {
        private const val TAG = "TranscriptionService"
        private const val MAX_VIDEO_DURATION_SECONDS = 300f // 5 минут
    }

    suspend fun transcribeVideo(
        uri: Uri,
        onProgress: (String) -> Unit = {}
    ): List<TranscriptionSegment> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Начинаем транскрибацию видео: $uri")
            onProgress("Проверка длительности видео...")

            val duration = audioExtractor.getVideoDuration(uri)
            Log.d(TAG, "Длительность видео: ${duration}s")
            if (duration > MAX_VIDEO_DURATION_SECONDS) {
                Log.e(TAG, "Видео слишком длинное: ${duration}s > ${MAX_VIDEO_DURATION_SECONDS}s")
                onProgress("Видео слишком длинное (макс. 5 минут)")
                return@withContext emptyList()
            }

            onProgress("Извлечение аудио...")
            val audioFile: File? = audioExtractor.extractAudio(uri)

            // Добавленная проверка на null
            if (audioFile == null) {
                Log.d(TAG, "Видео не содержит аудио дорожки, транскрибация невозможна.")
                onProgress("Видео не содержит аудио дорожки")
                return@withContext emptyList() // Возвращаем пустой список, если аудио отсутствует
            }

            // Отправляем аудио на сервер Whisper
            onProgress("Отправка аудио на сервер...")
            val response = whisperApi.transcribeAudio(audioFile.toMultipartBodyPart())
            Log.d(TAG, "Полный ответ от Whisper API: $response")
            Log.d(TAG, "Транскрибация успешно завершена. Получено ${response.segments.size} сегментов.")

            // Логируем все сегменты транскрипции
            val segments = WhisperMapper.toTranscriptionSegments(response.segments)
            segments.forEach { segment ->
                Log.d(TAG, "  [${segment.start}s - ${segment.end}s]: ${segment.text}")
            }

            segments

        } catch (e: HttpException) {
            Log.e(TAG, "HTTP ошибка при транскрибации", e)
            when (e.code()) {
                400 -> onProgress("Ошибка: файл слишком большой")
                422 -> onProgress("Ошибка: неверный формат аудио")
                500 -> onProgress("Ошибка сервера транскрибации")
                else -> onProgress("Ошибка транскрибации: ${e.code()}")
            }
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка транскрибации", e)
            onProgress("Ошибка: ${e.localizedMessage ?: "Неизвестная ошибка транскрибации"}")
            emptyList()
        }
    }

    suspend fun hasAudioTrack(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val extractor = android.media.MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).any { i ->
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                mime.startsWith("audio/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при проверке аудиодорожки", e)
            false
        } finally {
            extractor.release()
        }
    }
}