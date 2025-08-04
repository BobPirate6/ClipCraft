package com.example.clipcraft.utils

import android.content.Context
import com.example.clipcraft.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides localized processing messages
 */
@Singleton
class ProcessingMessageProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getEditModeMessage(): String = 
        context.getString(R.string.processing_edit_mode)
    
    fun getAnalyzingVideosMessage(): String = 
        context.getString(R.string.processing_analyzing_videos)
    
    fun getVideoProgressMessage(current: Int, total: Int): String = 
        context.getString(R.string.processing_video_progress, current, total)
    
    fun getVideoAnalyzedMessage(index: Int): String = 
        context.getString(R.string.processing_video_analyzed, index)
    
    fun getSpeechFoundMessage(videoIndex: Int, speech: String): String = 
        context.getString(R.string.processing_speech_found, videoIndex, speech)
    
    fun getAllVideosReadyMessage(): String = 
        context.getString(R.string.processing_all_ready)
    
    fun getAnalyzingContentMessage(): String = 
        context.getString(R.string.processing_analyzing_content)
    
    fun getAnalysisCompleteMessage(): String = 
        context.getString(R.string.processing_analysis_complete)
    
    fun getCreatingPlanMessage(): String = 
        context.getString(R.string.processing_creating_plan)
    
    fun getPlanReadyMessage(): String = 
        context.getString(R.string.processing_plan_ready)
    
    fun getPlanSummaryMessage(summary: String): String = 
        context.getString(R.string.processing_plan_summary, summary)
    
    fun getPlanNotesMessage(notes: String): String = 
        context.getString(R.string.processing_plan_notes, notes)
    
    fun getStartingEditMessage(): String = 
        context.getString(R.string.processing_starting_edit)
    
    fun getVideoReadyMessage(): String = 
        context.getString(R.string.processing_video_ready)
    
    // Enhanced tips with more variety
    fun getRandomTip(): String {
        val tips = listOf(
            R.string.speech_bubble_tip_1,
            R.string.speech_bubble_tip_2,
            R.string.speech_bubble_tip_3,
            R.string.speech_bubble_tip_4,
            R.string.speech_bubble_tip_5,
            R.string.speech_bubble_tip_6,
            R.string.speech_bubble_tip_7,
            R.string.speech_bubble_tip_8,
            R.string.speech_bubble_tip_9,
            R.string.speech_bubble_tip_10,
            R.string.speech_bubble_tip_11,
            R.string.speech_bubble_tip_12,
            R.string.speech_bubble_tip_13,
            R.string.speech_bubble_tip_14,
            R.string.speech_bubble_tip_15
        )
        return context.getString(tips.random())
    }
    
    // Progress messages with less repetition
    private var lastProgressMessageIndex = -1
    
    fun getRandomProgressMessage(): String {
        val messages = listOf(
            R.string.speech_bubble_progress_1,
            R.string.speech_bubble_progress_2,
            R.string.speech_bubble_progress_3,
            R.string.speech_bubble_progress_4,
            R.string.speech_bubble_progress_5,
            R.string.speech_bubble_progress_6,
            R.string.speech_bubble_progress_7,
            R.string.speech_bubble_progress_8,
            R.string.speech_bubble_progress_9,
            R.string.speech_bubble_progress_10,
            R.string.speech_bubble_progress_11,
            R.string.speech_bubble_progress_12,
            R.string.speech_bubble_progress_13,
            R.string.speech_bubble_progress_14,
            R.string.speech_bubble_progress_15
        )
        
        // Avoid repeating the same message
        var newIndex = messages.indices.random()
        while (newIndex == lastProgressMessageIndex && messages.size > 1) {
            newIndex = messages.indices.random()
        }
        lastProgressMessageIndex = newIndex
        
        return context.getString(messages[newIndex])
    }
    
    // Error messages
    fun getVideoAnalysisNotFoundError(): String = 
        context.getString(R.string.error_video_analysis_not_found)
    
    fun getCannotExtractFramesError(): String = 
        context.getString(R.string.error_cannot_extract_frames)
    
    fun getCannotAnalyzeVideoError(): String = 
        context.getString(R.string.error_cannot_analyze_video)
    
    fun getEmptyEditPlanError(): String = 
        context.getString(R.string.error_empty_edit_plan)
    
    fun getProcessingLaterError(): String = 
        context.getString(R.string.error_processing_later)
    
    fun getFeatureUnavailableError(): String = 
        context.getString(R.string.error_feature_unavailable)
    
    fun getInvalidParametersError(): String = 
        context.getString(R.string.error_invalid_parameters)
    
    fun getNetworkError(code: Int): String = 
        context.getString(R.string.error_network, code)
    
    fun getNoInternetError(): String = 
        context.getString(R.string.error_no_internet)
    
    fun getTimeoutError(): String = 
        context.getString(R.string.error_timeout)
    
    fun getUnknownError(): String = 
        context.getString(R.string.error_unknown)
    
    fun getServerUnavailableError(status: String): String = 
        context.getString(R.string.error_server_unavailable, status)
}