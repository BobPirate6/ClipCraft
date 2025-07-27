package com.example.clipcraft.domain.repository

import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
import com.example.clipcraft.models.EditPlan
import com.example.clipcraft.models.VideoAnalysis
import com.example.clipcraft.workers.EditWorker
import com.example.clipcraft.workers.VideoProcessingWorker
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.example.clipcraft.services.VideoEditorService
import android.net.Uri

@Singleton
class VideoProcessingRepository @Inject constructor(
    private val workManager: WorkManager,
    private val gson: Gson,
    private val sharedPreferences: SharedPreferences,
    private val videoEditorService: VideoEditorService
) {
    companion object {
        private const val TAG = "VideoProcessingRepo"
        private const val KEY_CURRENT_WORK_ID = "current_work_id"
        private const val TAG_VIDEO_PROCESSING = "video_processing"
    }

    fun processNewVideo(videoUris: Array<String>, userCommand: String): UUID {
        Log.d(TAG, "processNewVideo: ${videoUris.size} videos, command: $userCommand")

        val workData = workDataOf(
            VideoProcessingWorker.KEY_VIDEO_URIS to videoUris,
            VideoProcessingWorker.KEY_USER_COMMAND to userCommand
        )

        // Добавляем ограничения для предотвращения остановки worker'а
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<VideoProcessingWorker>()
            .setInputData(workData)
            .addTag(TAG_VIDEO_PROCESSING)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) // Пытаемся запустить как срочную работу
            .build()

        workManager.enqueue(workRequest)
        saveCurrentWorkId(workRequest.id)

        Log.d(TAG, "Work enqueued with id: ${workRequest.id}")
        return workRequest.id
    }

    fun processEditedVideo(
        videoUris: Array<String>,
        originalCommand: String,
        editCommand: String,
        previousPlan: EditPlan,
        originalVideoAnalyses: Map<String, VideoAnalysis>
    ): UUID {
        Log.d(TAG, "processEditedVideo: ${videoUris.size} videos")
        Log.d(TAG, "  originalCommand: $originalCommand")
        Log.d(TAG, "  editCommand: $editCommand")
        Log.d(TAG, "  previousPlan segments: ${previousPlan.finalEdit.size}")
        Log.d(TAG, "  originalVideoAnalyses size: ${originalVideoAnalyses.size}")
        
        // Генерируем уникальный ID для этой задачи
        val workId = UUID.randomUUID()
        
        // Сохраняем большие данные в SharedPreferences
        val editDataKey = "edit_work_data_$workId"
        val editData = mapOf(
            "videoUris" to videoUris.toList(),
            "originalCommand" to originalCommand,
            "editCommand" to editCommand,
            "previousPlan" to previousPlan,
            "originalVideoAnalyses" to originalVideoAnalyses
        )
        
        val editDataJson = gson.toJson(editData)
        Log.d(TAG, "Saving edit data to SharedPreferences, size: ${editDataJson.length} chars")
        
        sharedPreferences.edit()
            .putString(editDataKey, editDataJson)
            .apply()
        
        // Передаем только ID в WorkManager
        val workData = workDataOf(
            "WORK_DATA_KEY" to editDataKey
        )
        
        Log.d(TAG, "WorkData created with key: $editDataKey")

        // Добавляем ограничения
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<EditWorker>()
            .setId(workId)  // Устанавливаем наш сгенерированный ID
            .setInputData(workData)
            .addTag(TAG_VIDEO_PROCESSING)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueue(workRequest)
        saveCurrentWorkId(workRequest.id)

        Log.d(TAG, "Edit work enqueued with id: ${workRequest.id}")
        return workRequest.id
    }

    fun getWorkInfoById(workId: UUID): Flow<WorkInfo?> {
        Log.d(TAG, "Getting work info for: $workId")
        return workManager.getWorkInfoByIdFlow(workId)
    }

    fun cancelAllWork() {
        Log.d(TAG, "Cancelling all work")
        workManager.cancelAllWorkByTag(TAG_VIDEO_PROCESSING)
        clearCurrentWorkId()
    }

    fun getSavedWorkId(): UUID? {
        val idString = sharedPreferences.getString(KEY_CURRENT_WORK_ID, null)
        return idString?.let {
            Log.d(TAG, "Found saved work id: $it")
            UUID.fromString(it)
        }
    }

    private fun saveCurrentWorkId(workId: UUID) {
        Log.d(TAG, "Saving work id: $workId")
        sharedPreferences.edit().putString(KEY_CURRENT_WORK_ID, workId.toString()).apply()
    }

    fun clearCurrentWorkId() {
        Log.d(TAG, "Clearing current work id")
        sharedPreferences.edit().remove(KEY_CURRENT_WORK_ID).apply()
    }
    
    /**
     * Сохраняет видео из временного файла в галерею
     * @param tempVideoPath путь к временному видео файлу
     * @return Uri сохраненного видео в MediaStore
     */
    suspend fun saveVideoToGallery(tempVideoPath: String): Uri {
        Log.d(TAG, "Saving video to gallery from: $tempVideoPath")
        return videoEditorService.saveVideoToGallery(tempVideoPath)
    }
}