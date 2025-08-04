package com.example.clipcraft.utils

import com.example.clipcraft.models.EditPlan
import com.example.clipcraft.models.VideoAnalysis
import com.example.clipcraft.models.SelectedVideo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер для обновления состояния видеоредактора после внешних изменений
 */
@Singleton
class VideoEditorUpdateManager @Inject constructor() {
    
    data class EditorUpdate(
        val editPlan: EditPlan,
        val videoAnalyses: Map<String, VideoAnalysis>,
        val selectedVideos: List<SelectedVideo>,
        val resultPath: String
    )
    
    private var pendingUpdate: EditorUpdate? = null
    
    /**
     * Установить обновление для редактора
     */
    fun setPendingUpdate(update: EditorUpdate) {
        pendingUpdate = update
    }
    
    /**
     * Получить и очистить обновление
     */
    fun getPendingUpdate(): EditorUpdate? {
        val update = pendingUpdate
        pendingUpdate = null
        return update
    }
    
    /**
     * Проверить наличие обновления
     */
    fun hasPendingUpdate(): Boolean = pendingUpdate != null
    
    /**
     * Очистить обновление
     */
    fun clearPendingUpdate() {
        pendingUpdate = null
    }
}