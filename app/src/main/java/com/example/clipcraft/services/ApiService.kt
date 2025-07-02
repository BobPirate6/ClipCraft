package com.example.clipcraft.services

import com.example.clipcraft.models.EditResp
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.http.*
import javax.inject.Inject
import javax.inject.Singleton

interface ClipCraftApi {
    @Multipart
    @POST("edit")
    suspend fun editLegacy(
        @Part("command") command: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): EditResp

    @Multipart
    @POST("analyze-and-plan")
    suspend fun analyzeAndCreatePlan(
        @Part("command") command: RequestBody,
        @Part files: List<MultipartBody.Part>
    ): EditPlanResponse

    @GET("health")
    suspend fun healthCheck(): Map<String, String>
}

@Singleton
class ApiService @Inject constructor(
    private val retrofit: Retrofit
) {
    private val api = retrofit.create(ClipCraftApi::class.java)

    suspend fun editVideo(
        videoPaths: List<String>,
        userCommand: String
    ): EditResp {
        kotlinx.coroutines.delay(2000)
        return EditResp(
            success = true,
            message = "Видео успешно обработано",
            downloadUrl = "/path/to/processed_video.mp4"
        )
    }

    suspend fun analyzeAndCreatePlan(
        videoPaths: List<String>,
        userCommand: String,
        onProgress: (String) -> Unit = {}
    ): Result<EditPlanResponse> {
        return try {
            onProgress("Анализ видео...")
            kotlinx.coroutines.delay(1000)

            val response = EditPlanResponse(
                final_edit = listOf(
                    VideoSegment("video1.mp4", 0.0, 5.0),
                    VideoSegment("video2.mp4", 2.0, 7.0)
                )
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}