package com.example.clipcraft.models

import com.google.gson.annotations.SerializedName

/**
 * Данные проекта для сохранения в JSON
 */
data class ProjectData(
    @SerializedName("project_id")
    val projectId: String,
    
    @SerializedName("created_at")
    val createdAt: Long,
    
    @SerializedName("updated_at")
    val updatedAt: Long,
    
    @SerializedName("original_command")
    val originalCommand: String,
    
    @SerializedName("edit_commands")
    val editCommands: List<String> = emptyList(),
    
    @SerializedName("edit_plan")
    val editPlan: EditPlan,
    
    @SerializedName("timeline_segments")
    val timelineSegments: List<TimelineSegmentData>,
    
    @SerializedName("video_analyses")
    val videoAnalyses: List<VideoAnalysis>,
    
    @SerializedName("original_video_uris")
    val originalVideoUris: List<String>,
    
    @SerializedName("result_video_path")
    val resultVideoPath: String?,
    
    @SerializedName("is_manual_edit")
    val isManualEdit: Boolean = false,
    
    @SerializedName("ai_plan_raw")
    val aiPlanRaw: String? = null
)

/**
 * Данные сегмента таймлайна для сохранения
 */
data class TimelineSegmentData(
    @SerializedName("segment_id")
    val segmentId: String,
    
    @SerializedName("source_video_uri")
    val sourceVideoUri: String,
    
    @SerializedName("source_file_name")
    val sourceFileName: String,
    
    @SerializedName("original_duration")
    val originalDuration: Float,
    
    @SerializedName("in_point")
    val inPoint: Float,
    
    @SerializedName("out_point")
    val outPoint: Float,
    
    @SerializedName("timeline_position")
    val timelinePosition: Float
)