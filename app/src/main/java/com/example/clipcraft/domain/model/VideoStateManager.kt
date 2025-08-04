package com.example.clipcraft.domain.model

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.clipcraft.models.EditPlan
import com.example.clipcraft.models.VideoSegment
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Video state definitions following the architecture:
 * Initial -> 1a (AI) or 2a (Manual) -> editing states -> final states
 */
sealed class VideoEditState {
    abstract val id: String
    abstract val sessionId: String
    abstract val timestamp: Long
    abstract val parentId: String?
    
    data class Initial(
        override val id: String = UUID.randomUUID().toString(),
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val parentId: String? = null,
        val selectedVideos: List<Uri>
    ) : VideoEditState()
    
    data class Stage1A(
        override val id: String = UUID.randomUUID().toString(),
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val parentId: String?,
        val aiGeneratedVideoPath: String,
        val editPlan: EditPlan,
        val sourceVideos: List<Uri>
    ) : VideoEditState()
    
    data class Stage1AEditInput(
        override val id: String = UUID.randomUUID().toString(),
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val parentId: String?,
        val currentEditPlan: EditPlan,
        val segments: List<VideoSegment>,
        val sourceVideos: List<Uri>,
        val baseVideoPath: String? = null
    ) : VideoEditState()
    
    data class Stage1A1Final(
        override val id: String = UUID.randomUUID().toString(),
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val parentId: String?,
        val renderedVideoPath: String,
        val finalEditPlan: EditPlan,
        val segments: List<VideoSegment>,
        val sourceVideos: List<Uri>
    ) : VideoEditState()
    
    data class Stage2AEditInput(
        override val id: String = UUID.randomUUID().toString(),
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val parentId: String?,
        val segments: List<VideoSegment>,
        val sourceVideos: List<Uri>
    ) : VideoEditState()
    
    data class Stage2A1Final(
        override val id: String = UUID.randomUUID().toString(),
        override val sessionId: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val parentId: String?,
        val renderedVideoPath: String,
        val segments: List<VideoSegment>,
        val sourceVideos: List<Uri>
    ) : VideoEditState()
}

/**
 * Manages video editing states and transitions
 */
