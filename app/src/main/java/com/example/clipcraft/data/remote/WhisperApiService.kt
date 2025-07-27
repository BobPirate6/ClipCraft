package com.example.clipcraft.data.remote

import com.example.clipcraft.models.TranscriptionSegment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.http.*
import java.io.File

// API интерфейс
interface WhisperApiService {
    @Multipart
    @POST("/api/transcribe")
    suspend fun transcribeAudio(
        @Part audio: MultipartBody.Part,
        @Part("language") language: String? = null,
        @Part("format") format: String = "detailed"
    ): WhisperResponse

    @GET("/health")
    suspend fun checkHealth(): HealthResponse
}

// Модели ответов
data class WhisperResponse(
    val status: String,
    val language: String,
    val language_probability: Float?,
    val duration: Float?,
    val text: String,
    val segments: List<WhisperSegment>
)

data class WhisperSegment(
    val id: Int,
    val start: Float,
    val end: Float,
    val text: String,
    val confidence: Float?,
    val words: List<WhisperWord>?
)

data class WhisperWord(
    val word: String,
    val start: Float,
    val end: Float,
    val confidence: Float
)

data class HealthResponse(
    val status: String
)

data class WhisperError(
    val status: String,
    val error: String,
    val code: String
)

// Mapper для конвертации
object WhisperMapper {
    fun toTranscriptionSegments(whisperSegments: List<WhisperSegment>): List<TranscriptionSegment> {
        val segments = mutableListOf<TranscriptionSegment>()

        whisperSegments.forEach { segment ->
            // Если есть пословная разметка - используем её
            if (!segment.words.isNullOrEmpty()) {
                segment.words.forEach { word ->
                    segments.add(
                        TranscriptionSegment(
                            start = word.start,
                            end = word.end,
                            text = word.word
                        )
                    )
                }
            } else {
                // Иначе добавляем весь сегмент
                segments.add(
                    TranscriptionSegment(
                        start = segment.start,
                        end = segment.end,
                        text = segment.text
                    )
                )
            }
        }

        return segments
    }
}

// Extension для создания MultipartBody.Part из файла
fun File.toMultipartBodyPart(name: String = "audio"): MultipartBody.Part {
    // MIME-тип для m4a
    val requestBody = this.asRequestBody("audio/mp4".toMediaType())

    return MultipartBody.Part.createFormData(name, this.name, requestBody)
}