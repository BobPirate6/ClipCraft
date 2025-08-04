package com.example.clipcraft.domain.model

import android.util.Log
import com.example.clipcraft.models.EditPlan
import com.example.clipcraft.models.VideoAnalysis
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages transitions between video states
 */
@Singleton
class VideoStateTransitionManager @Inject constructor() {
    
    companion object {
        private const val TAG = "VideoStateTransition"
    }
    
    /**
     * Transition from one state to another based on the operation
     */
    fun transition(
        currentState: VideoState,
        operation: EditOperation
    ): Result<VideoState> {
        Log.d(TAG, "Transitioning from ${currentState::class.simpleName} with ${operation::class.simpleName}")
        
        return try {
            when (currentState) {
                is VideoState.Initial -> handleInitialState(currentState, operation)
                is VideoState.AIProcessed -> handleAIProcessedState(currentState, operation)
                is VideoState.ManuallyEdited -> handleManuallyEditedState(currentState, operation)
                is VideoState.Combined -> handleCombinedState(currentState, operation)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transition failed", e)
            Result.failure(e)
        }
    }
    
    private fun handleInitialState(
        state: VideoState.Initial,
        operation: EditOperation
    ): Result<VideoState> {
        return when (operation) {
            is EditOperation.AIProcess -> {
                Log.d(TAG, "Initial -> AIProcessed")
                Result.success(
                    VideoState.AIProcessed(
                        videoPath = operation.resultPath,
                        editPlan = operation.editPlan,
                        aiCommand = operation.command,
                        videoAnalyses = operation.videoAnalyses,
                        sessionId = state.sessionId,
                        previousState = state
                    )
                )
            }
            is EditOperation.ManualEdit -> {
                Log.d(TAG, "Initial -> ManuallyEdited")
                Result.success(
                    VideoState.ManuallyEdited(
                        videoPath = operation.resultPath,
                        editPlan = operation.editPlan,
                        changes = operation.changes,
                        sessionId = state.sessionId,
                        previousState = state
                    )
                )
            }
        }
    }
    
    private fun handleAIProcessedState(
        state: VideoState.AIProcessed,
        operation: EditOperation
    ): Result<VideoState> {
        return when (operation) {
            is EditOperation.AIProcess -> {
                Log.d(TAG, "AIProcessed -> AIProcessed (re-process)")
                Result.success(
                    VideoState.AIProcessed(
                        videoPath = operation.resultPath,
                        editPlan = operation.editPlan,
                        aiCommand = operation.command,
                        videoAnalyses = operation.videoAnalyses,
                        sessionId = state.sessionId,
                        previousState = state
                    )
                )
            }
            is EditOperation.ManualEdit -> {
                Log.d(TAG, "AIProcessed -> Combined")
                val history = listOf(
                    EditOperation.AIProcess(
                        command = state.aiCommand,
                        timestamp = System.currentTimeMillis() - 1000,
                        resultPath = state.videoPath,
                        editPlan = state.editPlan,
                        videoAnalyses = state.videoAnalyses
                    ),
                    operation
                )
                Result.success(
                    VideoState.Combined(
                        videoPath = operation.resultPath,
                        editPlan = operation.editPlan,
                        history = history,
                        sessionId = state.sessionId,
                        previousState = state
                    )
                )
            }
        }
    }
    
    private fun handleManuallyEditedState(
        state: VideoState.ManuallyEdited,
        operation: EditOperation
    ): Result<VideoState> {
        return when (operation) {
            is EditOperation.AIProcess -> {
                Log.d(TAG, "ManuallyEdited -> Combined")
                val history = listOf(
                    EditOperation.ManualEdit(
                        changes = state.changes,
                        timestamp = System.currentTimeMillis() - 1000,
                        resultPath = state.videoPath,
                        editPlan = state.editPlan
                    ),
                    operation
                )
                Result.success(
                    VideoState.Combined(
                        videoPath = operation.resultPath,
                        editPlan = operation.editPlan,
                        history = history,
                        sessionId = state.sessionId,
                        previousState = state
                    )
                )
            }
            is EditOperation.ManualEdit -> {
                Log.d(TAG, "ManuallyEdited -> ManuallyEdited (continue editing)")
                Result.success(
                    VideoState.ManuallyEdited(
                        videoPath = operation.resultPath,
                        editPlan = operation.editPlan,
                        changes = state.changes + operation.changes,
                        sessionId = state.sessionId,
                        previousState = state
                    )
                )
            }
        }
    }
    
    private fun handleCombinedState(
        state: VideoState.Combined,
        operation: EditOperation
    ): Result<VideoState> {
        Log.d(TAG, "Combined -> Combined (adding to history)")
        val newEditPlan = when (operation) {
            is EditOperation.AIProcess -> operation.editPlan
            is EditOperation.ManualEdit -> operation.editPlan
        }
        return Result.success(
            VideoState.Combined(
                videoPath = operation.resultPath,
                editPlan = newEditPlan,
                history = state.history + operation,
                sessionId = state.sessionId,
                previousState = state
            )
        )
    }
    
    /**
     * Validate if a transition is allowed
     */
    fun canTransition(currentState: VideoState, operation: EditOperation): Boolean {
        return when (operation) {
            is EditOperation.AIProcess -> currentState.canEditWithAI
            is EditOperation.ManualEdit -> currentState.canEditManually
        }
    }
    
    /**
     * Get the video path from the current state
     */
    fun getCurrentVideoPath(state: VideoState): String? {
        return state.videoPath
    }
    
    /**
     * Check if state has been modified from initial
     */
    fun isModified(state: VideoState): Boolean {
        return state !is VideoState.Initial
    }
    
    /**
     * Get edit history from state
     */
    fun getEditHistory(state: VideoState): List<EditOperation> {
        return when (state) {
            is VideoState.Initial -> emptyList()
            is VideoState.AIProcessed -> {
                listOfNotNull(state.previousState?.let { getEditHistory(it) })
                    .flatten() + EditOperation.AIProcess(
                    command = state.aiCommand,
                    timestamp = System.currentTimeMillis(),
                    resultPath = state.videoPath,
                    editPlan = state.editPlan,
                    videoAnalyses = state.videoAnalyses
                )
            }
            is VideoState.ManuallyEdited -> {
                listOfNotNull(state.previousState?.let { getEditHistory(it) })
                    .flatten() + EditOperation.ManualEdit(
                    changes = state.changes,
                    timestamp = System.currentTimeMillis(),
                    resultPath = state.videoPath,
                    editPlan = state.editPlan
                )
            }
            is VideoState.Combined -> state.history
        }
    }
}