@Singleton
class VideoStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "VideoStateManager"
        private const val CACHE_DIR = "clipcraft"
        private const val SESSION_TIMEOUT = 24 * 60 * 60 * 1000L // 24 hours
        private const val PREFS_NAME = "video_state_prefs"
        private const val KEY_CURRENT_SESSION = "current_session_id"
        private const val KEY_CURRENT_STATE = "current_state_id"
    }
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _currentState = MutableStateFlow<VideoEditState?>(null)
    val currentState: StateFlow<VideoEditState?> = _currentState.asStateFlow()
    
    private val _stateHistory = MutableStateFlow<List<VideoEditState>>(emptyList())
    val stateHistory: StateFlow<List<VideoEditState>> = _stateHistory.asStateFlow()
    
    private var currentSessionId: String? = null
    
    init {
        cleanupOldSessions()
        restoreSession()
    }
    
    /**
     * Start a new editing session
     */
    fun startNewSession(selectedVideos: List<Uri>) {
        Log.d(TAG, "Starting new session with ${selectedVideos.size} videos")
        Log.d("clipcraftlogic", "=== VideoStateManager.startNewSession ===")
        Log.d("clipcraftlogic", "Previous session ID: $currentSessionId")
        Log.d("clipcraftlogic", "Previous state: ${_currentState.value?.javaClass?.simpleName}")
        Log.d("clipcraftlogic", "Videos to add: ${selectedVideos.joinToString { it.lastPathSegment ?: "unknown" }}")
        
        // Clean up previous session if exists
        currentSessionId?.let { 
            Log.d("clipcraftlogic", "Cleaning up previous session: $it")
            cleanupSession(it) 
        }
        
        // Create new session
        currentSessionId = UUID.randomUUID().toString()
        Log.d("clipcraftlogic", "New session ID created: $currentSessionId")
        
        val initialState = VideoEditState.Initial(
            sessionId = currentSessionId!!,
            selectedVideos = selectedVideos
        )
        
        _currentState.value = initialState
        _stateHistory.value = listOf(initialState)
        
        Log.d("clipcraftlogic", "Initial state created and set")
        saveSession()
        Log.d("clipcraftlogic", "Session saved to preferences")
        Log.d("clipcraftlogic", "=== startNewSession completed ===")
    }
    
    /**
     * Transition to AI-created video state (1a)
     */
    fun transitionToAICreated(
        videoPath: String,
        editPlan: EditPlan,
        sourceVideos: List<Uri>
    ) {
        val currentState = _currentState.value ?: return
        
        val newState = VideoEditState.Stage1A(
            sessionId = currentSessionId!!,
            parentId = currentState.id,
            aiGeneratedVideoPath = videoPath,
            editPlan = editPlan,
            sourceVideos = sourceVideos
        )
        
        updateState(newState)
        saveStateToFile(newState)
    }
    
    /**
     * Transition to editor input state
     */
    fun transitionToEditorInput(editPlan: EditPlan?, segments: List<VideoSegment>) {
        val current = _currentState.value ?: return
        
        val newState = when (current) {
            is VideoEditState.Stage1A -> {
                VideoEditState.Stage1AEditInput(
                    sessionId = currentSessionId!!,
                    parentId = current.id,
                    currentEditPlan = editPlan ?: current.editPlan,
                    segments = segments,
                    sourceVideos = current.sourceVideos,
                    baseVideoPath = current.aiGeneratedVideoPath
                )
            }
            is VideoEditState.Initial -> {
                VideoEditState.Stage2AEditInput(
                    sessionId = currentSessionId!!,
                    parentId = current.id,
                    segments = segments,
                    sourceVideos = current.selectedVideos
                )
            }
            else -> return
        }
        
        updateState(newState)
        saveStateToFile(newState)
    }
    
    /**
     * Transition to final state after manual editing
     */
    fun transitionToFinalState(renderedVideoPath: String, segments: List<VideoSegment>) {
        val current = _currentState.value ?: return
        
        val newState = when (current) {
            is VideoEditState.Stage1AEditInput -> {
                VideoEditState.Stage1A1Final(
                    sessionId = currentSessionId!!,
                    parentId = current.id,
                    renderedVideoPath = renderedVideoPath,
                    finalEditPlan = current.currentEditPlan,
                    segments = segments,
                    sourceVideos = current.sourceVideos
                )
            }
            is VideoEditState.Stage2AEditInput -> {
                VideoEditState.Stage2A1Final(
                    sessionId = currentSessionId!!,
                    parentId = current.id,
                    renderedVideoPath = renderedVideoPath,
                    segments = segments,
                    sourceVideos = current.sourceVideos
                )
            }
            else -> return
        }
        
        updateState(newState)
        saveStateToFile(newState)
    }
    
    /**
     * Apply current state - saves and keeps current
     */
    fun applyCurrentState() {
        val current = _currentState.value ?: return
        saveStateToFile(current)
        saveSession()
    }
    
    /**
     * Exit to previous state
     */
    fun exitToPreviousState(): VideoEditState? {
        val current = _currentState.value ?: return null
        val parentId = current.parentId ?: return null
        
        // Find parent state in history
        val parentState = _stateHistory.value.find { it.id == parentId }
        if (parentState != null) {
            _currentState.value = parentState
            saveSession()
            return parentState
        }
        
        // Try to load from file if not in memory
        return loadStateFromFile(currentSessionId!!, parentId)?.also {
            _currentState.value = it
            saveSession()
        }
    }
    
    /**
     * Get rendered video path for current state
     */
    fun getCurrentVideoPath(): String? {
        return when (val state = _currentState.value) {
            is VideoEditState.Stage1A -> state.aiGeneratedVideoPath
            is VideoEditState.Stage1A1Final -> state.renderedVideoPath
            is VideoEditState.Stage2A1Final -> state.renderedVideoPath
            else -> null
        }
    }
    
    /**
     * Get segments for editor
     */
    fun getCurrentSegments(): List<VideoSegment>? {
        return when (val state = _currentState.value) {
            is VideoEditState.Stage1AEditInput -> state.segments
            is VideoEditState.Stage1A1Final -> state.segments
            is VideoEditState.Stage2AEditInput -> state.segments
            is VideoEditState.Stage2A1Final -> state.segments
            else -> null
        }
    }
    
    /**
     * Clear current session
     */
    fun clearSession() {
        Log.d("clipcraftlogic", "=== VideoStateManager.clearSession ===")
        Log.d("clipcraftlogic", "Current session to clear: $currentSessionId")
        Log.d("clipcraftlogic", "Current state before clear: ${_currentState.value?.javaClass?.simpleName}")
        
        currentSessionId?.let { 
            Log.d("clipcraftlogic", "Cleaning up session: $it")
            cleanupSession(it) 
        }
        
        _currentState.value = null
        _stateHistory.value = emptyList()
        currentSessionId = null
        
        prefs.edit().clear().apply()
        Log.d("clipcraftlogic", "Session cleared, preferences cleared")
        Log.d("clipcraftlogic", "=== clearSession completed ===")
    }
    
    private fun updateState(newState: VideoEditState) {
        _currentState.value = newState
        _stateHistory.value = _stateHistory.value + newState
        
        // Keep only last 10 states in memory for performance
        if (_stateHistory.value.size > 10) {
            _stateHistory.value = _stateHistory.value.takeLast(10)
        }
    }
    
    private fun saveSession() {
        val state = _currentState.value ?: return
        prefs.edit().apply {
            putString(KEY_CURRENT_SESSION, currentSessionId)
            putString(KEY_CURRENT_STATE, state.id)
            apply()
        }
    }
    
    private fun restoreSession() {
        val sessionId = prefs.getString(KEY_CURRENT_SESSION, null) ?: return
        val stateId = prefs.getString(KEY_CURRENT_STATE, null) ?: return
        
        currentSessionId = sessionId
        loadStateFromFile(sessionId, stateId)?.let {
            _currentState.value = it
            _stateHistory.value = listOf(it)
        }
    }
    
    private fun saveStateToFile(state: VideoEditState) {
        try {
            val sessionDir = File(context.cacheDir, "$CACHE_DIR/session_${state.sessionId}")
            val stateDir = File(sessionDir, "state_${state.id}")
            stateDir.mkdirs()
            
            val metadataFile = File(stateDir, "metadata.json")
            
            // Добавляем тип состояния для десериализации
            val stateMap = mutableMapOf<String, Any?>()
            stateMap["type"] = when (state) {
                is VideoEditState.Initial -> "Initial"
                is VideoEditState.Stage1A -> "Stage1A"
                is VideoEditState.Stage1AEditInput -> "Stage1AEditInput"
                is VideoEditState.Stage1A1Final -> "Stage1A1Final"
                is VideoEditState.Stage2AEditInput -> "Stage2AEditInput"
                is VideoEditState.Stage2A1Final -> "Stage2A1Final"
            }
            
            // Конвертируем состояние в карту для сохранения
            val json = gson.toJson(state)
            val map = gson.fromJson(json, Map::class.java) as MutableMap<String, Any?>
            map.putAll(stateMap)
            
            metadataFile.writeText(gson.toJson(map))
            
            Log.d(TAG, "Saved state ${state.id} to file")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state to file", e)
        }
    }
    
    private fun loadStateFromFile(sessionId: String, stateId: String): VideoEditState? {
        return try {
            val stateDir = File(context.cacheDir, "$CACHE_DIR/session_$sessionId/state_$stateId")
            val metadataFile = File(stateDir, "metadata.json")
            
            if (!metadataFile.exists()) return null
            
            val json = metadataFile.readText()
            // Parse based on type field in JSON
            val jsonObject = gson.fromJson(json, Map::class.java)
            val type = jsonObject["type"] as? String ?: return null
            
            when (type) {
                "Initial" -> gson.fromJson(json, VideoEditState.Initial::class.java)
                "Stage1A" -> gson.fromJson(json, VideoEditState.Stage1A::class.java)
                "Stage1AEditInput" -> gson.fromJson(json, VideoEditState.Stage1AEditInput::class.java)
                "Stage1A1Final" -> gson.fromJson(json, VideoEditState.Stage1A1Final::class.java)
                "Stage2AEditInput" -> gson.fromJson(json, VideoEditState.Stage2AEditInput::class.java)
                "Stage2A1Final" -> gson.fromJson(json, VideoEditState.Stage2A1Final::class.java)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load state from file", e)
            null
        }
    }
    
    private fun cleanupSession(sessionId: String) {
        try {
            val sessionDir = File(context.cacheDir, "$CACHE_DIR/session_$sessionId")
            if (sessionDir.exists()) {
                sessionDir.deleteRecursively()
                Log.d(TAG, "Cleaned up session $sessionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup session", e)
        }
    }
    
    private fun cleanupOldSessions() {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            if (!cacheDir.exists()) return
            
            val now = System.currentTimeMillis()
            cacheDir.listFiles()?.forEach { sessionDir ->
                if (sessionDir.name.startsWith("session_")) {
                    val lastModified = sessionDir.lastModified()
                    if (now - lastModified > SESSION_TIMEOUT) {
                        sessionDir.deleteRecursively()
                        Log.d(TAG, "Cleaned up old session ${sessionDir.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old sessions", e)
        }
    }
}