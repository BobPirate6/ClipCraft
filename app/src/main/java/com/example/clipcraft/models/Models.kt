package com.example.clipcraft.models

import android.net.Uri
import com.google.gson.annotations.SerializedName

// ✅ Состояния аутентификации
sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

// ✅ Состояния обработки видео (ОБНОВЛЕННЫЙ с videoAnalyses)
sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class ProgressUpdate(val message: String, val progress: Float? = null) : ProcessingState()
    data class Success(
        val result: String,
        val editPlan: EditPlan? = null,
        val videoAnalyses: Map<String, VideoAnalysis>? = null // Добавлено для сохранения анализа
    ) : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}

// ✅ Модель пользователя
data class User(
    val uid: String,
    val email: String,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val subscriptionType: SubscriptionType = SubscriptionType.FREE,
    val creditsRemaining: Int = 3,
    val isFirstTime: Boolean = true
) {
    constructor() : this("", "", null, null, SubscriptionType.FREE, 3, true)
}

// ✅ Типы подписки
enum class SubscriptionType {
    FREE,
    PREMIUM
}

// ✅ Модель выбранного видео
data class SelectedVideo(
    val uri: Uri,
    val path: String,
    val fileName: String,
    val durationMs: Long,
    val hasAudio: Boolean = false
)

// ✅ Настройки экспорта
data class ExportSettings(
    val resolution: VideoResolution = VideoResolution.FULL_HD_1080P,
    val frameRate: Int = 30,
    val bitrate: Int = 8_000_000,
    val audioEnabled: Boolean = true
)

// ✅ Разрешения видео
enum class VideoResolution(val width: Int, val height: Int) {
    HD_720P(1280, 720),
    FULL_HD_1080P(1920, 1080),
    UHD_4K(3840, 2160)
}

// ✅ Модели для анализа видео
data class VideoAnalysis(
    val fileName: String,
    val scenes: List<SceneAnalysis>
)

data class SceneAnalysis(
    val sceneNumber: Int,
    val startTime: Float,
    val endTime: Float,
    val frameBase64: String?,
    val transcription: List<TranscriptionSegment>
)

data class TranscriptionSegment(
    val start: Float,
    val end: Float,
    val text: String
)

// ✅ Модели для плана монтажа
data class EditPlan(
    val finalEdit: List<EditSegment>
)

data class EditSegment(
    val sourceVideo: String,
    val startTime: Float,
    val endTime: Float,
    val notes: String? = null
)

// ✅ Режим обработки видео
enum class ProcessingMode {
    NEW,      // Создание нового видео
    EDIT      // Редактирование существующего
}

// ✅ Состояние редактирования
data class EditingState(
    val mode: ProcessingMode = ProcessingMode.NEW,
    val originalCommand: String = "",
    val editCommand: String = "",
    val previousPlan: EditPlan? = null,
    val currentVideoPath: String? = null,
    val originalVideoAnalyses: Map<String, VideoAnalysis>? = null,
    val currentEditCount: Int = 0, // Количество правок текущего видео
    val parentVideoId: String? = null, // ID оригинального видео
    val isVoiceEditingFromEditor: Boolean = false // Флаг для отслеживания голосового редактирования из редактора
)

// ✅ Tutorial models
data class TutorialStep(
    val id: String,
    val title: String,
    val description: String,
    val targetElement: TutorialTarget,
    val position: TutorialPosition = TutorialPosition.AUTO
)

enum class TutorialTarget {
    GALLERY,
    INPUT_FIELD,
    VOICE_BUTTON,
    PROCESS_BUTTON,
    VIDEO_PLAYER,
    EDIT_BUTTON,
    SHARE_BUTTON,
    PROFILE_BUTTON,
    HISTORY_ITEM,
    NEW_VIDEO_BUTTON,
    MANUAL_EDIT_BUTTON
}

enum class TutorialPosition {
    AUTO,
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    CENTER
}

data class TutorialState(
    val isEnabled: Boolean = true,
    val currentStep: Int = 0,
    val completedSteps: Set<String> = emptySet(),
    val isShowing: Boolean = false
)

// ✅ История редактирования
data class EditHistory(
    val id: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val command: String,
    val plan: EditPlan,
    val resultPath: String,
    val videoUris: List<String> = emptyList(),
    val videoAnalyses: List<VideoAnalysis> = emptyList(),
    val editCount: Int = 0, // Количество правок для этого видео
    val parentId: String? = null // ID оригинального видео для отслеживания правок
) {
    constructor() : this("", 0L, "", EditPlan(emptyList()), "", emptyList(), emptyList(), 0, null)
}

// ✅ Дополнительные модели
data class EditResp(
    val success: Boolean,
    val message: String,
    val downloadUrl: String? = null
)

// ─────────────────────────────────────────────────────────────
//                    API REQUEST/RESPONSE MODELS
// ─────────────────────────────────────────────────────────────

data class ApiAnalyzeRequest(
    @SerializedName("user_command")
    val userCommand: String,
    @SerializedName("videos_analysis")
    val videosAnalysis: List<ApiVideoAnalysis>,
    @SerializedName("edit_mode")
    val editMode: Boolean = false,
    @SerializedName("edit_command")
    val editCommand: String = "",
    @SerializedName("previous_plan")
    val previousPlan: List<ApiEditSegment> = emptyList()
)

data class ApiVideoAnalysis(
    @SerializedName("filename")
    val filename: String,
    @SerializedName("scenes")
    val scenes: List<ApiSceneAnalysis>,
    @SerializedName("has_audio")
    val hasAudio: Boolean = true,
    @SerializedName("audio_info")
    val audioInfo: String? = null
)

data class ApiSceneAnalysis(
    @SerializedName("scene_number")
    val sceneNumber: Int,
    @SerializedName("start_time")
    val startTime: Float,
    @SerializedName("end_time")
    val endTime: Float,
    @SerializedName("frame_base64")
    val frameBase64: String,
    @SerializedName("transcription")
    val transcription: List<ApiTranscriptionSegment>
)

data class ApiTranscriptionSegment(
    @SerializedName("start")
    val start: Float,
    @SerializedName("end")
    val end: Float,
    @SerializedName("text")
    val text: String
)

data class ApiEditPlanResponse(
    @SerializedName("final_edit")
    val finalEdit: List<ApiEditSegment>
)

data class ApiEditSegment(
    @SerializedName("source_video")
    val sourceVideo: String,
    @SerializedName("start_time")
    val startTime: Float,
    @SerializedName("end_time")
    val endTime: Float,
    @SerializedName("notes")
    val notes: String? = null
)

data class ApiStatus(
    @SerializedName("status")
    val status: String
)

data class ApiHealth(
    @SerializedName("status")
    val status: String,
    @SerializedName("timestamp")
    val timestamp: Double
)