package com.example.clipcraft.workers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.clipcraft.domain.usecase.ProcessVideosUseCase
import com.example.clipcraft.models.*
import com.example.clipcraft.services.VideoAnalyzerService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.last

/**
 * Worker для редактирования существующих видео.
 * Использует предыдущий план монтажа и анализ видео для ускорения процесса.
 */
@HiltWorker
class EditWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val processVideosUseCase: ProcessVideosUseCase,
    private val videoAnalyzer: VideoAnalyzerService,
    private val gson: Gson,
    private val sharedPreferences: SharedPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_VIDEO_URIS = "VIDEO_URIS"
        const val KEY_ORIGINAL_COMMAND = "ORIGINAL_COMMAND"
        const val KEY_EDIT_COMMAND = "EDIT_COMMAND"
        const val KEY_PREVIOUS_PLAN = "PREVIOUS_PLAN"
        const val KEY_VIDEO_ANALYSES = "VIDEO_ANALYSES"
        const val KEY_PROGRESS_MESSAGE = "PROGRESS_MESSAGE"
        const val KEY_RESULT_PATH = "RESULT_PATH"
        const val KEY_WORK_ID = "WORK_ID"
        const val KEY_ERROR_MESSAGE = "ERROR_MESSAGE"

        private const val TAG = "EditWorker"

        // Ключи для SharedPreferences
        private const val PREF_EDIT_PLAN_PREFIX = "work_edit_plan_"
        private const val PREF_VIDEO_ANALYSES_PREFIX = "work_video_analyses_"
    }

    override suspend fun doWork(): Result {
        val workId = id.toString()
        Log.d(TAG, "EditWorker started with ID: $workId")
        
        // Пытаемся получить ключ для данных из SharedPreferences
        val workDataKey = inputData.getString("WORK_DATA_KEY")
        Log.d(TAG, "Work data key: $workDataKey")
        
        if (workDataKey == null) {
            // Fallback: пытаемся получить данные напрямую из inputData (старый метод)
            Log.d(TAG, "No work data key found, trying direct data extraction")
            val videoUrisStrings = inputData.getStringArray(KEY_VIDEO_URIS)
            val originalCommand = inputData.getString(KEY_ORIGINAL_COMMAND)
            val editCommand = inputData.getString(KEY_EDIT_COMMAND)
            val previousPlanJson = inputData.getString(KEY_PREVIOUS_PLAN)
            val videoAnalysesJson = inputData.getString(KEY_VIDEO_ANALYSES)
            
            // Расширенное логирование входных данных
            Log.d(TAG, "=== Direct input data debug ===")
            Log.d(TAG, "  videoUrisStrings: ${videoUrisStrings?.size ?: "null"}")
            Log.d(TAG, "  originalCommand: ${originalCommand ?: "null"}")
            Log.d(TAG, "  editCommand: ${editCommand ?: "null"}")
            Log.d(TAG, "  previousPlanJson length: ${previousPlanJson?.length ?: "null"}")
            Log.d(TAG, "  videoAnalysesJson length: ${videoAnalysesJson?.length ?: "null"}")
            
            if (videoUrisStrings == null || originalCommand == null || editCommand == null ||
                previousPlanJson == null || videoAnalysesJson == null) {
                Log.e(TAG, "=== FAILURE: Missing required parameters ===")
                return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Отсутствуют необходимые параметры для редактирования")
                )
            }
            
            return processEditWork(workId, videoUrisStrings, originalCommand, editCommand, 
                                 previousPlanJson, videoAnalysesJson)
        }
        
        // Загружаем данные из SharedPreferences
        val editDataJson = sharedPreferences.getString(workDataKey, null)
        if (editDataJson == null) {
            Log.e(TAG, "=== FAILURE: No data found in SharedPreferences for key: $workDataKey ===")
            return Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Не удалось загрузить данные для редактирования")
            )
        }
        
        Log.d(TAG, "Loaded edit data from SharedPreferences, size: ${editDataJson.length}")
        
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val editData = gson.fromJson<Map<String, Any>>(editDataJson, type)
            
            @Suppress("UNCHECKED_CAST")
            val videoUrisStrings = (editData["videoUris"] as? List<String>)?.toTypedArray()
            val originalCommand = editData["originalCommand"] as? String
            val editCommand = editData["editCommand"] as? String
            val previousPlan = gson.toJson(editData["previousPlan"])
            val videoAnalyses = gson.toJson(editData["originalVideoAnalyses"])
            
            Log.d(TAG, "=== Parsed data from SharedPreferences ===")
            Log.d(TAG, "  videoUris count: ${videoUrisStrings?.size ?: "null"}")
            Log.d(TAG, "  originalCommand: $originalCommand")
            Log.d(TAG, "  editCommand: $editCommand")
            Log.d(TAG, "  previousPlan length: ${previousPlan.length}")
            Log.d(TAG, "  videoAnalyses length: ${videoAnalyses.length}")
            
            // Очищаем данные из SharedPreferences после загрузки
            sharedPreferences.edit().remove(workDataKey).apply()
            Log.d(TAG, "Cleaned up SharedPreferences for key: $workDataKey")
            
            if (videoUrisStrings == null || originalCommand == null || editCommand == null) {
                Log.e(TAG, "=== FAILURE: Missing data after parsing ===")
                return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Некорректные данные для редактирования")
                )
            }
            
            processEditWork(workId, videoUrisStrings, originalCommand, editCommand,
                          previousPlan, videoAnalyses)
            
        } catch (e: Exception) {
            Log.e(TAG, "=== FAILURE: Error parsing edit data ===", e)
            // Очищаем данные в случае ошибки
            sharedPreferences.edit().remove(workDataKey).apply()
            Result.failure(
                workDataOf(KEY_ERROR_MESSAGE to "Ошибка обработки данных: ${e.message}")
            )
        }
    }
    
    private suspend fun processEditWork(
        workId: String,
        videoUrisStrings: Array<String>,
        originalCommand: String,
        editCommand: String,
        previousPlanJson: String,
        videoAnalysesJson: String
    ): Result {
        Log.d(TAG, "processEditWork started for work ID: $workId")


        return try {
            // Десериализуем предыдущий план и анализы
            val previousPlan = try {
                Log.d(TAG, "Parsing previous plan...")
                val plan = gson.fromJson(previousPlanJson, EditPlan::class.java)
                Log.d(TAG, "Previous plan parsed successfully: ${plan.finalEdit.size} segments")
                plan
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing previous plan", e)
                Log.e(TAG, "previousPlanJson content: ${previousPlanJson.take(500)}...")
                null
            }

            if (previousPlan == null) {
                Log.e(TAG, "=== FAILURE: Could not parse previous plan ===")
                return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Не удалось прочитать предыдущий план монтажа")
                )
            }

            // ОТЛАДКА: Логируем содержимое предыдущего плана
            Log.d(TAG, "=== Предыдущий план монтажа ===")
            previousPlan.finalEdit.forEachIndexed { index, segment ->
                Log.d(TAG, "Сегмент $index: sourceVideo='${segment.sourceVideo}', start=${segment.startTime}, end=${segment.endTime}")
            }

            val type = object : TypeToken<Map<String, VideoAnalysis>>() {}.type
            val videoAnalysesMap: Map<String, VideoAnalysis>? = try {
                Log.d(TAG, "Parsing video analyses...")
                val analyses = gson.fromJson<Map<String, VideoAnalysis>>(videoAnalysesJson, type)
                Log.d(TAG, "Video analyses parsed successfully: ${analyses?.size ?: 0} items")
                analyses
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing video analyses", e)
                Log.e(TAG, "videoAnalysesJson content: ${videoAnalysesJson.take(500)}...")
                null
            }

            // Если анализ видео пустой или null, создаем новый
            val finalVideoAnalysesMap = if (videoAnalysesMap.isNullOrEmpty()) {
                Log.w(TAG, "Video analyses is empty or null, will perform full analysis")
                null
            } else {
                // ОТЛАДКА: Логируем содержимое анализов
                Log.d(TAG, "=== Загруженные анализы видео ===")
                videoAnalysesMap.forEach { (fileName, analysis) ->
                    Log.d(TAG, "Анализ для '$fileName': ${analysis.scenes.size} сцен")
                }
                videoAnalysesMap
            }

            // Получаем информацию о видео
            Log.d(TAG, "Getting video info for ${videoUrisStrings.size} URIs...")
            val selectedVideos = videoUrisStrings.mapNotNull { uriString ->
                try {
                    val uri = if (uriString.startsWith("content://")) {
                        Uri.parse(uriString)
                    } else {
                        uriString.toUri()
                    }
                    val video = videoAnalyzer.getVideoInfo(uri)
                    Log.d(TAG, "Загружено видео: ${video?.fileName} из URI: $uriString")
                    video
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting video info for URI: $uriString", e)
                    null
                }
            }

            if (selectedVideos.isEmpty()) {
                Log.e(TAG, "=== FAILURE: No valid videos found ===")
                return Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to "Не удалось получить информацию о видео")
                )
            }

            // ОТЛАДКА: Логируем загруженные видео
            Log.d(TAG, "=== Загруженные видео для редактирования ===")
            selectedVideos.forEach { video ->
                Log.d(TAG, "Видео: ${video.fileName}, URI: ${video.uri}")
            }

            // Создаем EditingState для режима редактирования
            val editingState = EditingState(
                mode = ProcessingMode.EDIT,
                originalCommand = originalCommand,
                editCommand = editCommand,
                previousPlan = previousPlan,
                originalVideoAnalyses = finalVideoAnalysesMap
            )

            Log.d(TAG, "Starting ProcessVideosUseCase in EDIT mode...")
            Log.d(TAG, "  originalCommand: $originalCommand")
            Log.d(TAG, "  editCommand: $editCommand")
            Log.d(TAG, "  previousPlan segments: ${previousPlan.finalEdit.size}")
            Log.d(TAG, "  videoAnalysesMap size: ${finalVideoAnalysesMap?.size ?: 0}")

            // Запускаем процесс с использованием существующего анализа (если есть)
            val finalState = processVideosUseCase(
                selectedVideos = selectedVideos,
                userCommand = originalCommand, // Исходная команда
                exportSettings = ExportSettings(),
                editingState = editingState,
                videoAnalysesMap = finalVideoAnalysesMap // Переиспользуем анализ если есть
            )
                .onEach { state ->
                    Log.d(TAG, "Processing state: ${state.javaClass.simpleName}")
                    if (state is ProcessingState.ProgressUpdate) {
                        Log.d(TAG, "Progress: ${state.message}")
                        setProgress(workDataOf(KEY_PROGRESS_MESSAGE to state.message))
                    }
                }
                .last()

            Log.d(TAG, "ProcessVideosUseCase completed with state: ${finalState.javaClass.simpleName}")

            when (finalState) {
                is ProcessingState.Success -> {
                    Log.d(TAG, "Edit processing successful!")
                    Log.d(TAG, "  Result path: ${finalState.result}")
                    Log.d(TAG, "  Edit plan segments: ${finalState.editPlan?.finalEdit?.size}")
                    Log.d(TAG, "  Video analyses count: ${finalState.videoAnalyses?.size}")

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
                        val newVideoAnalysesJson = gson.toJson(videoAnalysesList)
                        sharedPreferences.edit()
                            .putString(videoAnalysesKey, newVideoAnalysesJson)
                            .apply()
                        Log.d(TAG, "Video analyses saved to SharedPreferences with key: $videoAnalysesKey")

                        // Передаем только маленькие данные через WorkManager
                        val output = workDataOf(
                            KEY_RESULT_PATH to finalState.result,
                            KEY_WORK_ID to workId
                        )

                        Log.d(TAG, "Returning success with minimal data")
                        Log.d(TAG, "  Result video path: ${finalState.result}")
                        Log.d(TAG, "  Work ID: $workId")
                        Result.success(output)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving data to SharedPreferences", e)
                        Result.failure(
                            workDataOf(KEY_ERROR_MESSAGE to "Ошибка сохранения результатов: ${e.message}")
                        )
                    }
                }
                is ProcessingState.Error -> {
                    Log.e(TAG, "=== FAILURE: ProcessingState.Error ===")
                    Log.e(TAG, "Error message: ${finalState.message}")
                    Result.failure(workDataOf(KEY_ERROR_MESSAGE to finalState.message))
                }
                else -> {
                    Log.e(TAG, "=== FAILURE: Unexpected final state ===")
                    Log.e(TAG, "State: $finalState")
                    Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Unexpected final state: $finalState"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== FAILURE: Exception in edit worker ===", e)
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (e.message ?: "Unknown error in edit worker")))
        }
    }
}