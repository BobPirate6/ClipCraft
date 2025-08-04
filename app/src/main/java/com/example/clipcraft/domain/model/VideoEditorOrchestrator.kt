package com.example.clipcraft.domain.model

import android.util.Log
import com.example.clipcraft.models.EditPlan
import com.example.clipcraft.models.SelectedVideo
import com.example.clipcraft.models.VideoAnalysis
import com.example.clipcraft.utils.TemporaryFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User actions that can be performed in the video editor
 */
sealed class UserAction {
    data class CreateWithAI(
        val command: String,
        val selectedVideos: List<SelectedVideo>
    ) : UserAction()
    
    data class EditWithAI(
        val command: String
    ) : UserAction()
    
    data class EditManually(
        val changes: List<TimelineChange>
    ) : UserAction()
    
    object Undo : UserAction()
    object Redo : UserAction()
    object Reset : UserAction()
}

/**
 * Result of AI processing
 */
data class AIEditResult(
    val command: String,
    val videoPath: String,
    val editPlan: EditPlan,
    val videoAnalyses: Map<String, VideoAnalysis>
)

/**
 * Orchestrates video editing sessions and state transitions
 */
@Singleton
class VideoEditorOrchestrator @Inject constructor(
    private val transitionManager: VideoStateTransitionManager,
    private val temporaryFileManager: TemporaryFileManager
) {
    companion object {
        private const val TAG = "VideoEditorOrchestrator"
    }
    
    private val sessionMutex = Mutex()
    private val _currentSession = MutableStateFlow<VideoEditingSession?>(null)
    val currentSession: StateFlow<VideoEditingSession?> = _currentSession.asStateFlow()
    
    /**
     * Initialize a new editing session
     */
    suspend fun initializeSession(
        selectedVideos: List<SelectedVideo>? = null,
        existingEditPlan: EditPlan? = null,
        videoAnalyses: Map<String, VideoAnalysis>? = null,
        currentVideoPath: String? = null,
        aiCommand: String = ""
    ) {
        sessionMutex.withLock {
            Log.d(TAG, "Initializing new session")
            
            val initialState = when {
                // Case 1: Starting fresh with selected videos
                selectedVideos != null && existingEditPlan == null -> {
                    VideoState.Initial(selectedVideos)
                }
                
                // Case 2: Resuming with existing AI-processed video
                existingEditPlan != null && videoAnalyses != null && currentVideoPath != null -> {
                    VideoState.AIProcessed(
                        videoPath = currentVideoPath,
                        editPlan = existingEditPlan,
                        aiCommand = aiCommand,
                        videoAnalyses = videoAnalyses,
                        sessionId = _currentSession.value?.sessionId ?: java.util.UUID.randomUUID().toString()
                    )
                }
                
                // Case 3: Invalid state
                else -> {
                    Log.e(TAG, "Invalid initialization parameters")
                    return
                }
            }
            
            _currentSession.value = VideoEditingSession(
                currentState = initialState,
                sessionId = initialState.sessionId
            )
            
            Log.d(TAG, "Session initialized with state: ${initialState::class.simpleName}")
        }
    }
    
    /**
     * Process a user action
     */
    suspend fun processUserAction(action: UserAction): Result<VideoEditingSession> {
        return sessionMutex.withLock {
            val session = _currentSession.value ?: return Result.failure(
                IllegalStateException("No active session")
            )
            
            Log.d(TAG, "Processing action: ${action::class.simpleName}")
            
            when (action) {
                is UserAction.CreateWithAI -> handleCreateWithAI(session, action)
                is UserAction.EditWithAI -> handleEditWithAI(session, action)
                is UserAction.EditManually -> handleEditManually(session, action)
                UserAction.Undo -> handleUndo(session)
                UserAction.Redo -> handleRedo(session)
                UserAction.Reset -> handleReset(session)
            }.also { result ->
                result.onSuccess { newSession ->
                    _currentSession.value = newSession
                    Log.d(TAG, "Session updated successfully")
                }.onFailure { error ->
                    Log.e(TAG, "Action failed", error)
                }
            }
        }
    }
    
    /**
     * Handle AI edit result
     */
    suspend fun onAIEditComplete(result: AIEditResult): Result<VideoEditingSession> {
        return sessionMutex.withLock {
            val session = _currentSession.value ?: return Result.failure(
                IllegalStateException("No active session")
            )
            
            Log.d(TAG, "Processing AI edit result for command: ${result.command}")
            
            val operation = EditOperation.AIProcess(
                command = result.command,
                timestamp = System.currentTimeMillis(),
                resultPath = result.videoPath,
                editPlan = result.editPlan,
                videoAnalyses = result.videoAnalyses
            )
            
            // Register the new video file for tracking
            temporaryFileManager.registerTemporaryFile(result.videoPath)
            
            val transitionResult = transitionManager.transition(session.currentState, operation)
            
            transitionResult.fold(
                onSuccess = { newState ->
                    val newSession = session.copy(
                        currentState = newState,
                        history = session.history + operation
                    ).pushToUndoStack(session.currentState)
                    
                    _currentSession.value = newSession
                    Log.d(TAG, "AI edit completed successfully, new state: ${newState::class.simpleName}")
                    Result.success(newSession)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to apply AI edit", error)
                    Result.failure(error)
                }
            )
        }
    }
    
    private fun handleCreateWithAI(
        session: VideoEditingSession,
        action: UserAction.CreateWithAI
    ): Result<VideoEditingSession> {
        // This action will trigger AI processing
        // The actual result will come through onAIEditComplete
        Log.d(TAG, "Create with AI requested: ${action.command}")
        return Result.success(session)
    }
    
    private fun handleEditWithAI(
        session: VideoEditingSession,
        action: UserAction.EditWithAI
    ): Result<VideoEditingSession> {
        if (!session.currentState.canEditWithAI) {
            return Result.failure(IllegalStateException("Cannot edit with AI in current state"))
        }
        
        // This action will trigger AI processing
        // The actual result will come through onAIEditComplete
        Log.d(TAG, "Edit with AI requested: ${action.command}")
        return Result.success(session)
    }
    
    private fun handleEditManually(
        session: VideoEditingSession,
        action: UserAction.EditManually
    ): Result<VideoEditingSession> {
        if (!session.currentState.canEditManually) {
            return Result.failure(IllegalStateException("Cannot edit manually in current state"))
        }
        
        val currentPath = session.currentState.videoPath ?: return Result.failure(
            IllegalStateException("No video to edit")
        )
        
        // Generate new path for edited video
        val newPath = generateEditedVideoPath(session.sessionId)
        
        val operation = EditOperation.ManualEdit(
            changes = action.changes,
            timestamp = System.currentTimeMillis(),
            resultPath = newPath,
            editPlan = session.currentState.editPlan // Will be updated by editor
        )
        
        temporaryFileManager.registerTemporaryFile(newPath)
        
        return transitionManager.transition(session.currentState, operation).fold(
            onSuccess = { newState ->
                Result.success(
                    session.copy(
                        currentState = newState,
                        history = session.history + operation
                    ).pushToUndoStack(session.currentState)
                )
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    private fun handleUndo(session: VideoEditingSession): Result<VideoEditingSession> {
        if (session.undoStack.isEmpty()) {
            return Result.failure(IllegalStateException("Nothing to undo"))
        }
        
        val previousState = session.undoStack.last()
        val newUndoStack = session.undoStack.dropLast(1)
        val newRedoStack = session.redoStack + session.currentState
        
        return Result.success(
            session.copy(
                currentState = previousState,
                undoStack = newUndoStack,
                redoStack = newRedoStack
            )
        )
    }
    
    private fun handleRedo(session: VideoEditingSession): Result<VideoEditingSession> {
        if (session.redoStack.isEmpty()) {
            return Result.failure(IllegalStateException("Nothing to redo"))
        }
        
        val nextState = session.redoStack.last()
        val newRedoStack = session.redoStack.dropLast(1)
        val newUndoStack = session.undoStack + session.currentState
        
        return Result.success(
            session.copy(
                currentState = nextState,
                undoStack = newUndoStack,
                redoStack = newRedoStack
            )
        )
    }
    
    private fun handleReset(session: VideoEditingSession): Result<VideoEditingSession> {
        // Find the initial state in the history
        // Find the initial state in the history
        val initialState = when (val state = session.currentState) {
            is VideoState.Initial -> state
            is VideoState.AIProcessed -> state.previousState as? VideoState.Initial ?: VideoState.Initial(emptyList())
            is VideoState.ManuallyEdited -> state.previousState as? VideoState.Initial ?: VideoState.Initial(emptyList())
            is VideoState.Combined -> state.previousState as? VideoState.Initial ?: VideoState.Initial(emptyList())
        }
        
        return Result.success(
            session.copy(
                currentState = initialState,
                undoStack = emptyList(),
                redoStack = emptyList()
            )
        )
    }
    
    private fun generateEditedVideoPath(sessionId: String): String {
        val timestamp = System.currentTimeMillis()
        return "${temporaryFileManager.getVideoEditorTempDir()}/edited_${sessionId}_$timestamp.mp4"
    }
    
    /**
     * Get current video path
     */
    fun getCurrentVideoPath(): String? {
        return _currentSession.value?.currentState?.videoPath
    }
    
    /**
     * Get current edit plan
     */
    fun getCurrentEditPlan(): EditPlan? {
        return _currentSession.value?.currentState?.editPlan
    }
    
    /**
     * Clean up session
     */
    suspend fun clearSession() {
        sessionMutex.withLock {
            Log.d(TAG, "Clearing session")
            _currentSession.value = null
        }
    }
}