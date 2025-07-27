package com.example.clipcraft.models

import android.net.Uri
import java.util.UUID

/**
 * Представляет отдельный сегмент видео на таймлайне
 * Это прокси-объект, который ссылается на часть исходного видео
 */
data class VideoSegment(
    val id: String = UUID.randomUUID().toString(),
    val sourceVideoUri: Uri,          // URI исходного видео
    val sourceVideoPath: String,      // Путь к исходному видео
    val sourceFileName: String,       // Имя файла для EditPlan
    val originalDuration: Float,      // Полная длительность исходного видео
    val inPoint: Float,              // Точка входа в исходное видео (in point)
    val outPoint: Float,             // Точка выхода из исходного видео (out point)
    val timelinePosition: Float,      // Позиция на таймлайне (для визуализации)
    val thumbnails: List<Uri> = emptyList(), // Превью кадры
    val isSelected: Boolean = false,
    val isDragging: Boolean = false   // Флаг перетаскивания
) {
    // Длительность сегмента на таймлайне
    val duration: Float get() = outPoint - inPoint
    
    // Для совместимости со старым кодом
    val startTime: Float get() = inPoint
    val endTime: Float get() = outPoint
    val originalStartTime: Float get() = 0f
    val originalEndTime: Float get() = originalDuration
}

/**
 * Состояние таймлайна редактора
 */
data class TimelineState(
    val segments: List<VideoSegment> = emptyList(),
    val totalDuration: Float = 0f,
    val currentPosition: Float = 0f, // Позиция playhead
    val zoomLevel: Float = 1f,      // Уровень зума (1f = 100%)
    val scrollOffset: Float = 0f,    // Горизонтальный скролл
    val selectedSegmentId: String? = null,
    val isDragging: Boolean = false,
    val draggedSegmentId: String? = null,
    val isPlaying: Boolean = false,
    val editingSegmentId: String? = null, // ID сегмента, который редактируется
    val isEditingStart: Boolean = false,  // Редактируется начало сегмента
    val isEditingEnd: Boolean = false     // Редактируется конец сегмента
)

/**
 * Действия редактирования для системы undo/redo
 */
sealed class EditAction {
    data class AddSegment(
        val segment: VideoSegment,
        val position: Int
    ) : EditAction()
    
    data class RemoveSegment(
        val segment: VideoSegment,
        val position: Int
    ) : EditAction()
    
    data class TrimSegment(
        val segmentId: String,
        val oldStartTime: Float,
        val oldEndTime: Float,
        val newStartTime: Float,
        val newEndTime: Float
    ) : EditAction()
    
    data class MoveSegment(
        val segmentId: String,
        val fromPosition: Int,
        val toPosition: Int
    ) : EditAction()
    
    data object ResetToOriginal : EditAction()
}

/**
 * Состояние истории редактирования для undo/redo
 */
data class EditHistoryState(
    val actions: List<EditAction> = emptyList(),
    val currentIndex: Int = -1,
    val maxHistorySize: Int = 10
) {
    val canUndo: Boolean = currentIndex >= 0
    val canRedo: Boolean = currentIndex < actions.size - 1
}

/**
 * Результат операции редактирования
 */
sealed class EditResult {
    data object Success : EditResult()
    data class Error(val message: String) : EditResult()
}

/**
 * Конфигурация редактора
 */
data class VideoEditorConfig(
    val minSegmentDuration: Float = 0.5f,         // Минимальная длина сегмента в секундах
    val timelineHeight: Int = 80,                 // Высота таймлайна в dp
    val thumbnailWidth: Int = 60,                 // Ширина одного превью в dp
    val minZoomLevel: Float = 0.5f,               // Минимальный зум (50%)
    val maxZoomLevel: Float = 5f,                 // Максимальный зум (500%)
    val playheadWidth: Int = 2,                   // Ширина индикатора позиции в dp
    val handleWidth: Int = 24,                    // Ширина манипуляторов обрезки в dp
    val segmentSpacing: Int = 2                   // Расстояние между сегментами в dp
)

/**
 * Состояние видеоредактора
 */
data class VideoEditorState(
    val timelineState: TimelineState = TimelineState(),
    val editHistory: EditHistoryState = EditHistoryState(),
    val originalPlan: EditPlan? = null,           // Исходный план монтажа
    val tempVideoPath: String? = null,            // Путь к временному видео
    val isProcessing: Boolean = false,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false
)

/**
 * Позиция для вставки нового сегмента
 */
data class InsertPosition(
    val index: Int,
    val side: InsertSide
)

enum class InsertSide {
    BEFORE,
    AFTER
}