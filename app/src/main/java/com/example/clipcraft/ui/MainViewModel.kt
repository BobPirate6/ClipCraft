package com.example.clipcraft.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import com.example.clipcraft.ClipCraftApplication
import com.example.clipcraft.domain.repository.AuthRepository
import com.example.clipcraft.domain.repository.EditHistoryRepository
import com.example.clipcraft.domain.repository.GalleryRepository
import com.example.clipcraft.domain.repository.VideoProcessingRepository
import com.example.clipcraft.models.*
import com.example.clipcraft.workers.EditWorker
import com.example.clipcraft.workers.VideoProcessingWorker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers

import java.util.UUID
import javax.inject.Inject
import android.app.Application
import com.example.clipcraft.utils.VideoEditorStateManager
import com.example.clipcraft.utils.TemporaryFileManager
import com.example.clipcraft.utils.VideoEditorUpdateManager
import com.example.clipcraft.domain.model.VideoEditorOrchestrator
import com.example.clipcraft.domain.model.AIEditResult
import com.example.clipcraft.domain.model.VideoStateManager
import com.example.clipcraft.domain.model.VideoEditState
import com.example.clipcraft.services.BackgroundRenderingService
import java.io.File

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val galleryRepository: GalleryRepository,
    private val historyRepository: EditHistoryRepository,
    private val processingRepository: VideoProcessingRepository,
    private val gson: Gson,
    private val sharedPreferences: SharedPreferences,
    private val application: Application,
    private val videoEditorStateManager: VideoEditorStateManager,
    private val temporaryFileManager: TemporaryFileManager,
    private val videoEditorUpdateManager: VideoEditorUpdateManager,
    private val videoEditorOrchestrator: VideoEditorOrchestrator,
    private val videoStateManager: VideoStateManager,
    private val backgroundRenderingService: BackgroundRenderingService
) : ViewModel() {

    companion object {
        private const val TAG = "MainViewModel"
        // Ключи для SharedPreferences (должны совпадать с Worker)
        private const val PREF_EDIT_PLAN_PREFIX = "work_edit_plan_"
        private const val PREF_VIDEO_ANALYSES_PREFIX = "work_video_analyses_"
        private const val PREF_MONTAGE_COUNT = "montage_count"
        private const val PREF_FEEDBACK_SHOWN = "feedback_dialog_shown"
    }

    enum class Screen { Intro, Main, Profile, VideoEditor, Subscription }

    private val _currentScreen = MutableStateFlow(Screen.Intro)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.getAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    val currentUser: StateFlow<User?> = authRepository.getCurrentUser()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _selectedVideos = MutableStateFlow<List<SelectedVideo>>(emptyList())
    val selectedVideos: StateFlow<List<SelectedVideo>> = _selectedVideos.asStateFlow()

    private val _userCommand = MutableStateFlow("")
    val userCommand: StateFlow<String> = _userCommand.asStateFlow()

    private val _editingState = MutableStateFlow(EditingState())
    val editingState: StateFlow<EditingState> = _editingState.asStateFlow()

    private val _editHistory = MutableStateFlow<List<EditHistory>>(emptyList())
    val editHistory: StateFlow<List<EditHistory>> = _editHistory.asStateFlow()

    private val _processingChatMessages = MutableStateFlow<List<String>>(emptyList())
    val processingChatMessages: StateFlow<List<String>> = _processingChatMessages.asStateFlow()

    private val _tutorialState = MutableStateFlow(TutorialState())
    val tutorialState: StateFlow<TutorialState> = _tutorialState.asStateFlow()
    
    // Background rendering state
    val backgroundRenderingState = backgroundRenderingService.renderingState
    
    private val _showFeedbackDialog = MutableStateFlow(false)
    val showFeedbackDialog: StateFlow<Boolean> = _showFeedbackDialog.asStateFlow()
    
    private val _showNoCreditsDialog = MutableStateFlow(false)
    val showNoCreditsDialog: StateFlow<Boolean> = _showNoCreditsDialog.asStateFlow()

    // Определяем начальные шаги туториала
    private val initialTutorialSteps = listOf(
        TutorialStep(
            id = "welcome",
            title = "Добро пожаловать в ClipCraft!",
            description = "Это приложение поможет вам создавать потрясающие видеоклипы с помощью AI. Давайте я покажу, как им пользоваться.",
            targetElement = TutorialTarget.GALLERY,
            position = TutorialPosition.CENTER
        ),
        TutorialStep(
            id = "select_videos",
            title = "Выберите видео",
            description = "Начните с выбора видео из галереи. Вы можете выбрать до 20 видео для создания клипа.",
            targetElement = TutorialTarget.GALLERY,
            position = TutorialPosition.TOP
        ),
        TutorialStep(
            id = "input_command",
            title = "Опишите свою идею",
            description = "Введите текстом или голосом, какой клип вы хотите создать. AI поймет ваш запрос и смонтирует видео.",
            targetElement = TutorialTarget.INPUT_FIELD,
            position = TutorialPosition.TOP
        ),
        TutorialStep(
            id = "voice_input",
            title = "Голосовой ввод",
            description = "Нажмите на микрофон для голосового ввода команды.",
            targetElement = TutorialTarget.VOICE_BUTTON,
            position = TutorialPosition.LEFT
        )
    )
    
    // Дополнительные шаги туториала
    private val processButtonStep = TutorialStep(
        id = "process_video",
        title = "Создайте клип",
        description = "Теперь нажмите эту кнопку, чтобы AI начал монтировать ваше видео.",
        targetElement = TutorialTarget.PROCESS_BUTTON,
        position = TutorialPosition.TOP
    )
    
    private val afterProcessingSteps = listOf(
        TutorialStep(
            id = "edit_video",
            title = "Редактируйте результат",
            description = "После создания клипа вы можете его отредактировать, добавив новые инструкции для AI.",
            targetElement = TutorialTarget.EDIT_BUTTON,
            position = TutorialPosition.TOP
        ),
        TutorialStep(
            id = "share_video",
            title = "Поделитесь клипом",
            description = "Готовый клип можно сохранить или поделиться в социальных сетях.",
            targetElement = TutorialTarget.SHARE_BUTTON,
            position = TutorialPosition.TOP
        ),
        TutorialStep(
            id = "profile",
            title = "Ваш профиль",
            description = "Здесь вы можете управлять аккаунтом, просматривать историю и настраивать приложение.",
            targetElement = TutorialTarget.PROFILE_BUTTON,
            position = TutorialPosition.LEFT
        )
    )
    
    // Используем первые 4 шага туториала + динамический шаг для кнопки процесса
    private val tutorialSteps = initialTutorialSteps.toMutableList()

    init {
        Log.d(TAG, "MainViewModel INIT called")
        loadEditHistory()
        checkPendingWork()
        checkTutorialStatus()
        
        // Наблюдаем за изменением состояния авторизации
        viewModelScope.launch {
            authState.collect { state ->
                Log.d(TAG, "Auth state changed: $state")
                when (state) {
                    is AuthState.Authenticated -> {
                        Log.d(TAG, "User authenticated, navigating to main screen")
                        if (_currentScreen.value == Screen.Intro) {
                            _currentScreen.value = Screen.Main
                        }
                    }
                    is AuthState.Unauthenticated -> {
                        Log.d(TAG, "User unauthenticated, navigating to intro screen")
                        _currentScreen.value = Screen.Intro
                    }
                    else -> {}
                }
            }
        }
        
        // Monitor background rendering completion
        viewModelScope.launch {
            backgroundRenderingState.collect { renderState ->
                if (renderState.resultPath != null && !renderState.isRendering) {
                    Log.d(TAG, "Background render completed: ${renderState.resultPath}")
                    Log.d("clipcraftlogic", "Background render detected in MainViewModel: ${renderState.resultPath}")
                    
                    // Update current video with rendered result BEFORE consuming
                    updateVideoAfterBackgroundRender(renderState.resultPath)
                    
                    // Delay to ensure UI updates
                    delay(100)
                    
                    // Now consume the result to clear it from service
                    backgroundRenderingService.consumeResult()
                }
            }
        }
    }

    private fun checkPendingWork() {
        viewModelScope.launch {
            processingRepository.getSavedWorkId()?.let { workId ->
                Log.d(TAG, "Found pending work: $workId")
                observeWork(workId)
            }
        }
    }

    // --- Authentication ---
    fun signInWithGoogle(idToken: String) = viewModelScope.launch {
        authRepository.signInWithGoogle(idToken)
    }

    fun signInWithEmail(email: String, password: String) = viewModelScope.launch {
        Log.d(TAG, "Attempting to sign in with email: $email")
        authRepository.signInWithEmail(email, password).fold(
            onSuccess = { user ->
                Log.d(TAG, "Sign in successful for: ${user.email}")
            },
            onFailure = { exception ->
                Log.e(TAG, "Sign in failed: ${exception.message}")
            }
        )
    }

    fun createAccount(email: String, password: String) = viewModelScope.launch {
        Log.d(TAG, "Attempting to create account for email: $email")
        authRepository.createAccount(email, password).fold(
            onSuccess = { user ->
                Log.d(TAG, "Account created successfully for: ${user.email}")
            },
            onFailure = { exception ->
                Log.e(TAG, "Account creation failed: ${exception.message}")
            }
        )
    }

    fun signOut() {
        authRepository.signOut()
        _currentScreen.value = Screen.Intro
    }
    
    fun sendPasswordResetEmail(email: String) = viewModelScope.launch {
        authRepository.sendPasswordResetEmail(email)
    }

    fun updateUserName(newName: String) = viewModelScope.launch {
        currentUser.value?.uid?.let { uid ->
            authRepository.updateUserName(uid, newName)
        }
    }

    fun deleteAccount() = viewModelScope.launch {
        val authService = authRepository.javaClass.getDeclaredField("authService").apply {
            isAccessible = true
        }.get(authRepository) as com.example.clipcraft.services.AuthService

        authService.deleteAccount()
            .onSuccess {
                _currentScreen.value = Screen.Intro
            }
            .onFailure { error ->
                Log.e(TAG, "Failed to delete account", error)
            }
    }

    // --- Video Processing ---
    fun processVideos() {
        Log.d(TAG, "processVideos called")
        Log.d("clipcraftlogic", "=== PROCESS VIDEOS STARTED ===")
        Log.d("clipcraftlogic", "Selected videos: ${_selectedVideos.value.size}")
        Log.d("clipcraftlogic", "Command: ${_userCommand.value}")
        Log.d("clipcraftlogic", "Current session before processing: ${videoStateManager.currentState.value?.sessionId}")
        
        if (_selectedVideos.value.isEmpty() || _userCommand.value.isBlank()) {
            Log.e(TAG, "processVideos: Missing videos or command")
            Log.e("clipcraftlogic", "ERROR: Missing videos or command")
            _processingState.value = ProcessingState.Error("Выберите видео и введите команду.")
            return
        }
        
        // Проверяем кредиты
        val user = currentUser.value
        if (user == null || user.creditsRemaining <= 0) {
            Log.e(TAG, "processVideos: No credits remaining")
            Log.e("clipcraftlogic", "ERROR: No credits remaining")
            _processingState.value = ProcessingState.Error("У вас закончились кредиты.")
            return
        }
        
        // Инициализируем новую сессию с выбранными видео
        Log.d("clipcraftlogic", "Starting new session with ${_selectedVideos.value.size} videos")
        videoStateManager.startNewSession(_selectedVideos.value.map { it.uri })
        Log.d("clipcraftlogic", "New session created: ${videoStateManager.currentState.value?.sessionId}")
        
        val videoUris = _selectedVideos.value.map { it.uri.toString() }.toTypedArray()
        Log.d(TAG, "Starting work with ${videoUris.size} videos and command: ${_userCommand.value}")
        Log.d("clipcraftlogic", "Starting work processing")
        val workId = processingRepository.processNewVideo(videoUris, _userCommand.value)
        Log.d("clipcraftlogic", "Work ID: $workId")
        observeWork(workId)
        Log.d("clipcraftlogic", "=== PROCESS VIDEOS INITIATED ===")
    }
    
    fun startManualEdit() {
        Log.d(TAG, "startManualEdit called")
        Log.d("clipcraftlogic", "=== START MANUAL EDIT ===")
        Log.d("clipcraftlogic", "Selected videos: ${_selectedVideos.value.size}")
        Log.d("clipcraftlogic", "Current session before manual edit: ${videoStateManager.currentState.value?.sessionId}")
        
        if (_selectedVideos.value.isEmpty()) {
            Log.e(TAG, "startManualEdit: No videos selected")
            Log.e("clipcraftlogic", "ERROR: No videos selected for manual edit")
            _processingState.value = ProcessingState.Error("Выберите видео для редактирования.")
            return
        }
        
        // Инициализируем новую сессию для ручного редактирования
        Log.d("clipcraftlogic", "Starting new session for manual editing")
        videoStateManager.startNewSession(_selectedVideos.value.map { it.uri })
        Log.d("clipcraftlogic", "New session created: ${videoStateManager.currentState.value?.sessionId}")
        
        // Создаем пустой план монтажа для ручного редактирования
        val emptyEditPlan = EditPlan(emptyList())
        Log.d("clipcraftlogic", "Created empty edit plan for manual editing")
        
        // Устанавливаем состояние успеха с пустым планом для ручного редактирования
        _processingState.value = ProcessingState.Success(
            result = "", // Путь будет создан в видеоредакторе
            editPlan = emptyEditPlan,
            videoAnalyses = emptyMap()
        )
        Log.d("clipcraftlogic", "Processing state set to Success with empty plan")
        
        // Переходим в видеоредактор
        navigateTo(Screen.VideoEditor)
        Log.d("clipcraftlogic", "=== NAVIGATING TO VIDEO EDITOR ===")
    }

    fun startEditing(editCommand: String) {
        _processingState.value = ProcessingState.Processing
        Log.d(TAG, "startEditing called with command: $editCommand")
        val currentState = _editingState.value
        
        // Детальное логирование состояния
        Log.d(TAG, "Current editing state:")
        Log.d(TAG, "  mode: ${currentState.mode}")
        Log.d(TAG, "  originalCommand: ${currentState.originalCommand}")
        Log.d(TAG, "  editCommand: ${currentState.editCommand}")
        Log.d(TAG, "  previousPlan: ${currentState.previousPlan?.finalEdit?.size ?: "null"}")
        Log.d(TAG, "  originalVideoAnalyses: ${currentState.originalVideoAnalyses?.size ?: "null"}")
        Log.d(TAG, "  selectedVideos count: ${_selectedVideos.value.size}")
        Log.d(TAG, "  currentEditCount: ${currentState.currentEditCount}")
        
        if (_selectedVideos.value.isEmpty() || editCommand.isBlank() || currentState.previousPlan == null || currentState.originalVideoAnalyses == null) {
            Log.e(TAG, "startEditing: Missing data")
            Log.e(TAG, "  selectedVideos.isEmpty: ${_selectedVideos.value.isEmpty()}")
            Log.e(TAG, "  editCommand.isBlank: ${editCommand.isBlank()}")
            Log.e(TAG, "  previousPlan is null: ${currentState.previousPlan == null}")
            Log.e(TAG, "  originalVideoAnalyses is null: ${currentState.originalVideoAnalyses == null}")
            _processingState.value = ProcessingState.Error("Отсутствуют данные для редактирования.")
            return
        }
        
        // Проверяем кредиты для правок
        val user = currentUser.value
        if (currentState.currentEditCount > 0 && (user == null || user.creditsRemaining <= 0)) {
            Log.e(TAG, "startEditing: No credits remaining for edits")
            _processingState.value = ProcessingState.Error("У вас закончились кредиты. Первая правка бесплатна.")
            return
        }
        _editingState.value = currentState.copy(editCommand = editCommand)
        val videoUris = _selectedVideos.value.map { it.uri.toString() }.toTypedArray()
        
        Log.d(TAG, "Calling processEditedVideo with:")
        Log.d(TAG, "  videoUris count: ${videoUris.size}")
        Log.d(TAG, "  originalCommand: ${currentState.originalCommand}")
        Log.d(TAG, "  editCommand: $editCommand")
        
        val workId = processingRepository.processEditedVideo(
            videoUris = videoUris,
            originalCommand = currentState.originalCommand,
            editCommand = editCommand,
            previousPlan = currentState.previousPlan,
            originalVideoAnalyses = currentState.originalVideoAnalyses
        )
        observeWork(workId)
    }

    private fun observeWork(workId: UUID) {
        Log.d(TAG, "Observing work: $workId")
        viewModelScope.launch {
            processingRepository.getWorkInfoById(workId).collect { workInfo ->
                if (workInfo == null) {
                    Log.w(TAG, "WorkInfo is null for $workId")
                    return@collect
                }

                Log.d(TAG, "WorkInfo state: ${workInfo.state}, id: $workId")

                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        _processingState.value = ProcessingState.Processing
                        val progressMsg = workInfo.progress.getString(VideoProcessingWorker.KEY_PROGRESS_MESSAGE)
                            ?: workInfo.progress.getString(EditWorker.KEY_PROGRESS_MESSAGE)
                        if (!progressMsg.isNullOrBlank() && !_processingChatMessages.value.contains(progressMsg)) {
                            Log.d(TAG, "Progress message: $progressMsg")
                            _processingChatMessages.value += progressMsg
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "Work succeeded, handling output data")
                        handleSuccessfulWork(workInfo.outputData)
                    }
                    WorkInfo.State.FAILED -> {
                        val errorMsg = workInfo.outputData.getString(VideoProcessingWorker.KEY_ERROR_MESSAGE)
                            ?: workInfo.outputData.getString(EditWorker.KEY_ERROR_MESSAGE)
                            ?: "Неизвестная ошибка обработки"
                        Log.e(TAG, "Work failed: $errorMsg")
                        _processingState.value = ProcessingState.Error(errorMsg)
                        processingRepository.clearCurrentWorkId()
                    }
                    WorkInfo.State.CANCELLED -> {
                        Log.w(TAG, "Work was cancelled")
                        _processingState.value = ProcessingState.Error("Обработка отменена")
                        processingRepository.clearCurrentWorkId()
                    }
                    WorkInfo.State.ENQUEUED -> {
                        Log.d(TAG, "Work enqueued")
                    }
                    WorkInfo.State.BLOCKED -> {
                        Log.d(TAG, "Work blocked")
                    }
                }
            }
        }
    }

    private fun handleSuccessfulWork(outputData: Data) {
        Log.d(TAG, "handleSuccessfulWork called")
        Log.d("videoeditorclipcraft", "handleSuccessfulWork called")
        Log.d("clipcraftlogic", "=== HANDLE SUCCESSFUL WORK ===")
        Log.d("clipcraftlogic", "Current session: ${videoStateManager.currentState.value?.sessionId}")
        Log.d("clipcraftlogic", "Current state: ${videoStateManager.currentState.value?.javaClass?.simpleName}")

        // Логируем все ключи в outputData
        outputData.keyValueMap.forEach { (key, value) ->
            Log.d(TAG, "OutputData key: $key, value type: ${value?.javaClass?.simpleName}, value: $value")
        }

        val resultPath = outputData.getString(VideoProcessingWorker.KEY_RESULT_PATH)
            ?: outputData.getString(EditWorker.KEY_RESULT_PATH)
        val workId = outputData.getString(VideoProcessingWorker.KEY_WORK_ID)
            ?: outputData.getString(EditWorker.KEY_WORK_ID)

        Log.d(TAG, "Result path: $resultPath")
        Log.d(TAG, "Work ID: $workId")

        if (resultPath != null && workId != null) {
            try {
                // Загружаем данные из SharedPreferences
                val editPlanKey = "$PREF_EDIT_PLAN_PREFIX$workId"
                val videoAnalysesKey = "$PREF_VIDEO_ANALYSES_PREFIX$workId"

                val editPlanJson = sharedPreferences.getString(editPlanKey, null)
                val videoAnalysesJson = sharedPreferences.getString(videoAnalysesKey, null)

                Log.d(TAG, "Loaded from SharedPreferences:")
                Log.d(TAG, "Edit plan JSON exists: ${editPlanJson != null}")
                Log.d(TAG, "Video analyses JSON exists: ${videoAnalysesJson != null}")

                if (editPlanJson != null) {
                    val editPlan = gson.fromJson(editPlanJson, EditPlan::class.java)
                    Log.d(TAG, "Edit plan parsed: ${editPlan.finalEdit.size} segments")

                    val videoAnalysesList: List<VideoAnalysis> = if (!videoAnalysesJson.isNullOrEmpty()) {
                        val type = object : TypeToken<List<VideoAnalysis>>() {}.type
                        gson.fromJson(videoAnalysesJson, type)
                    } else {
                        emptyList()
                    }
                    Log.d(TAG, "Video analyses parsed: ${videoAnalysesList.size} items")

                    val videoAnalysesMap = videoAnalysesList.associateBy { it.fileName }

                    val successState = ProcessingState.Success(resultPath, editPlan, videoAnalysesMap)
                    _processingState.value = successState
                    Log.d(TAG, "Processing state set to Success")
                    Log.d("videoeditorclipcraft", "Processing state set to Success with ${editPlan.finalEdit.size} segments")
                    Log.d("videoeditorclipcraft", "Current screen: ${_currentScreen.value}")
                    
                    // Переходим в состояние Stage1A если это первое AI создание
                    if (_editingState.value.mode == ProcessingMode.NEW) {
                        Log.d("clipcraftlogic", "Transitioning to AI created state")
                        Log.d("clipcraftlogic", "Video path: $resultPath")
                        Log.d("clipcraftlogic", "Edit plan segments: ${editPlan.finalEdit.size}")
                        videoStateManager.transitionToAICreated(
                            videoPath = resultPath,
                            editPlan = editPlan,
                            sourceVideos = _selectedVideos.value.map { it.uri }
                        )
                        Log.d("clipcraftlogic", "State after transition: ${videoStateManager.currentState.value?.javaClass?.simpleName}")
                    }
                    
                    // Обновляем editingState с новым путем видео, если мы в режиме редактирования
                    if (_editingState.value.mode == ProcessingMode.EDIT) {
                        Log.d(TAG, "Updating editingState with new video path: $resultPath")
                        _editingState.value = _editingState.value.copy(
                            currentVideoPath = resultPath
                        )
                        
                        // Если это голосовое редактирование из редактора, сохраняем обновление для редактора
                        if (_editingState.value.isVoiceEditingFromEditor) {
                            Log.d(TAG, "Saving update for video editor")
                            videoEditorUpdateManager.setPendingUpdate(
                                VideoEditorUpdateManager.EditorUpdate(
                                    editPlan = editPlan,
                                    videoAnalyses = videoAnalysesMap,
                                    selectedVideos = _selectedVideos.value,
                                    resultPath = resultPath
                                )
                            )
                        }
                    }

                    saveResultToHistory(successState)
                    
                    // Увеличиваем счетчик монтажей
                    incrementMontageCount()
                    
                    // Обновляем оркестратор с результатом AI
                    if (_editingState.value.isVoiceEditingFromEditor) {
                        viewModelScope.launch {
                            val aiResult = AIEditResult(
                                command = _editingState.value.editCommand,
                                videoPath = resultPath,
                                editPlan = editPlan,
                                videoAnalyses = videoAnalysesMap
                            )
                            videoEditorOrchestrator.onAIEditComplete(aiResult)
                        }
                        
                        // Переходим обратно в редактор после обработки
                        // Но сначала дадим время обновиться состоянию
                        viewModelScope.launch {
                            delay(100) // Небольшая задержка для гарантии обновления состояния
                            _currentScreen.value = Screen.VideoEditor
                            Log.d(TAG, "Navigating back to VideoEditor after AI processing")
                        }
                    } else if (_editingState.value.mode == ProcessingMode.NEW) {
                        // Для первого AI монтажа НЕ переходим в редактор автоматически
                        // Остаемся на главном экране для отображения результата
                        Log.d(TAG, "First AI processing completed, staying on Main screen")
                    }

                    // Очищаем сообщения чата
                    _processingChatMessages.value = emptyList()

                    // Очищаем данные из SharedPreferences
                    sharedPreferences.edit()
                        .remove(editPlanKey)
                        .remove(videoAnalysesKey)
                        .apply()
                    Log.d(TAG, "Cleaned up SharedPreferences")
                } else {
                    Log.e(TAG, "Edit plan not found in SharedPreferences")
                    _processingState.value = ProcessingState.Error("План монтажа не найден")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing work result", e)
                _processingState.value = ProcessingState.Error("Ошибка обработки результата: ${e.message}")
            }
        } else {
            Log.e(TAG, "Missing result data: resultPath=$resultPath, workId=$workId")
            _processingState.value = ProcessingState.Error("Не получен результат обработки")
        }

        _processingChatMessages.value = emptyList()
        processingRepository.clearCurrentWorkId()
    }

    // --- Gallery and Video Selection ---
    fun loadGalleryVideos(): Flow<List<SelectedVideo>> = galleryRepository.loadGalleryVideos()

    fun addVideos(uris: List<Uri>) = viewModelScope.launch {
        Log.d(TAG, "Adding ${uris.size} videos")
        val currentVideos = _selectedVideos.value
        if (currentVideos.size + uris.size > 20) {
            _processingState.value = ProcessingState.Error("Максимум 20 видео")
            return@launch
        }
        val newVideos = uris.mapNotNull { galleryRepository.getVideoInfo(it) }
        _selectedVideos.value = currentVideos + newVideos
        Log.d(TAG, "Selected videos updated: ${_selectedVideos.value.size} total")
    }

    fun removeVideo(fileName: String) {
        Log.d(TAG, "Removing video: $fileName")
        _selectedVideos.value = _selectedVideos.value.filter { it.fileName != fileName }
    }

    // --- History ---
    private fun loadEditHistory() {
        _editHistory.value = historyRepository.loadEditHistory()
        Log.d(TAG, "Loaded ${_editHistory.value.size} history items")
    }

    private fun saveResultToHistory(state: ProcessingState.Success) = viewModelScope.launch {
        val videoAnalyses = state.videoAnalyses?.values?.toList() ?: emptyList()
        val currentEditingState = _editingState.value
        
        // Определяем parentId и editCount
        val parentId = if (currentEditingState.mode == ProcessingMode.EDIT) {
            currentEditingState.parentVideoId ?: UUID.randomUUID().toString()
        } else {
            UUID.randomUUID().toString()
        }
        
        val editCount = if (currentEditingState.mode == ProcessingMode.EDIT) {
            currentEditingState.currentEditCount + 1
        } else {
            0
        }
        
        // Сохраняем оригинальные URI видео, а не временные пути
        val originalVideoUris = _selectedVideos.value.map { selectedVideo ->
            // Используем оригинальный URI из галереи
            selectedVideo.uri.toString()
        }
        
        val historyEntry = EditHistory(
            id = UUID.randomUUID().toString(),
            command = _userCommand.value,
            plan = state.editPlan!!,
            resultPath = state.result,
            videoUris = originalVideoUris, // Используем оригинальные URI
            videoAnalyses = videoAnalyses,
            editCount = editCount,
            parentId = if (currentEditingState.mode == ProcessingMode.EDIT) parentId else null
        )
        historyRepository.saveEditHistory(historyEntry)
        _editHistory.value = listOf(historyEntry) + _editHistory.value

        // Расходуем кредиты
        currentUser.value?.let { user ->
            val shouldChargeCredit = when {
                // Новое видео - всегда 1 кредит
                currentEditingState.mode == ProcessingMode.NEW -> true
                // Правка - бесплатно только первая правка
                currentEditingState.mode == ProcessingMode.EDIT && currentEditingState.currentEditCount > 0 -> true
                // Первая правка бесплатна
                else -> false
            }
            
            if (shouldChargeCredit) {
                val newCredits = user.creditsRemaining - 1
                authRepository.updateUserCredits(user.uid, newCredits)
                Log.d(TAG, "Credit charged. Remaining: $newCredits")
            } else {
                Log.d(TAG, "No credit charged (first edit is free)")
            }
        }
        
        // Обновляем editingState с новым количеством правок
        if (currentEditingState.mode == ProcessingMode.EDIT) {
            _editingState.value = currentEditingState.copy(
                currentEditCount = editCount,
                parentVideoId = parentId
            )
        }
    }

    fun loadHistoryItemForEdit(historyItem: EditHistory) = viewModelScope.launch {
        Log.d(TAG, "=== Loading history item for edit: ${historyItem.id} ===")
        resetProcessing()
        _userCommand.value = historyItem.command

        // ОТЛАДКА: Логируем содержимое истории
        Log.d(TAG, "History command: ${historyItem.command}")
        Log.d(TAG, "History plan segments: ${historyItem.plan.finalEdit.size}")
        historyItem.plan.finalEdit.forEachIndexed { index, segment ->
            Log.d(TAG, "History plan segment $index: sourceVideo='${segment.sourceVideo}', start=${segment.startTime}, end=${segment.endTime}")
        }

        Log.d(TAG, "History video URIs: ${historyItem.videoUris.size}")
        historyItem.videoUris.forEach { uri ->
            Log.d(TAG, "History URI: $uri")
        }

        Log.d(TAG, "History video analyses: ${historyItem.videoAnalyses.size}")
        historyItem.videoAnalyses.forEach { analysis ->
            Log.d(TAG, "History analysis: fileName='${analysis.fileName}', scenes=${analysis.scenes.size}")
        }

        val videoUris = historyItem.videoUris.map { uriString ->
            if (uriString.startsWith("content://")) {
                Uri.parse(uriString)
            } else {
                uriString.toUri()
            }
        }
        addVideos(videoUris)

        val videoAnalysesMap = historyItem.videoAnalyses.associateBy { it.fileName }

        // ОТЛАДКА: Логируем финальную карту анализов
        Log.d(TAG, "=== Video analyses map created ===")
        videoAnalysesMap.forEach { (fileName, analysis) ->
            Log.d(TAG, "Map entry: '$fileName' -> ${analysis.scenes.size} scenes")
        }

        _editingState.value = EditingState(
            mode = ProcessingMode.EDIT,
            originalCommand = historyItem.command,
            previousPlan = historyItem.plan,
            currentVideoPath = historyItem.resultPath,
            originalVideoAnalyses = videoAnalysesMap,
            currentEditCount = historyItem.editCount,
            parentVideoId = historyItem.parentId ?: historyItem.id
        )
        _processingState.value = ProcessingState.Success(historyItem.resultPath, historyItem.plan, videoAnalysesMap)

        Log.d(TAG, "=== History item loaded successfully ===")
    }

    // --- UI State and Navigation ---
    fun navigateTo(screen: Screen) {
        Log.d("videoeditorclipcraft", "navigateTo called: from ${_currentScreen.value} to $screen")
        _currentScreen.value = screen
        
        // Если возвращаемся из редактора на главный экран, сбрасываем режим редактирования
        if (screen == Screen.Main) {
            _editingState.update { it.copy(mode = ProcessingMode.NEW) }
        }
    }

    fun updateCommand(command: String) {
        _userCommand.value = command
    }
    
    fun updateEditingState(state: EditingState) {
        _editingState.value = state
    }

    fun resetProcessing() {
        Log.d(TAG, "Resetting processing")
        Log.d("videoeditorclipcraft", "resetProcessing called - setting state to Idle")
        Log.d("videoeditorclipcraft", "Stack trace:", Exception())
        _processingState.value = ProcessingState.Idle
        processingRepository.cancelAllWork()
    }

    fun createNewVideo() {
        Log.d(TAG, "Creating new video")
        Log.d("videoeditorclipcraft", "createNewVideo called - resetting all states")
        Log.d("clipcraftlogic", "=== CREATE NEW VIDEO STARTED ===")
        Log.d("clipcraftlogic", "Current session: ${videoStateManager.currentState.value?.sessionId}")
        Log.d("clipcraftlogic", "Current state: ${videoStateManager.currentState.value?.javaClass?.simpleName}")
        Log.d("clipcraftlogic", "Current processing state: ${_processingState.value}")
        Log.d("clipcraftlogic", "Selected videos count: ${_selectedVideos.value.size}")
        
        // Отмечаем, что состояние VideoEditorViewModel нужно очистить
        videoEditorStateManager.markForClear()
        Log.d("clipcraftlogic", "VideoEditorStateManager marked for clear")
        
        // Очищаем сессию в VideoStateManager
        videoStateManager.clearSession()
        Log.d("clipcraftlogic", "VideoStateManager session cleared")
        
        // Reset background rendering service
        backgroundRenderingService.reset()
        Log.d("clipcraftlogic", "BackgroundRenderingService reset")
        
        // Очищаем все временные файлы
        viewModelScope.launch {
            Log.d("clipcraftlogic", "Starting temporary file cleanup")
            temporaryFileManager.cleanupAllTemporaryFiles()
            Log.d("clipcraftlogic", "Temporary file cleanup completed")
        }
        
        // Полный сброс всех состояний
        resetProcessing()
        _editingState.value = EditingState()
        _selectedVideos.value = emptyList()
        _userCommand.value = ""
        _currentScreen.value = Screen.Main
        
        Log.d("clipcraftlogic", "All states reset completed")
        Log.d("clipcraftlogic", "=== CREATE NEW VIDEO COMPLETED ===")
    }
    
    fun updateEditPlanAfterManualEdit(updatedEditPlan: EditPlan) {
        Log.d("videoeditorclipcraft", "Updating edit plan after manual edits")
        val currentState = _processingState.value
        if (currentState is ProcessingState.Success) {
            // Update the processing state with the new edit plan
            _processingState.value = currentState.copy(
                editPlan = updatedEditPlan
            )
            
            // Update the saved history if it exists
            _editHistory.value.firstOrNull()?.let { latestHistory ->
                viewModelScope.launch {
                    val updatedHistory = latestHistory.copy(plan = updatedEditPlan)
                    historyRepository.updateEditHistory(updatedHistory)
                    // Update local history list
                    _editHistory.value = listOf(updatedHistory) + _editHistory.value.drop(1)
                }
            }
        }
    }
    
    private fun updateVideoAfterBackgroundRender(renderedPath: String) {
        Log.d(TAG, "Updating video after background render: $renderedPath")
        Log.d("clipcraftlogic", "=== updateVideoAfterBackgroundRender ===")
        Log.d("clipcraftlogic", "Rendered path: $renderedPath")
        Log.d("clipcraftlogic", "File exists: ${File(renderedPath).exists()}")
        
        val currentState = _processingState.value
        Log.d("clipcraftlogic", "Current processing state: ${currentState.javaClass.simpleName}")
        
        if (currentState is ProcessingState.Success) {
            // Update video path
            _editingState.value = _editingState.value.copy(
                currentVideoPath = renderedPath
            )
            Log.d("clipcraftlogic", "Updated editing state with new video path")
            
            // Update processing state with new path
            _processingState.value = currentState.copy(
                result = renderedPath
            )
            Log.d("clipcraftlogic", "Updated processing state with new video path")
            
            Log.d(TAG, "Video updated with background rendered result")
            Log.d("clipcraftlogic", "=== updateVideoAfterBackgroundRender COMPLETED ===")
        } else {
            Log.e("clipcraftlogic", "ERROR: Cannot update video - processing state is not Success")
        }
    }
    
    fun replaceCurrentVideoWithEdited(tempVideoPath: String, updatedEditPlan: EditPlan) {
        Log.d("videoeditorclipcraft", "Replacing current video with edited version, path: $tempVideoPath")
        Log.d("clipcraftlogic", "=== REPLACE CURRENT VIDEO WITH EDITED ===")
        Log.d("clipcraftlogic", "Temp video path: $tempVideoPath")
        Log.d("clipcraftlogic", "Updated edit plan segments: ${updatedEditPlan.finalEdit.size}")
        Log.d("clipcraftlogic", "Current session: ${videoStateManager.currentState.value?.sessionId}")
        
        // Empty path indicates background rendering in progress
        if (tempVideoPath.isEmpty()) {
            Log.d("videoeditorclipcraft", "Background rendering started, will update when complete")
            Log.d("clipcraftlogic", "Background rendering started - no path yet")
            
            // Keep the current processing state but update the edit plan
            val currentState = _processingState.value
            if (currentState is ProcessingState.Success) {
                _processingState.value = currentState.copy(
                    editPlan = updatedEditPlan
                )
                Log.d("clipcraftlogic", "Updated edit plan in processing state")
            }
            return
        }
        
        val currentState = _processingState.value
        Log.d("clipcraftlogic", "Current processing state: ${currentState.javaClass.simpleName}")
        
        if (currentState is ProcessingState.Success) {
            // НЕ сохраняем в галерею, только обновляем путь к временному файлу
            Log.d("videoeditorclipcraft", "Updating to temp video path: $tempVideoPath")
            Log.d("clipcraftlogic", "Updating processing state with new video path")
            
            // Check if file exists
            val tempFile = File(tempVideoPath)
            Log.d("clipcraftlogic", "Temp file exists: ${tempFile.exists()}, size: ${tempFile.length()}")
            
            // Обновляем путь к видео на временный файл
            _editingState.value = _editingState.value.copy(
                currentVideoPath = tempVideoPath
            )
            Log.d("clipcraftlogic", "Editing state updated with new path")
            
            // Обновляем состояние с новым планом и временным путем
            _processingState.value = currentState.copy(
                result = tempVideoPath,
                editPlan = updatedEditPlan
            )
            Log.d("clipcraftlogic", "Processing state updated with new path and plan")
            
            // Обновляем историю
            _editHistory.value.firstOrNull()?.let { latestHistory ->
                viewModelScope.launch {
                    val updatedHistory = latestHistory.copy(
                        plan = updatedEditPlan,
                        resultPath = tempVideoPath
                    )
                    historyRepository.updateEditHistory(updatedHistory)
                    _editHistory.value = listOf(updatedHistory) + _editHistory.value.drop(1)
                    Log.d("clipcraftlogic", "History updated with new path")
                }
            }
            
            Log.d("videoeditorclipcraft", "Video replaced successfully with temp file")
            Log.d("clipcraftlogic", "=== VIDEO REPLACEMENT COMPLETED ===")
        } else {
            Log.e("clipcraftlogic", "ERROR: Cannot replace video - processing state is not Success")
        }
    }

    fun setEditMode(enabled: Boolean) {
        Log.d(TAG, "Setting edit mode: $enabled")
        if (!enabled) {
            _editingState.value = EditingState()
            // При выходе из режима редактирования сбрасываем состояние обработки
            // только если у нас нет успешного результата
            if (_processingState.value !is ProcessingState.Success) {
                _processingState.value = ProcessingState.Idle
            }
            return
        }

        val currentState = _processingState.value
        if (currentState is ProcessingState.Success) {
            // Находим историю для текущего видео
            val currentHistory = _editHistory.value.find { it.resultPath == currentState.result }
            
            _editingState.value = EditingState(
                mode = ProcessingMode.EDIT,
                originalCommand = _userCommand.value,
                previousPlan = currentState.editPlan,
                currentVideoPath = currentState.result,
                originalVideoAnalyses = currentState.videoAnalyses,
                currentEditCount = currentHistory?.editCount ?: 0,
                parentVideoId = currentHistory?.parentId ?: currentHistory?.id
            )
            _userCommand.value = ""
            // В режиме редактирования оставляем Success для отображения видео
            // Кнопка должна быть активна в любом случае если isEditMode = true
        }
    }

    fun handleVoiceResult(result: String) {
        Log.d(TAG, "handleVoiceResult called with: '$result'")
        if (result.isNotBlank()) {
            _userCommand.value = result
            Log.d(TAG, "Command updated to: '${_userCommand.value}'")
            // Принудительно обновляем StateFlow для перекомпозиции UI
            _userCommand.value = _userCommand.value
        }
    }

    fun startVoiceRecognition(launcher: ActivityResultLauncher<Intent>) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Опишите, какой клип создать")
        }
        launcher.launch(intent)
    }

    fun shareGeneric(context: Context) {
        val currentState = _processingState.value
        if (currentState is ProcessingState.Success) {
            val videoUri = if (currentState.result.startsWith("content://")) {
                // Уже content URI
                Uri.parse(currentState.result)
            } else {
                // Файловый путь - используем FileProvider для создания content URI
                try {
                    val file = File(currentState.result)
                    if (file.exists()) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else {
                        Log.e(TAG, "File does not exist: ${currentState.result}")
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating content URI: ${e.message}")
                    return
                }
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Предоставляем разрешения всем приложениям, которые могут обработать intent
            val chooser = Intent.createChooser(shareIntent, "Поделиться видео")
            val resInfoList = context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, videoUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(chooser)
        }
    }

    fun saveToGallery(context: Context) {
        viewModelScope.launch {
            try {
                val currentState = _processingState.value
                if (currentState is ProcessingState.Success) {
                    val videoPath = currentState.result
                    Log.d(TAG, "Saving video to gallery: $videoPath")
                    
                    // Проверяем, что это временный файл (не из галереи)
                    if (videoPath.contains("/cache/") || videoPath.contains("/temp/") || 
                        videoPath.contains("/output_") || videoPath.contains("/edited_")) {
                        
                        val savedUri = processingRepository.saveVideoToGallery(videoPath)
                        Log.d(TAG, "Video saved to gallery: $savedUri")
                        
                        // Обновляем состояние с новым URI из галереи
                        _processingState.value = currentState.copy(
                            result = savedUri.toString()
                        )
                        
                        Toast.makeText(context, "Видео сохранено в галерею", Toast.LENGTH_SHORT).show()
                    } else {
                        // Если видео уже в галерее
                        Toast.makeText(context, "Видео уже находится в галерее", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Нет видео для сохранения", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving video to gallery", e)
                Toast.makeText(context, "Ошибка при сохранении видео: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    fun openVideoEditor() {
        Log.d("videoeditorclipcraft", "MainViewModel.openVideoEditor() called")
        val currentState = _processingState.value
        Log.d("videoeditorclipcraft", "Current processing state: $currentState")
        
        if (currentState is ProcessingState.Success && currentState.editPlan != null) {
            Log.d("videoeditorclipcraft", "Edit plan found, navigating to VideoEditor screen")
            Log.d("videoeditorclipcraft", "Edit plan segments: ${currentState.editPlan.finalEdit.size}")
            Log.d("videoeditorclipcraft", "Video analyses: ${currentState.videoAnalyses?.size}")
            _currentScreen.value = Screen.VideoEditor
            Log.d("videoeditorclipcraft", "Screen changed to: ${_currentScreen.value}")
        } else {
            Log.e("videoeditorclipcraft", "Cannot open video editor: no edit plan available")
            Log.e("videoeditorclipcraft", "Processing state type: ${currentState.javaClass.simpleName}")
        }
    }

    // --- Tutorial Methods ---
    private fun checkTutorialStatus() {
        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    // Проверяем флаг первого входа пользователя
                    val isTutorialEnabled = sharedPreferences.getBoolean("tutorial_enabled", true)
                    val shouldShowTutorial = user.isFirstTime && isTutorialEnabled
                    
                    Log.d(TAG, "User isFirstTime: ${user.isFirstTime}, tutorial enabled: $isTutorialEnabled")
                    
                    if (shouldShowTutorial) {
                        _tutorialState.value = TutorialState(
                            isEnabled = true,
                            completedSteps = emptySet(),
                            isShowing = true,
                            currentStep = 0
                        )
                        // Сбрасываем флаг первого входа после показа туториала
                        authRepository.updateFirstTimeFlag(user.uid, false)
                    } else {
                        val completedSteps = sharedPreferences.getStringSet("tutorial_completed_steps", emptySet()) ?: emptySet()
                        _tutorialState.value = TutorialState(
                            isEnabled = isTutorialEnabled,
                            completedSteps = completedSteps,
                            isShowing = false
                        )
                    }
                }
            }
        }
    }
    
    fun startTutorial() {
        if (!_tutorialState.value.isEnabled) return
        
        // Сбрасываем все туториалы при запуске общего туториала
        sharedPreferences.edit()
            .remove("video_editor_tutorial_shown")
            .remove("voice_edit_tutorial_shown")
            .apply()
        
        _tutorialState.value = _tutorialState.value.copy(
            currentStep = 0,
            isShowing = true
        )
    }
    
    fun nextTutorialStep() {
        val currentState = _tutorialState.value
        val currentStep = tutorialSteps.getOrNull(currentState.currentStep)
        
        if (currentStep != null) {
            // Отмечаем текущий шаг как выполненный
            val updatedCompletedSteps = currentState.completedSteps + currentStep.id
            sharedPreferences.edit()
                .putStringSet("tutorial_completed_steps", updatedCompletedSteps)
                .apply()
            
            // Переходим к следующему шагу
            val nextStepIndex = currentState.currentStep + 1
            if (nextStepIndex < tutorialSteps.size) {
                _tutorialState.value = currentState.copy(
                    currentStep = nextStepIndex,
                    completedSteps = updatedCompletedSteps
                )
            } else {
                // Туториал завершен
                completeTutorial()
            }
        }
    }
    
    fun skipTutorial() {
        completeTutorial()
    }
    
    private fun completeTutorial() {
        // Отмечаем только начальные шаги как выполненные
        val allStepIds = initialTutorialSteps.map { it.id }.toSet()
        sharedPreferences.edit()
            .putStringSet("tutorial_completed_steps", allStepIds)
            .apply()
        
        _tutorialState.value = _tutorialState.value.copy(
            isShowing = false,
            completedSteps = allStepIds
        )
    }
    
    fun setTutorialEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean("tutorial_enabled", enabled)
            .apply()
        
        _tutorialState.value = _tutorialState.value.copy(isEnabled = enabled)
        
        if (enabled) {
            // Сбрасываем прогресс туториала при включении
            sharedPreferences.edit()
                .remove("tutorial_completed_steps")
                .remove("video_editor_tutorial_shown") // Сбрасываем туториал редактора
                .remove("voice_edit_tutorial_shown") // Сбрасываем туториал голосового редактирования
                .apply()
            
            _tutorialState.value = _tutorialState.value.copy(
                completedSteps = emptySet(),
                currentStep = 0
            )
        }
    }
    
    fun getCurrentTutorialStep(): TutorialStep? {
        return if (_tutorialState.value.isShowing) {
            val currentStep = _tutorialState.value.currentStep
            val step = tutorialSteps.getOrNull(currentStep)
            
            // Для шага с голосовым вводом, если пользователь ввел текст,
            // меняем target на кнопку процесса
            if (step?.id == "voice_input" && _userCommand.value.isNotBlank()) {
                step.copy(
                    title = "Создайте клип",
                    description = "Теперь нажмите эту кнопку, чтобы AI начал монтировать ваше видео.",
                    targetElement = TutorialTarget.PROCESS_BUTTON
                )
            } else {
                step
            }
        } else {
            null
        }
    }

    // --- Feedback Dialog Methods ---
    
    private fun incrementMontageCount() {
        val currentCount = sharedPreferences.getInt(PREF_MONTAGE_COUNT, 0)
        val newCount = currentCount + 1
        
        sharedPreferences.edit()
            .putInt(PREF_MONTAGE_COUNT, newCount)
            .apply()
        
        Log.d(TAG, "Montage count incremented to: $newCount")
        
        // Проверяем, нужно ли показать диалог обратной связи после 3-го монтажа
        if (newCount == 3 && !sharedPreferences.getBoolean(PREF_FEEDBACK_SHOWN, false)) {
            _showFeedbackDialog.value = true
        }
    }
    
    fun checkCreditsAndShowDialog() {
        viewModelScope.launch {
            currentUser.value?.let { user ->
                if (user.creditsRemaining <= 0) {
                    _showNoCreditsDialog.value = true
                }
            }
        }
    }
    
    fun dismissFeedbackDialog() {
        _showFeedbackDialog.value = false
        sharedPreferences.edit()
            .putBoolean(PREF_FEEDBACK_SHOWN, true)
            .apply()
    }
    
    fun dismissNoCreditsDialog() {
        _showNoCreditsDialog.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // Отменяем все активные задачи
        processingRepository.cancelAllWork()
        // Очищаем состояния для освобождения памяти
        _selectedVideos.value = emptyList()

        _processingChatMessages.value = emptyList()
        _editHistory.value = emptyList()
        Log.d(TAG, "ViewModel cleared, resources released")
    }
}