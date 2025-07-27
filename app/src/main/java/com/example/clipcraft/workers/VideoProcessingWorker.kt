package com.example.clipcraft.workers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.clipcraft.domain.usecase.ProcessVideosUseCase
import com.example.clipcraft.models.ExportSettings
import com.example.clipcraft.models.ProcessingState
import com.example.clipcraft.models.VideoAnalysis
import com.example.clipcraft.services.VideoAnalyzerService
import com.google.gson.Gson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.last
import java.util.UUID

@HiltWorker
class VideoProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processVideosUseCase: ProcessVideosUseCase,
    private val videoAnalyzer: VideoAnalyzerService,
    private val gson: Gson,
    private val sharedPreferences: SharedPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "VideoProcessingWorker"
        const val KEY_VIDEO_URIS = "VIDEO_URIS"
        const val KEY_USER_COMMAND = "USER_COMMAND"
        const val KEY_PROGRESS_MESSAGE = "PROGRESS_MESSAGE"
        const val KEY_RESULT_PATH = "RESULT_PATH"
        const val KEY_WORK_ID = "WORK_ID"
        const val KEY_ERROR_MESSAGE = "ERROR_MESSAGE"

        // Ключи для SharedPreferences
        private const val PREF_EDIT_PLAN_PREFIX = "work_edit_plan_"
        private const val PREF_VIDEO_ANALYSES_PREFIX = "work_video_analyses_"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork started")

        val workId = id.toString()
        val videoUrisStrings = inputData.getStringArray(KEY_VIDEO_URIS)
        val userCommand = inputData.getString(KEY_USER_COMMAND)

        Log.d(TAG, "Work ID: $workId")
        Log.d(TAG, "Input data - videos: ${videoUrisStrings?.size}, command: $userCommand")

        if (videoUrisStrings == null || userCommand == null) {
            Log.e(TAG, "Missing required input data")
            return Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Отсутствуют необходимые данные")
            )
        }

        return try {
            Log.d(TAG, "Getting video metadata for ${videoUrisStrings.size} videos")

            // Получаем метаданные по каждому ролику
            val selectedVideos = videoUrisStrings.mapNotNull { uriString ->
                try {
                    val uri = Uri.parse(uriString)
                    val video = videoAnalyzer.getVideoInfo(uri)
                    Log.d(TAG, "Video info retrieved: ${video?.fileName}")
                    video
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting video info for $uriString", e)
                    null
                }
            }

            if (selectedVideos.isEmpty()) {
                Log.e(TAG, "No valid videos found")
                return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Не удалось получить информацию о видео")
                )
            }

            Log.d(TAG, "Starting video processing use case with ${selectedVideos.size} videos")

            // Запускаем пайплайн
            val finalState = processVideosUseCase(
                selectedVideos = selectedVideos,
                userCommand = userCommand,
                exportSettings = ExportSettings()
            )
                .onEach { state ->
                    Log.d(TAG, "Processing state: ${state.javaClass.simpleName}")
                    if (state is ProcessingState.ProgressUpdate) {
                        Log.d(TAG, "Progress: ${state.message}")
                        setProgress(workDataOf(KEY_PROGRESS_MESSAGE to state.message))
                    }
                }
                .last()

            Log.d(TAG, "Final state: ${finalState.javaClass.simpleName}")

            when (finalState) {
                is ProcessingState.Success -> {
                    Log.d(TAG, "Processing successful!")
                    Log.d(TAG, "Result path: ${finalState.result}")
                    Log.d(TAG, "Edit plan segments: ${finalState.editPlan?.finalEdit?.size}")
                    Log.d(TAG, "Video analyses count: ${finalState.videoAnalyses?.size}")

                    // Сохраняем большие данные в SharedPreferences
                    try {
                        // Сохраняем план монтажа
                        val editPlanKey = "$PREF_EDIT_PLAN_PREFIX$workId"
                        val editPlanJson = gson.toJson(finalState.editPlan)
                        sharedPreferences.edit()
                            .putString(editPlanKey, editPlanJson)
                            .apply()
                        Log.d(TAG, "Edit plan saved to SharedPreferences with key: $editPlanKey")

                        // Сохраняем анализы видео (преобразуем Map в List)
                        val videoAnalysesKey = "$PREF_VIDEO_ANALYSES_PREFIX$workId"
                        val videoAnalysesList = finalState.videoAnalyses?.values?.toList() ?: emptyList()
                        val videoAnalysesJson = gson.toJson(videoAnalysesList)
                        sharedPreferences.edit()
                            .putString(videoAnalysesKey, videoAnalysesJson)
                            .apply()
                        Log.d(TAG, "Video analyses saved to SharedPreferences with key: $videoAnalysesKey")

                        // Передаем только маленькие данные через WorkManager
                        val output = workDataOf(
                            KEY_RESULT_PATH to finalState.result,
                            KEY_WORK_ID to workId
                        )

                        Log.d(TAG, "Returning success with minimal data")
                        Result.success(output)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving data to SharedPreferences", e)
                        Result.failure(
                            workDataOf(KEY_ERROR_MESSAGE to "Ошибка сохранения результатов: ${e.message}")
                        )
                    }
                }
                is ProcessingState.Error -> {
                    Log.e(TAG, "Processing failed: ${finalState.message}")
                    Result.failure(workDataOf(KEY_ERROR_MESSAGE to finalState.message))
                }
                else -> {
                    Log.e(TAG, "Unexpected final state: $finalState")
                    Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Unexpected final state: ${finalState.javaClass.simpleName}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in worker", e)
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error in worker")))
        } finally {
            // Очистка данных из SharedPreferences будет выполнена в MainViewModel после обработки
        }
    }
}