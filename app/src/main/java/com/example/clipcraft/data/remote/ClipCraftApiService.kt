package com.example.clipcraft.data.remote

import com.example.clipcraft.models.*
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import android.util.Log

// ─────────────────────────────────────────────────────────────
//                    API SERVICE
// ─────────────────────────────────────────────────────────────
interface ClipCraftApiService {

    @GET("/")
    suspend fun getStatus(): ApiStatus

    @GET("/health")
    suspend fun checkHealth(): ApiHealth

    @POST("/analyze")
    suspend fun analyzeVideos(
        @Body request: ApiAnalyzeRequest
    ): ApiEditPlanResponse
}

// ─────────────────────────────────────────────────────────────
//                    REQUEST MODELS (Уже в Models.kt)
// ─────────────────────────────────────────────────────────────
// Эти модели теперь определены в Models.kt для централизации.
// data class ApiAnalyzeRequest(...)
// data class ApiVideoAnalysis(...)
// data class ApiSceneAnalysis(...)
// data class ApiTranscriptionSegment(...)

// ─────────────────────────────────────────────────────────────
//                    RESPONSE MODELS (Уже в Models.kt)
// ─────────────────────────────────────────────────────────────
// Эти модели теперь определены в Models.kt для централизации.
// data class ApiEditPlanResponse(...)
// data class ApiEditSegment(...)
// data class ApiStatus(...)
// data class ApiHealth(...)

// ─────────────────────────────────────────────────────────────
//                    MAPPER (ОБНОВЛЕНО)
// ─────────────────────────────────────────────────────────────
object ApiMapper {

    // Обновленная функция toApiRequest, принимающая EditingState
    fun toApiRequest(
        userCommand: String,
        videoAnalyses: List<VideoAnalysis>,
        editingState: EditingState // НОВЫЙ ПАРАМЕТР
    ): ApiAnalyzeRequest {
        Log.d("ApiMapper", "=== Подготовка запроса к серверу ===")
        Log.d("ApiMapper", "Команда пользователя: '$userCommand'")
        Log.d("ApiMapper", "Количество видео: ${videoAnalyses.size}")
        Log.d("ApiMapper", "Режим редактирования: ${editingState.mode}")

        videoAnalyses.forEach { analysis ->
            Log.d("ApiMapper", "Видео '${analysis.fileName}' содержит ${analysis.scenes.size} сцен")
        }

        // Преобразуем EditPlan в List<ApiEditSegment> для previous_plan
        val previousPlanApiSegments = editingState.previousPlan?.finalEdit?.map { segment ->
            ApiEditSegment(
                sourceVideo = segment.sourceVideo,
                startTime = segment.startTime,
                endTime = segment.endTime,
                notes = segment.notes
            )
        } ?: emptyList()


        return ApiAnalyzeRequest(
            userCommand = userCommand,
            videosAnalysis = videoAnalyses.map { analysis ->
                // Проверяем есть ли транскрипция во всех сценах
                val hasTranscription = analysis.scenes.any { it.transcription.isNotEmpty() }
                val audioInfo = when {
                    !hasTranscription -> "Видео без звука или речи"
                    else -> null
                }

                ApiVideoAnalysis(
                    filename = analysis.fileName,
                    scenes = analysis.scenes.map { scene ->
                        ApiSceneAnalysis(
                            sceneNumber = scene.sceneNumber,
                            startTime = scene.startTime,
                            endTime = scene.endTime,
                            frameBase64 = scene.frameBase64 ?: "",
                            transcription = scene.transcription.map { segment ->
                                ApiTranscriptionSegment(
                                    start = segment.start,
                                    end = segment.end,
                                    text = segment.text
                                )
                            }
                        )
                    },
                    hasAudio = hasTranscription,
                    audioInfo = audioInfo
                )
            },
            // Заполняем новые поля на основе EditingState
            editMode = editingState.mode == ProcessingMode.EDIT,
            editCommand = editingState.editCommand,
            previousPlan = previousPlanApiSegments
        )
    }

    fun fromApiResponse(response: ApiEditPlanResponse): EditPlan {
        Log.d("ApiMapper", "=== Получен ответ от сервера ===")
        Log.d("ApiMapper", "Количество сегментов: ${response.finalEdit.size}")

        response.finalEdit.forEachIndexed { index, segment ->
            Log.d("ApiMapper", "Сегмент $index: sourceVideo='${segment.sourceVideo}', " +
                    "start=${segment.startTime}s, end=${segment.endTime}s, notes='${segment.notes}'")
        }

        return EditPlan(
            finalEdit = response.finalEdit.map { segment ->
                EditSegment(
                    sourceVideo = segment.sourceVideo,
                    startTime = segment.startTime,
                    endTime = segment.endTime,
                    notes = segment.notes
                )
            }
        )
    }
}
