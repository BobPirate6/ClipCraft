package com.example.clipcraft.domain.model

import com.example.clipcraft.models.EditPlan
import com.example.clipcraft.models.EditSegment
import com.example.clipcraft.models.SelectedVideo
import com.example.clipcraft.models.VideoAnalysis
import java.util.UUID

/**
 * Represents the current state of video in the editor
 */
sealed class VideoState {
    abstract val videoPath: String?
    abstract val editPlan: EditPlan
    abstract val sourceType: SourceType
    abstract val canEditManually: Boolean
    abstract val canEditWithAI: Boolean
    abstract val sessionId: String
    
    /**
     * Initial state - only selected videos, no processing yet
     */
    data class Initial(
        val selectedVideos: List<SelectedVideo>,
        override val sessionId: String = UUID.randomUUID().toString()
    ) : VideoState() {
        override val videoPath: String? = null
        override val editPlan = EditPlan(
            finalEdit = emptyList()
        )
        override val sourceType = SourceType.RAW_VIDEOS
        override val canEditManually = true
        override val canEditWithAI = true
    }
    
    /**
     * After AI processing
     */
    data class AIProcessed(
        override val videoPath: String,
        override val editPlan: EditPlan,
        val aiCommand: String,
        val videoAnalyses: Map<String, VideoAnalysis>,
        override val sessionId: String,
        val previousState: VideoState? = null
    ) : VideoState() {
        override val sourceType = SourceType.AI_GENERATED
        override val canEditManually = true
        override val canEditWithAI = true
    }
    
    /**
     * After manual editing
     */
    data class ManuallyEdited(
        override val videoPath: String,
        override val editPlan: EditPlan,
        val changes: List<TimelineChange>,
        override val sessionId: String,
        val previousState: VideoState? = null
    ) : VideoState() {
        override val sourceType = SourceType.MANUALLY_EDITED
        override val canEditManually = true
        override val canEditWithAI = true
    }
    
    /**
     * Combined state (AI + Manual operations)
     */
    data class Combined(
        override val videoPath: String,
        override val editPlan: EditPlan,
        val history: List<EditOperation>,
        override val sessionId: String,
        val previousState: VideoState? = null
    ) : VideoState() {
        override val sourceType = SourceType.COMBINED
        override val canEditManually = true
        override val canEditWithAI = true
    }
}

/**
 * Source type of the video
 */
enum class SourceType {
    RAW_VIDEOS,      // Original videos
    AI_GENERATED,    // Created by AI
    MANUALLY_EDITED, // Edited manually
    COMBINED         // Combination of AI + manual
}

/**
 * Represents a change made to the timeline
 */
sealed class TimelineChange {
    abstract val timestamp: Long
    
    data class SegmentTrimmed(
        val segmentId: String,
        val newStartTime: Float,
        val newEndTime: Float,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TimelineChange()
    
    data class SegmentReordered(
        val fromIndex: Int,
        val toIndex: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TimelineChange()
    
    data class SegmentDeleted(
        val segmentId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TimelineChange()
    
    data class SegmentAdded(
        val segment: EditSegment,
        val position: Int,
        override val timestamp: Long = System.currentTimeMillis()
    ) : TimelineChange()
}

/**
 * Represents an edit operation
 */
sealed class EditOperation {
    abstract val timestamp: Long
    abstract val resultPath: String
    
    data class AIProcess(
        val command: String,
        override val timestamp: Long,
        override val resultPath: String,
        val editPlan: EditPlan,
        val videoAnalyses: Map<String, VideoAnalysis>
    ) : EditOperation()
    
    data class ManualEdit(
        val changes: List<TimelineChange>,
        override val timestamp: Long,
        override val resultPath: String,
        val editPlan: EditPlan
    ) : EditOperation()
}

/**
 * Video editing session
 */
data class VideoEditingSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val currentState: VideoState,
    val history: List<EditOperation> = emptyList(),
    val undoStack: List<VideoState> = emptyList(),
    val redoStack: List<VideoState> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    fun addToHistory(operation: EditOperation): VideoEditingSession {
        return copy(
            history = history + operation,
            lastModified = System.currentTimeMillis()
        )
    }
    
    fun pushToUndoStack(state: VideoState): VideoEditingSession {
        return copy(
            undoStack = undoStack + state,
            redoStack = emptyList(), // Clear redo stack on new action
            lastModified = System.currentTimeMillis()
        )
    }
}