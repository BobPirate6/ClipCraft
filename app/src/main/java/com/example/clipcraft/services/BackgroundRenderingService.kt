package com.example.clipcraft.services

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global service for tracking background rendering state
 * Survives screen navigation and provides rendering status to any screen
 */
@Singleton
class BackgroundRenderingService @Inject constructor() {
    
    companion object {
        private const val TAG = "BackgroundRenderingService"
    }
    
    data class RenderingState(
        val isRendering: Boolean = false,
        val progress: Float = 0f,
        val currentSegment: Int = 0,
        val totalSegments: Int = 0,
        val resultPath: String? = null,
        val error: Exception? = null
    )
    
    private val _renderingState = MutableStateFlow(RenderingState())
    val renderingState: StateFlow<RenderingState> = _renderingState.asStateFlow()
    
    fun startRendering(totalSegments: Int) {
        Log.d(TAG, "Starting background rendering for $totalSegments segments")
        _renderingState.value = RenderingState(
            isRendering = true,
            totalSegments = totalSegments
        )
    }
    
    fun updateProgress(progress: Float, currentSegment: Int, totalSegments: Int) {
        Log.d(TAG, "Rendering progress: ${(progress * 100).toInt()}% (segment $currentSegment/$totalSegments)")
        _renderingState.value = _renderingState.value.copy(
            progress = progress,
            currentSegment = currentSegment,
            totalSegments = totalSegments
        )
    }
    
    fun completeRendering(resultPath: String) {
        Log.d(TAG, "Rendering completed: $resultPath")
        _renderingState.value = _renderingState.value.copy(
            isRendering = false,
            progress = 1f,
            resultPath = resultPath,
            error = null
        )
    }
    
    fun failRendering(error: Exception) {
        Log.e(TAG, "Rendering failed", error)
        _renderingState.value = _renderingState.value.copy(
            isRendering = false,
            error = error
        )
    }
    
    fun reset() {
        Log.d(TAG, "Resetting rendering state")
        _renderingState.value = RenderingState()
    }
    
    fun hasCompletedVideo(): Boolean {
        return _renderingState.value.resultPath != null && !_renderingState.value.isRendering
    }
    
    fun consumeResult(): String? {
        val result = _renderingState.value.resultPath
        if (result != null) {
            _renderingState.value = _renderingState.value.copy(resultPath = null)
        }
        return result
    }
}