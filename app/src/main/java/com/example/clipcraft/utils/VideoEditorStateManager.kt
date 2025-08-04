package com.example.clipcraft.utils

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton для управления состоянием между MainViewModel и VideoEditorViewModel
 */
@Singleton
class VideoEditorStateManager @Inject constructor() {
    companion object {
        private const val TAG = "VideoEditorStateManager"
    }
    
    private var shouldClearState = false
    
    /**
     * Отметить, что состояние редактора нужно очистить при следующем открытии
     */
    fun markForClear() {
        Log.d(TAG, "Marking editor state for clear")
        shouldClearState = true
    }
    
    /**
     * Проверить, нужно ли очистить состояние редактора
     */
    fun shouldClearEditorState(): Boolean {
        val should = shouldClearState
        if (should) {
            Log.d(TAG, "Editor state should be cleared")
            shouldClearState = false // Сбрасываем флаг после проверки
        }
        return should
    }
    
    /**
     * Сбросить флаг очистки (например, если редактор был открыт)
     */
    fun resetClearFlag() {
        shouldClearState = false
    }
}